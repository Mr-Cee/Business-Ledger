package com.businessledger;

import javax.swing.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.net.*;
import java.nio.channels.*;
import java.io.IOException;

/** Starts the same project-local database used by Start-Ledger.ps1. */
final class LocalDatabase {
    record Server(String base, String user, String password) {
        Connection connect(String database) throws SQLException {
            return DriverManager.getConnection(base + database + "?connectTimeout=10&socketTimeout=30", user, password);
        }
        @Override public String toString() { return "Local PostgreSQL server"; }
    }
    static Server openServer() throws Exception {
        Path home=Path.of(System.getProperty("ledger.home",".")).toAbsolutePath();Path local=home.resolve(".local");Files.createDirectories(local);
        try(FileChannel channel=FileChannel.open(local.resolve("startup.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.lock()){
            return openLocked(home);
        }
    }
    private static Server openLocked(Path home) throws Exception {
        Path local = home.resolve(".local"), data = local.resolve("postgres-data");
        Path bin = toolsDirectory();
        Files.createDirectories(local);
        boolean first = !Files.exists(data.resolve("PG_VERSION"));
        if(!first&&!Files.readString(data.resolve("PG_VERSION")).trim().equals("17"))throw new IllegalStateException("This data folder uses a different PostgreSQL major version. Restore a ledger backup into a new data folder.");
        if (first && Files.exists(data)) throw new IllegalStateException("Incomplete database at " + data + ". Review it before retrying.");
        Path protectedPassword = local.resolve("database-credential.dpapi");
        String password = System.getenv("LEDGER_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            if (Files.exists(protectedPassword)) password = WindowsSecrets.read(protectedPassword);
            else if (first) {
                byte[] random = new byte[32]; new java.security.SecureRandom().nextBytes(random);
                password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            } else password = askDatabasePassword();
        }
        if (password == null) return null;
        if (password.isBlank()) throw new IllegalArgumentException("A database password is required.");
        if (first) {
            // Persist generated credentials before initializing; an interrupted first run must not lose them.
            WindowsSecrets.save(protectedPassword, password);
            Path secret = Files.createTempFile(local, "init-", ".tmp");
            try {
                Files.writeString(secret, password);
                run(local, bin.resolve("initdb.exe").toString(), "-D", data.toString(), "-U", "ledger_user", "-A", "scram-sha-256", "--encoding=UTF8", "--no-locale", "--pwfile=" + secret);
            } finally { Files.deleteIfExists(secret); }
        }
        if(Files.exists(bin.getParent().resolve("bundle.properties")))rememberBundledHome(home);
        int port=ensureRunning(local,data,bin,validPort(System.getProperty("ledger.pg.preferred.port","55432")));
        Server server = new Server("jdbc:postgresql://127.0.0.1:"+port+"/", "ledger_user", password);
        try (Connection admin = server.connect("postgres"); Statement statement = admin.createStatement()) {
            verifyLocation(admin,data);
            for (String database : new String[]{"business_ledger", "business_ledger_profiles"}) {
                boolean exists;
                try (PreparedStatement s = admin.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
                    s.setString(1, database); try (ResultSet rows = s.executeQuery()) { exists = rows.next(); }
                }
                if (!exists) statement.executeUpdate("CREATE DATABASE " + database);
            }
        }
        WindowsSecrets.save(protectedPassword, password);
        return server;
    }
    static Path toolsDirectory()throws Exception{
        String explicit=System.getProperty("ledger.pg.bin");
        if(explicit!=null&&!explicit.isBlank())return requireTools(Path.of(explicit));
        Path root=AppPaths.applicationDirectory();
        Path bundled=root.resolve("postgres").resolve("bin");
        if(Files.exists(root.resolve("postgres")))return requireTools(bundled);
        if(Boolean.getBoolean("ledger.pg.system.disabled"))throw new IllegalStateException("Bundled database runtime was not found.");
        return requireTools(Path.of("C:/Program Files/PostgreSQL/17/bin"));
    }
    private static Path requireTools(Path bin){
        for(String tool:new String[]{"pg_ctl.exe","initdb.exe","postgres.exe"})if(!Files.isRegularFile(bin.resolve(tool)))throw new IllegalStateException("Database runtime is incomplete: "+bin+". Reinstall Business Ledger.");
        return bin.toAbsolutePath().normalize();
    }
    private static void rememberBundledHome(Path home)throws IOException{
        Path config=AppPaths.userDirectory();Files.createDirectories(config);
        try(FileChannel channel=FileChannel.open(config.resolve("database-locations.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.lock()){
            Path file=config.resolve("database-locations.txt");Set<String> locations=new LinkedHashSet<>();if(Files.exists(file))locations.addAll(Files.readAllLines(file));locations.add(home.toAbsolutePath().normalize().toString());
            Files.write(file,locations,java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    static int ensureRunning(Path local,Path data,Path bin,int defaultPort)throws Exception{
        Path saved=local.resolve("postgres-port.txt");
        if(command(local,bin.resolve("pg_ctl.exe").toString(),"status","-D",data.toString())==0){
            int port=runningPort(data);Files.writeString(saved,Integer.toString(port));return port;
        }
        int preferred=defaultPort;
        if(Files.exists(saved)){try{preferred=validPort(Files.readString(saved).trim());}catch(IllegalArgumentException ignored){}}
        for(int attempt=0;attempt<5;attempt++){
            int port=availablePort(preferred);Files.writeString(saved,Integer.toString(port));
            try{
                run(local,bin.resolve("pg_ctl.exe").toString(),"start","-D",data.toString(),"-l",local.resolve("postgres.log").toString(),"-o","-p "+port+" -h 127.0.0.1","-w");
                return port;
            }catch(Exception ex){
                if(command(local,bin.resolve("pg_ctl.exe").toString(),"status","-D",data.toString())==0){int active=runningPort(data);Files.writeString(saved,Integer.toString(active));return active;}
                if(!portAvailable(port)&&attempt<4){preferred=port+1;continue;}
                throw new IllegalStateException("Unable to start the database for this data folder. See "+local.resolve("postgres.log")+". "+ex.getMessage(),ex);
            }
        }
        throw new IllegalStateException("Unable to reserve a local database port.");
    }
    private static int validPort(String text){int port=Integer.parseInt(text);if(port<1024||port>65535)throw new IllegalArgumentException("Invalid database port.");return port;}
    private static int runningPort(Path data)throws IOException{
        List<String> lines=Files.readAllLines(data.resolve("postmaster.pid"));
        try{if(lines.size()<4)throw new IllegalArgumentException();return validPort(lines.get(3).trim());}
        catch(IllegalArgumentException ex){throw new IOException("Cannot determine the running database port for "+data+".",ex);}
    }
    static boolean portAvailable(int port){
        try(ServerSocket socket=new ServerSocket()){socket.setReuseAddress(false);socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port));return true;}
        catch(IOException|IllegalArgumentException ex){return false;}
    }
    private static int availablePort(int preferred)throws IOException{
        for(int port=Math.max(1024,preferred);port<=Math.min(65535,(long)preferred+100);port++)if(portAvailable(port))return port;
        throw new IOException("No available local database port near "+preferred+".");
    }
    static void verifyLocation(Connection db,Path expected)throws SQLException,IOException{
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SHOW data_directory")){
            r.next();if(!Files.isSameFile(expected,Path.of(r.getString(1))))throw new SQLException("The database server belongs to a different data folder. The selected ledger was not opened.");
        }
    }

    private static String askDatabasePassword() {
        JPasswordField input = new JPasswordField(24);
        Object[] message = {"One-time setup: enter your existing database password.", "This is separate from the login account you will create next.", input};
        JOptionPane prompt = new JOptionPane(message, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = prompt.createDialog(null, "Business Ledger");
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent event) {
                SwingUtilities.invokeLater(input::requestFocusInWindow);
            }
        });
        input.addActionListener(e -> { prompt.setValue(JOptionPane.OK_OPTION); dialog.dispose(); });
        try { dialog.setVisible(true); }
        finally { dialog.dispose(); }
        if (!Integer.valueOf(JOptionPane.OK_OPTION).equals(prompt.getValue())) return null;
        char[] chars = input.getPassword();
        String password = new String(chars); Arrays.fill(chars, '\0'); input.setText("");
        return password;
    }
    private static int command(Path local, String... args) throws Exception {
        Path log = Files.createTempFile(local, "startup-", ".log");
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Database startup timed out. Check .local/postgres.log."); }
            int status = process.exitValue();
            if (status != 0 && !Arrays.asList(args).contains("status")) throw new IllegalStateException(Files.readString(log));
            return status;
        } finally { Files.deleteIfExists(log); }
    }
    private static void run(Path local, String... args) throws Exception { command(local, args); }
}

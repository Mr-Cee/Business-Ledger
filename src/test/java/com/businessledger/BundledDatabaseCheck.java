package com.businessledger;

import javax.swing.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fresh data, shipped binaries, packaged Java, no system PostgreSQL lookup. */
public final class BundledDatabaseCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void run(Path executable,String...args)throws Exception{
        List<String> command=new ArrayList<>();command.add(executable.toString());command.addAll(List.of(args));
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new Exception("Bundled command timed out: "+executable.getFileName());}
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(process.exitValue()!=0)throw new Exception("Bundled command failed: "+output);
    }
    static void initialize(Connection db)throws Exception{
        SwingUtilities.invokeAndWait(()->{try{Constructor<BusinessLedger> constructor=BusinessLedger.class.getDeclaredConstructor(Connection.class);constructor.setAccessible(true);constructor.newInstance(db).dispose();}catch(Exception ex){throw new RuntimeException(ex);}});
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("Expected payload root, fresh test data root, lifecycle test helper.");
        Path image=Path.of(args[0]).toAbsolutePath(),home=Path.of(args[1]).toAbsolutePath();Files.createDirectories(home);
        check(home.toString().contains("target")&&!Files.exists(home.resolve(".local/postgres-data")),"Only fresh disposable test homes are allowed");
        System.setProperty("ledger.home",home.toString());System.setProperty("ledger.user.config",home.resolve("config").toString());System.setProperty("ledger.pg.system.disabled","true");System.setProperty("ledger.pg.preferred.port","55460");
        Path bin=LocalDatabase.toolsDirectory();check(Files.isSameFile(bin,image.resolve("postgres/bin")),"Shipped PostgreSQL selected");
        check(Path.of(System.getProperty("java.home")).startsWith(image),"Shipped Java selected");
        try{
            LocalDatabase.Server server=LocalDatabase.openServer();
            try(LoginStore accounts=new LoginStore(server)){
                LoginStore.Session session=accounts.create("Bundled test user","Test-only-password-123".toCharArray());
                try(Connection db=session.connection()){
                    initialize(db);
                    try(Statement s=db.createStatement()){s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount) VALUES ('2026-01-01','Income','Paycheck',123.45),('2026-01-02','Expense','Food',20.10)");}
                    LedgerBackup backup=new LedgerBackup(db);Path file=home.resolve("round-trip.ledgerbackup");backup.create(file,"Bundled test");
                    try(Statement s=db.createStatement()){s.executeUpdate("DELETE FROM transactions");}backup.restore(file);
                    try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM transactions")){r.next();check(r.getInt(1)==2,"Backup restores records with bundled engine");}
                }
            }
            Path locations=AppPaths.userDirectory().resolve("database-locations.txt");check(Files.readString(locations).contains(home.toString()),"Managed database location tracked");
            run(Path.of(args[2]),image.resolve("not-the-owner-root").toString(),locations.toString());
            check(Files.exists(home.resolve(".local/postgres-data/postmaster.pid")),"A server outside the allowed application root is not stopped");
            run(Path.of(args[2]),image.toString(),locations.toString());
            check(!Files.exists(home.resolve(".local/postgres-data/postmaster.pid")),"Owned server actually stopped");
            server=LocalDatabase.openServer();
            try(LoginStore accounts=new LoginStore(server);Connection db=accounts.login("Bundled test user","Test-only-password-123".toCharArray()).connection();Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM transactions")){
                r.next();check(r.getInt(1)==2,"Login and restored data survive managed stop/restart");
            }
            run(Path.of(args[2]),image.toString(),locations.toString());
            check(!Files.exists(home.resolve(".local/postgres-data/postmaster.pid")),"Restarted owned server actually stopped");
            System.out.println("Packaged Java/PostgreSQL bootstrap, DPAPI credentials, login creation, backup/restore, owned-server shutdown, and restart passed without system PostgreSQL lookup.");
        }finally{
            Path data=home.resolve(".local/postgres-data");if(Files.exists(data.resolve("postmaster.pid")))run(bin.resolve("pg_ctl.exe"),"stop","-D",data.toString(),"-m","fast","-w");
        }
    }
}

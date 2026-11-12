package com.businessledger;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Two fresh, disposable clusters. Never starts or stops the real ledger cluster. */
public final class DatabasePortCheck {
    static final Path BIN=Path.of("C:/Program Files/PostgreSQL/17/bin");
    static void run(String tool,String...args)throws Exception{
        List<String> command=new ArrayList<>();command.add(BIN.resolve(tool+".exe").toString());command.addAll(List.of(args));
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(!process.waitFor(90,TimeUnit.SECONDS)||process.exitValue()!=0)throw new Exception(tool+" failed: "+output);
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Connection connect(int port)throws SQLException{return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+port+"/postgres","ledger_test","");}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory(Path.of("target"),"database-port-check-").toAbsolutePath();
        Path a=root.resolve("first"),b=root.resolve("second");Files.createDirectories(a);Files.createDirectories(b);Path ad=a.resolve("data"),bd=b.resolve("data");
        run("initdb","-D",ad.toString(),"-U","ledger_test","-A","trust","--encoding=UTF8","--no-locale");
        run("initdb","-D",bd.toString(),"-U","ledger_test","-A","trust","--encoding=UTF8","--no-locale");
        boolean runningA=false,runningB=false;
        try{
            int first=LocalDatabase.ensureRunning(a,ad,BIN,55440);runningA=true;
            Files.writeString(b.resolve("postgres-port.txt"),Integer.toString(first));
            int second=LocalDatabase.ensureRunning(b,bd,BIN,55440);runningB=true;
            check(first!=second,"Distinct homes must not reuse an occupied port");check(first>=55440&&second>=55440,"Production port is never used by this test");
            try(Connection ca=connect(first);Connection cb=connect(second)){
                LocalDatabase.verifyLocation(ca,ad);LocalDatabase.verifyLocation(cb,bd);
                try(Statement s=ca.createStatement()){s.executeUpdate("CREATE TABLE first_cluster_only(value INT)");s.executeUpdate("INSERT INTO first_cluster_only VALUES (7)");}
                try(Statement s=cb.createStatement();ResultSet r=s.executeQuery("SELECT to_regclass('first_cluster_only') IS NULL")){r.next();check(r.getBoolean(1),"Clusters retain separate data");}
                try{LocalDatabase.verifyLocation(ca,bd);throw new AssertionError("Wrong data directory accepted");}catch(SQLException expected){}
            }
            Files.writeString(a.resolve("postgres-port.txt"),Integer.toString(second));
            check(LocalDatabase.ensureRunning(a,ad,BIN,55440)==first,"Running PID port takes precedence over stale saved port");
            check(Integer.parseInt(Files.readString(a.resolve("postgres-port.txt")))==first,"Saved port repaired");
            run("pg_ctl","stop","-D",ad.toString(),"-m","fast","-w");runningA=false;
            int restarted=LocalDatabase.ensureRunning(a,ad,BIN,55440);runningA=true;check(restarted==first,"Stopped cluster reuses its available saved port");
            try(Connection ca=connect(first);Statement s=ca.createStatement();ResultSet r=s.executeQuery("SELECT value FROM first_cluster_only")){r.next();check(r.getInt(1)==7,"Restart preserves ledger data");}
            try(Connection cb=connect(second)){LocalDatabase.verifyLocation(cb,bd);}
            System.out.println("Two independent data folders start concurrently on separate ports; reconnect, saved-port recovery, data isolation, directory validation, and restart checks passed.");
        }finally{
            if(runningA)run("pg_ctl","stop","-D",ad.toString(),"-m","fast","-w");
            if(runningB)run("pg_ctl","stop","-D",bd.toString(),"-m","fast","-w");
        }
    }
}

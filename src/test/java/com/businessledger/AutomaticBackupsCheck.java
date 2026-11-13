package com.businessledger;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Fresh source/other-profile databases on test port 55439 only. */
public final class AutomaticBackupsCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void sql(Connection db,String sql)throws SQLException{try(Statement s=db.createStatement()){s.executeUpdate(sql);}}
    static List<LedgerBackup.Snapshot> owned(LedgerBackup backups,AutomaticBackups.Kind kind)throws Exception{return AutomaticBackups.owned(AutomaticBackups.directory(backups,kind),backups.profile(),kind);}
    static void restart(String url,String folder)throws Exception{
        try(Connection db=DriverManager.getConnection(url,"ledger_test","")){
            check(!AutomaticBackups.take(db,AutomaticBackups.Kind.TIMED,"Restarted profile").created(),"Unchanged data after process restart does not create a backup");
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&args[0].equals("--restart")){restart(args[1],args[2]);return;}
        if(args.length!=2||Arrays.stream(args).anyMatch(s->!s.startsWith("jdbc:postgresql://127.0.0.1:55439/")))throw new IllegalArgumentException("Two disposable test databases on 55439 required");
        Path root=Files.createTempDirectory(Path.of("target"),"backup-retention-check-").toAbsolutePath();System.setProperty("ledger.home",root.toString());
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Connection other=DriverManager.getConnection(args[1],"ledger_test","")){
            BackupIntegrationCheck.initialize(db);BackupIntegrationCheck.initialize(other);
            LedgerBackup backups=new LedgerBackup(db),otherBackups=new LedgerBackup(other);backups.saveOptions(true,true,root);otherBackups.saveOptions(true,true,root);
            check(AutomaticBackups.take(db,AutomaticBackups.Kind.TIMED,"Profile").created(),"First interval creates a baseline");
            check(!AutomaticBackups.take(db,AutomaticBackups.Kind.TIMED,"Profile").created(),"Unchanged interval skips saving");
            sql(db,"INSERT INTO transactions(entry_date,kind,category,amount,party,notes,account_id) SELECT '2026-11-12','Expense','Other',10,'Shop','initial',id FROM accounts WHERE name='Checking'");
            for(int i=0;i<7;i++){sql(db,"UPDATE transactions SET notes='Change "+i+"'");check(AutomaticBackups.take(db,AutomaticBackups.Kind.TIMED,"Profile").created(),"Changed interval saves");}
            var timed=owned(backups,AutomaticBackups.Kind.TIMED);check(timed.size()==5&&timed.stream().mapToLong(LedgerBackup.Snapshot::generation).min().orElseThrow()==4,"Only newest five timed backups retained");
            for(int i=0;i<7;i++)AutomaticBackups.take(db,AutomaticBackups.Kind.CLOSING,"Profile");
            var closing=owned(backups,AutomaticBackups.Kind.CLOSING);check(closing.size()==5&&closing.stream().mapToLong(LedgerBackup.Snapshot::generation).min().orElseThrow()==3,"Only newest five close backups retained, even unchanged");
            check(owned(backups,AutomaticBackups.Kind.TIMED).size()==5,"Sets have independent limits");
            check(backups.fingerprint().equals(LedgerBackup.snapshotInfo(closing.getFirst().path()).fingerprint()),"Saved fingerprint describes exported snapshot");
            List<Path> manual=new ArrayList<>();for(int i=0;i<8;i++){Path file=root.resolve("manual-"+i+".ledgerbackup");backups.create(file,"Manual");manual.add(file);}
            Path manualInside=AutomaticBackups.directory(backups,AutomaticBackups.Kind.TIMED).resolve("looks_timed_automatic.ledgerbackup");backups.create(manualInside,"Manual in managed folder");manual.add(manualInside);
            for(int i=0;i<6;i++)AutomaticBackups.take(db,AutomaticBackups.Kind.CLOSING,"Profile");
            check(manual.stream().allMatch(Files::exists)&&owned(backups,AutomaticBackups.Kind.CLOSING).size()==5,"Manual backups unlimited and excluded even inside managed folder");
            AutomaticBackups.take(other,AutomaticBackups.Kind.CLOSING,"Other");check(owned(otherBackups,AutomaticBackups.Kind.CLOSING).size()==1&&owned(backups,AutomaticBackups.Kind.CLOSING).size()==5,"Profiles sharing a destination are independent");
            sql(db,"UPDATE transactions SET notes='Manual saved this change'");Path latestManual=root.resolve("latest-manual.ledgerbackup");backups.create(latestManual,"Manual");
            check(!AutomaticBackups.take(db,AutomaticBackups.Kind.TIMED,"Profile").created(),"Successful manual snapshot prevents redundant timed save");Files.delete(latestManual);
            check(AutomaticBackups.take(db,AutomaticBackups.Kind.TIMED,"Profile").created(),"Missing manual snapshot does not falsely protect changed data");
            AtomicInteger failures=new AtomicInteger(),opens=new AtomicInteger();boolean[] paused={false};
            PeriodicBackups scheduler=new PeriodicBackups(()->{opens.incrementAndGet();return DriverManager.getConnection(args[0],"ledger_test","");},"Profile",()->paused[0],ex->failures.incrementAndGet());
            check(PeriodicBackups.MINUTES==10,"Ten-minute interval");
            db.setAutoCommit(false);sql(db,"UPDATE transactions SET notes='Uncommitted'");scheduler.tick();check(!db.getAutoCommit(),"Timed snapshots never change UI connection transaction state");
            db.commit();db.setAutoCommit(true);long generation=owned(backups,AutomaticBackups.Kind.TIMED).stream().mapToLong(LedgerBackup.Snapshot::generation).max().orElseThrow();scheduler.tick();
            check(owned(backups,AutomaticBackups.Kind.TIMED).stream().mapToLong(LedgerBackup.Snapshot::generation).max().orElseThrow()==generation+1,"Committed change captured on dedicated connection");
            backups.saveOptions(true,false,root);sql(db,"UPDATE transactions SET notes='Disabled change'");scheduler.tick();check(owned(backups,AutomaticBackups.Kind.TIMED).stream().mapToLong(LedgerBackup.Snapshot::generation).max().orElseThrow()==generation+1,"Timed toggle respected independently of close backup");
            backups.saveOptions(true,true,root);scheduler.tick();check(failures.get()==0,"Normal scheduled snapshots succeed");
            int before=opens.get();paused[0]=true;scheduler.tick();check(opens.get()==before,"Closing suspends new timed work");paused[0]=false;scheduler.close();scheduler.tick();check(opens.get()==before,"Disposal stops timed work");
            Map<Path,String> retained=new HashMap<>();for(var entry:owned(backups,AutomaticBackups.Kind.CLOSING))retained.put(entry.path(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(entry.path()))));
            sql(db,"UPDATE transactions SET notes=repeat('x',16777217)");
            PeriodicBackups failedTimer=new PeriodicBackups(()->DriverManager.getConnection(args[0],"ledger_test",""),"Profile",()->false,ex->failures.incrementAndGet());failedTimer.tick();failedTimer.tick();check(failures.get()==1,"Repeated timed errors reported once");
            boolean failed=false;try{AutomaticBackups.take(db,AutomaticBackups.Kind.CLOSING,"Profile");}catch(java.io.IOException expected){failed=true;}
            check(failed,"Snapshot failure is surfaced");for(var entry:retained.entrySet())check(entry.getValue().equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(entry.getKey())))),"Failed save preserves all previous backups");
            sql(db,"UPDATE transactions SET notes='Disabled change'");failedTimer.tick();failedTimer.close();
            Path portable=root.resolve("settings-preservation.ledgerbackup");backups.create(portable,"Source");otherBackups.saveOptions(false,false,root.resolve("other-destination"));otherBackups.restore(portable);check(!otherBackups.options().timed()&&!otherBackups.options().automatic(),"Restore keeps both local backup preferences");
            check(db.getAutoCommit()&&!db.isReadOnly(),"Snapshot connections restored after success/failure");
            Path previousFolder=AutomaticBackups.directory(backups,AutomaticBackups.Kind.CLOSING);backups.saveOptions(true,true,root.resolve("new-destination"));
            check(AutomaticBackups.take(db,AutomaticBackups.Kind.TIMED,"Profile").created(),"Destination change creates a new baseline instead of relying on old files");
            check(AutomaticBackups.owned(previousFolder,backups.profile(),AutomaticBackups.Kind.CLOSING).size()==5,"Old destination remains intact");
            AutomaticBackups.take(db,AutomaticBackups.Kind.CLOSING,"Profile");
            String javaCommand=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();Process child=new ProcessBuilder(javaCommand,"-cp",System.getProperty("java.class.path"),AutomaticBackupsCheck.class.getName(),"--restart",args[0],root.toString()).inheritIO().start();
            if(!child.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("Restart check timed out");}check(child.exitValue()==0,"Baseline survives JVM restart through managed backup metadata");
            System.out.println("Independent five-file retention, changed-only timed saves, manual/profile isolation, dedicated connections, close/dispose guards, failure preservation, local preferences and restart baseline passed.");
        }
        System.exit(0);
    }
}

package com.businessledger;

import javax.swing.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;

/** Fresh source and destination databases only. Never use a real ledger. */
public final class BackupIntegrationCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static long number(Connection db,String sql)throws SQLException{try(Statement s=db.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    static String text(Connection db,String sql)throws SQLException{try(Statement s=db.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    static void initialize(Connection db)throws Exception{SwingUtilities.invokeAndWait(()->{try{SettingsIntegrationCheck.open(db).dispose();}catch(Exception ex){throw new RuntimeException(ex);}});}
    public static void main(String[] args)throws Exception{
        if(args.length!=2||Arrays.stream(args).anyMatch(s->!s.contains(":55439/")))throw new IllegalArgumentException("Two fresh disposable test databases required.");
        Path root=Path.of("target/backup-check").toAbsolutePath();Files.createDirectories(root);System.setProperty("ledger.home",root.toString());
        try(Connection source=DriverManager.getConnection(args[0],"ledger_test","");Connection target=DriverManager.getConnection(args[1],"ledger_test","")){
            initialize(source);initialize(target);
            try(Statement s=source.createStatement()){
                s.executeUpdate("INSERT INTO categories(name,active) VALUES ('Archived category',FALSE)");
                s.executeUpdate("INSERT INTO accounts(name,account_type,opening_balance,active) VALUES ('Archive cash','Cash',27.50,FALSE)");
                s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount,account_id) SELECT '2026-01-01','Income','Paycheck',1234.56,id FROM accounts WHERE name='Checking'");
                s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount,notes) VALUES ('2026-01-02','Expense','Archived category',25.50,'Unicode café ☕, quoted text and newline')");
                s.executeUpdate("INSERT INTO transfers(transfer_date,from_account_id,to_account_id,amount,notes) SELECT '2026-01-03',a.id,b.id,50.25,'A transfer' FROM accounts a,accounts b WHERE a.name='Checking' AND b.name='Savings'");
                s.executeUpdate("INSERT INTO mileage(trip_date,origin,destination,purpose,miles) VALUES ('2026-01-04','Office','Client','Business visit',17.25)");
                s.executeUpdate("INSERT INTO report_templates(name,kind,detail) VALUES ('Saved custom','Expense',FALSE)");
                s.executeUpdate("INSERT INTO report_template_categories SELECT id,'Food' FROM report_templates WHERE name='Saved custom'");
                s.executeUpdate("INSERT INTO report_template_categories SELECT id,'Supplies' FROM report_templates WHERE name='Saved custom'");
                s.executeUpdate("INSERT INTO transaction_edits(transaction_id,edited_by,reason,before_record,after_record) SELECT id,'Tester','Correct amount',to_jsonb(t),to_jsonb(t) FROM transactions t WHERE kind='Income'");
                s.executeUpdate("INSERT INTO transfer_edits(transfer_id,edited_by,reason,before_record,after_record) SELECT id,'Tester','Correct date',to_jsonb(t),to_jsonb(t) FROM transfers t");
                s.executeQuery("SELECT setval(pg_get_serial_sequence('transactions','id'),100)").close();
            }
            new IncomeScope("Paycheck").save(source);TaxEstimate.saveRate(source,new java.math.BigDecimal("17.25"));
            LedgerBackup sourceBackups=new LedgerBackup(source),targetBackups=new LedgerBackup(target);
            sourceBackups.saveOptions(true,root.resolve("source-auto"));targetBackups.saveOptions(false,root.resolve("destination-auto"));
            check(!new LedgerBackup(target).options().automatic(),"Automatic backup defaults/persistence");
            Path portable=root.resolve("portable ledger.ledgerbackup");sourceBackups.create(portable,"Source ledger");
            check(LedgerBackup.inspect(portable).ledger().equals("Source ledger"),"Backup metadata");
            check(source.getAutoCommit()&&!source.isReadOnly(),"Source connection restored after snapshot");
            try(Statement s=target.createStatement()){
                s.executeUpdate("INSERT INTO transactions(id,entry_date,kind,category,amount) OVERRIDING SYSTEM VALUE VALUES (100,'2026-02-01','Expense','Food',7)");
                s.executeQuery("SELECT setval(pg_get_serial_sequence('transactions','id'),100)").close();
            }
            Path damaged=root.resolve("damaged.ledgerbackup");
            try(ZipFile zip=new ZipFile(portable.toFile());ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(damaged))){
                for(ZipEntry entry:Collections.list(zip.entries())){
                    out.putNextEntry(new ZipEntry(entry.getName()));byte[] data;try(InputStream in=zip.getInputStream(entry)){data=in.readAllBytes();}
                    if(entry.getName().equals("ledger_settings.data"))data[data.length-1]^=1;out.write(data);out.closeEntry();
                }
            }
            boolean rejected=false;try{targetBackups.restoreWithSafety(damaged,"Target ledger");}catch(Exception expected){rejected=true;}
            check(rejected,"Damaged backup rejected");check(number(target,"SELECT COUNT(*) FROM transactions")==1,"Failed restore rolls back data");
            check(number(target,"INSERT INTO transactions(entry_date,kind,category,amount) VALUES ('2026-02-02','Expense','Food',8) RETURNING id")==101,"Failed restore rolls back identity changes");
            LedgerBackup.Restored restored=targetBackups.restoreWithSafety(portable,"Target ledger");
            check(Files.exists(restored.safetyBackup()),"Safety backup exists");
            check(LedgerBackup.inspect(restored.safetyBackup()).ledger().equals("Target ledger"),"Safety backup metadata");
            check(number(target,"SELECT COUNT(*) FROM transactions")==2,"Transactions restored, original target replaced");
            check(text(target,"SELECT SUM(amount) FROM transactions").equals("1260.06"),"Exact money preserved");
            check(text(target,"SELECT notes FROM transactions WHERE kind='Expense'").contains("café ☕"),"Unicode and notes preserved");
            check(number(target,"SELECT COUNT(*) FROM mileage")==1&&number(target,"SELECT COUNT(*) FROM transfers")==1,"Mileage and transfers restored");
            check(number(target,"SELECT COUNT(*) FROM transaction_edits")==1&&number(target,"SELECT COUNT(*) FROM transfer_edits")==1,"Edit history restored");
            check(text(target,"SELECT after_record->>'amount' FROM transaction_edits").equals("1234.56"),"JSON audit snapshots preserved");
            check(number(target,"SELECT COUNT(*) FROM report_template_categories WHERE template_id=(SELECT id FROM report_templates WHERE name='Saved custom')")==2,"Multi-category report template restored");
            check("Paycheck".equals(IncomeScope.load(target).category())&&TaxEstimate.rate(target).toPlainString().equals("17.25"),"Ledger preferences restored");
            check(!targetBackups.options().automatic()&&targetBackups.options().folder().equals(root.resolve("destination-auto")),"Local backup preferences retained");
            check(number(target,"INSERT INTO transactions(entry_date,kind,category,amount) VALUES ('2026-03-01','Expense','Food',1) RETURNING id")==101,"Deleted record numbers are not reused after restore");
            targetBackups.restore(restored.safetyBackup());check(number(target,"SELECT COUNT(*) FROM transactions")==2&&number(target,"SELECT MAX(id) FROM transactions")==101,"Safety backup can restore prior ledger");
            check(number(source,"SELECT COUNT(*) FROM transactions")==2,"Source/other profile unchanged");
            targetBackups.saveOptions(true,root.resolve("destination-auto"));
            Path automatic=LedgerBackup.datedFile(targetBackups.options().folder(),"Target ledger","automatic");targetBackups.create(automatic,"Target ledger");
            check(Files.exists(automatic)&&targetBackups.options().automatic(),"Automatic backup destination and format");
            Path invalid=root.resolve("invalid.ledgerbackup");Files.writeString(invalid,"not a backup");
            rejected=false;try{targetBackups.restore(invalid);}catch(Exception expected){rejected=true;}
            check(rejected&&number(target,"SELECT MAX(id) FROM transactions")==101,"Invalid file leaves data unchanged");
            System.out.println("Backup round trip, portability, safety copy, checksum rollback, identity rollback/restart, Unicode, audit history, report templates, preferences, and automatic destination passed.");
        }
        try(Connection target=DriverManager.getConnection(args[1],"ledger_test","")){check(new LedgerBackup(target).options().automatic(),"Automatic setting survives new session");}
    }
}

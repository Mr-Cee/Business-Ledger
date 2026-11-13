package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;

/** Uses fresh disposable profile databases on port 55439, never a real ledger. */
public final class DeletedRecordsCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static long n(Connection db,String sql)throws SQLException{return BackupIntegrationCheck.number(db,sql);}
    static String text(Connection db,String sql)throws SQLException{return BackupIntegrationCheck.text(db,sql);}
    static void sql(Connection db,String sql)throws SQLException{try(Statement s=db.createStatement()){s.executeUpdate(sql);}}
    @FunctionalInterface interface Attempt{void run()throws Exception;}
    static void fails(Attempt attempt,String message)throws Exception{boolean failed=false;try{attempt.run();}catch(SQLException|IllegalArgumentException|IOException expected){failed=true;}check(failed,message);}
    static long remove(Connection db,DeletedRecords.Kind kind,long id)throws SQLException{return DeletedRecords.delete(db,DeletedRecords.original(db,kind,id,false),"Tester");}
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{JTable table=new JTable(new DefaultTableModel(new Object[][]{{2L,"Earlier"},{8L,"Later"}},new String[]{"Recovery ID","Name"}));table.setAutoCreateRowSorter(true);table.getRowSorter().toggleSortOrder(1);table.getRowSorter().toggleSortOrder(1);table.setRowSelectionInterval(0,0);check(RecentlyDeletedDialog.selectedRecoveryId(table)==8,"Sorted recovery selection uses recovery ID");});
        if(args.length!=2||Arrays.stream(args).anyMatch(s->!s.startsWith("jdbc:postgresql://127.0.0.1:55439/")))throw new IllegalArgumentException("Two fresh disposable databases on 55439 required");
        Path root=Path.of("target/recovery/check").toAbsolutePath();Files.createDirectories(root);System.setProperty("ledger.home",root.toString());
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Connection target=DriverManager.getConnection(args[1],"ledger_test","")){
            BackupIntegrationCheck.initialize(db);BackupIntegrationCheck.initialize(target);
            sql(db,"INSERT INTO categories(name) VALUES ('Recovery category')");
            long checking=n(db,"SELECT id FROM accounts WHERE name='Checking'"),savings=n(db,"SELECT id FROM accounts WHERE name='Savings'");
            long transaction=n(db,"INSERT INTO transactions(entry_date,kind,category,amount,party,notes,account_id) VALUES ('2026-11-01','Expense','Recovery category',10.25,'Test shop','Unicode café / receipt',"+checking+") RETURNING id");
            sql(db,"INSERT INTO transaction_edits(transaction_id,edited_by,reason,before_record,after_record) SELECT id,'Tester','Prior edit',to_jsonb(t),to_jsonb(t) FROM transactions t WHERE id="+transaction);
            var stale=DeletedRecords.original(db,DeletedRecords.Kind.TRANSACTION,transaction,false);sql(db,"UPDATE transactions SET notes='Updated receipt' WHERE id="+transaction);
            fails(()->DeletedRecords.delete(db,stale,"Tester"),"Changed record not deleted");check(n(db,"SELECT count(*) FROM deleted_records")==0,"Stale deletion wrote no recovery entry");
            long recovery=remove(db,DeletedRecords.Kind.TRANSACTION,transaction);check(n(db,"SELECT count(*) FROM transactions")==0&&n(db,"SELECT count(*) FROM transaction_edits")==1,"Deletion removes transaction but preserves audit history");
            check(DeletedRecords.list(db).getFirst().recordId()==transaction&&DeletedRecords.list(target).isEmpty(),"Recovery is profile-local");
            try(Connection again=DriverManager.getConnection(args[0],"ledger_test","")){check(DeletedRecords.list(again).size()==1,"Recovery persists across connections");}
            fails(()->sql(db,"DELETE FROM accounts WHERE id="+checking),"Recovery references prevent account loss");sql(db,"UPDATE accounts SET active=FALSE WHERE id="+checking);
            db.setAutoCommit(false);sql(db,"INSERT INTO categories(name) VALUES ('Renamed recovery')");sql(db,"UPDATE deleted_records SET category='Renamed recovery' WHERE category='Recovery category'");sql(db,"DELETE FROM categories WHERE name='Recovery category'");db.commit();db.setAutoCommit(true);
            long repeat=n(db,"INSERT INTO transactions(entry_date,kind,category,amount,party,notes,account_id) VALUES ('2026-11-01','Expense','Other',10.25,' TEST  SHOP ','repeat',"+checking+") RETURNING id");
            fails(()->DeletedRecords.restore(db,recovery,"Tester",Set.of()),"Unapproved duplicate prevents restore");
            check(n(db,"SELECT count(*) FROM transactions")==1&&DeletedRecords.list(db).size()==1,"Duplicate rejection leaves entry recoverable");
            check(DeletedRecords.matches(db,recovery).rows().getFirst().id()==repeat,"Restore supplies duplicate details");
            DeletedRecords.restore(db,recovery,"Restorer",Set.of(repeat));
            check(text(db,"SELECT category FROM transactions WHERE id="+transaction).equals("Renamed recovery"),"Renamed category restored via retained reference");
            check(text(db,"SELECT notes FROM transactions WHERE id="+transaction).equals("Updated receipt")&&n(db,"SELECT count(*) FROM transaction_edits WHERE transaction_id="+transaction)==1,"Original data and edit history reconnect");
            check(n(db,"SELECT count(*) FROM deleted_records WHERE restored_by='Restorer' AND restored_at IS NOT NULL")==1,"Restore actor/time retained");fails(()->DeletedRecords.restore(db,recovery,"Tester",Set.of(repeat)),"Restore is exactly once");
            long transfer=n(db,"INSERT INTO transfers(transfer_date,from_account_id,to_account_id,amount,notes) VALUES ('2026-11-02',"+checking+","+savings+",7.50,'Move') RETURNING id");
            String before=text(db,"SELECT balance FROM account_balances WHERE id="+checking);long transferRecovery=remove(db,DeletedRecords.Kind.TRANSFER,transfer);
            check(!text(db,"SELECT balance FROM account_balances WHERE id="+checking).equals(before),"Transfer deletion updates balances");DeletedRecords.restore(db,transferRecovery,"Tester",Set.of());check(text(db,"SELECT balance FROM account_balances WHERE id="+checking).equals(before)&&n(db,"SELECT count(*) FROM transfers")==1,"Transfer restores both account effects, with colliding transaction/transfer IDs kept separate");
            long trip=n(db,"INSERT INTO mileage(trip_date,origin,destination,purpose,miles) VALUES ('2026-11-03','','','','12.75') RETURNING id");long tripRecovery=remove(db,DeletedRecords.Kind.MILEAGE,trip);
            sql(db,"INSERT INTO mileage(id,trip_date,origin,destination,purpose,miles) OVERRIDING SYSTEM VALUE VALUES ("+trip+",'2026-11-04','Other','','',1)");
            fails(()->DeletedRecords.restore(db,tripRecovery,"Tester",Set.of()),"Occupied original ID is never overwritten");check(text(db,"SELECT origin FROM mileage WHERE id="+trip).equals("Other"),"Conflicting row preserved");sql(db,"DELETE FROM mileage WHERE id="+trip);
            sql(db,"ALTER TABLE deleted_records ADD CONSTRAINT fail_restore CHECK(restored_by IS DISTINCT FROM 'failure') NOT VALID");fails(()->DeletedRecords.restore(db,tripRecovery,"failure",Set.of()),"Restoration metadata failure rolls back inserted record");check(n(db,"SELECT count(*) FROM mileage")==0,"Failed restore made no ledger changes");DeletedRecords.restore(db,tripRecovery,"Tester",Set.of());check(text(db,"SELECT miles FROM mileage WHERE id="+trip).equals("12.75"),"Mileage and optional empty fields restored");
            sql(db,"ALTER TABLE deleted_records ADD CONSTRAINT fail_delete CHECK(deleted_by<>'failure') NOT VALID");var original=DeletedRecords.original(db,DeletedRecords.Kind.MILEAGE,trip,false);fails(()->DeletedRecords.delete(db,original,"failure"),"Archive failure prevents deletion");check(n(db,"SELECT count(*) FROM mileage")==1,"Original remains after failed archive");
            long pending=remove(db,DeletedRecords.Kind.MILEAGE,trip);check(pending!=tripRecovery,"Repeated deletion has its own recovery ID");
            Path backup=root.resolve("v3.ledgerbackup");new LedgerBackup(db).create(backup,"Recovery source");new LedgerBackup(target).restore(backup);
            check(DeletedRecords.list(target).size()==1&&n(target,"SELECT count(*) FROM deleted_records WHERE restored_at IS NOT NULL")==3,"Backup preserves pending recovery and restored history");DeletedRecords.restore(target,pending,"Tester",Set.of());check(n(target,"SELECT count(*) FROM mileage")==1,"Pending record remains restorable after backup round trip");
            Path missing=root.resolve("missing.ledgerbackup");rewrite(backup,missing,"3",true,false);fails(()->new LedgerBackup(target).restore(missing),"New backup missing recovery rejected");
            Path v2=root.resolve("v2.ledgerbackup");rewrite(backup,v2,"2",true,false);new LedgerBackup(target).restore(v2);check(n(target,"SELECT count(*) FROM deleted_records")==0&&n(target,"SELECT count(*) FROM transactions")==2,"Version-2 backup restores with empty recovery list");
            Path v1=root.resolve("v1.ledgerbackup");rewrite(backup,v1,"1",true,true);new LedgerBackup(target).restore(v1);check(n(target,"SELECT count(*) FROM deleted_records")==0,"Version-1 backups remain supported");
            new LedgerBackup(target).restore(backup);check(n(target,"INSERT INTO mileage(trip_date,origin,destination,purpose,miles) VALUES ('2026-11-06','','','',1) RETURNING id")>trip,"Deleted original IDs are not reused after backup restore");
            System.out.println("Atomic delete/restore, stale changes, profile/restart persistence, duplicate approval, ID conflicts, archived/renamed references, history, transfer balances, mileage and v1/v2/v3 backup checks passed.");
        }
    }
    static void rewrite(Path source,Path target,String version,boolean omitRecovery,boolean omitRules)throws Exception{
        try(ZipFile zip=new ZipFile(source.toFile());ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(target))){for(ZipEntry entry:Collections.list(zip.entries())){
            if((omitRecovery&&entry.getName().equals("deleted_records.data"))||(omitRules&&entry.getName().equals("payee_rules.data")))continue;
            byte[] data;try(InputStream in=zip.getInputStream(entry)){data=in.readAllBytes();}
            if(entry.getName().equals("manifest.properties")){Properties p=new Properties();p.load(new ByteArrayInputStream(data));p.setProperty("version",version);if(omitRecovery){p.remove("deleted_records.sha256");p.remove("deleted_records.nextId");}if(omitRules)p.remove("payee_rules.sha256");ByteArrayOutputStream bytes=new ByteArrayOutputStream();p.store(bytes,"");data=bytes.toByteArray();}
            out.putNextEntry(new ZipEntry(entry.getName()));out.write(data);out.closeEntry();
        }}
    }
}

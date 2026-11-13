package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.util.*;

/** Fresh disposable integration database only; pure selection checks also run headlessly. */
public final class BulkTransactionEditCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static long number(Connection db,String sql)throws SQLException{try(Statement s=db.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    static String text(Connection db,String sql)throws SQLException{try(Statement s=db.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    static void sql(Connection db,String sql)throws SQLException{try(Statement s=db.createStatement()){s.executeUpdate(sql);}}
    @FunctionalInterface interface Attempt{void run()throws Exception;}
    static void rejected(Attempt attempt,String message)throws Exception{boolean failed=false;try{attempt.run();}catch(SQLException|IllegalArgumentException expected){failed=true;}check(failed,message);}
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{try{
            DefaultTableModel model=new DefaultTableModel(new String[]{"ID","Date","Type","Payee"},0);
            model.addRow(new Object[]{10L,"2026-01-01","Expense","Z Shop"});model.addRow(new Object[]{20L,"2026-01-02","Income","A Client"});model.addRow(new Object[]{10L,"2026-01-01","Transfer Out","Savings"});
            JTable table=new JTable(model);table.setAutoCreateRowSorter(true);table.getRowSorter().toggleSortOrder(3);
            table.setRowSelectionInterval(0,0);check(BulkTransactionEdit.selectedIds(table).equals(List.of(20L)),"Sorted selection uses stable model ID");
            table.addRowSelectionInterval(2,2);check(new HashSet<>(BulkTransactionEdit.selectedIds(table)).equals(Set.of(10L,20L)),"Disjoint multi-selection");
            var selected=BusinessLedger.class.getDeclaredMethod("selectedId",JTable.class);selected.setAccessible(true);
            boolean blocked=false;try{selected.invoke(null,table);}catch(java.lang.reflect.InvocationTargetException expected){blocked=expected.getCause() instanceof IllegalArgumentException;}
            check(blocked,"Single edit/delete guard rejects multi-selection");
            table.addRowSelectionInterval(1,1);rejected(()->BulkTransactionEdit.selectedIds(table),"Any selected transfer rejects whole batch despite colliding ID");
            @SuppressWarnings("unchecked") javax.swing.table.TableRowSorter<DefaultTableModel> sorter=(javax.swing.table.TableRowSorter<DefaultTableModel>)table.getRowSorter();
            sorter.setRowFilter(RowFilter.regexFilter("Income",2));table.setRowSelectionInterval(0,0);check(BulkTransactionEdit.selectedIds(table).equals(List.of(20L)),"Filtered selection does not include hidden rows");
            table.clearSelection();rejected(()->BulkTransactionEdit.selectedIds(table),"Empty selection rejected");
        }catch(Exception ex){throw new RuntimeException(ex);}});
        if(args.length==0){System.out.println("Sorted/filtered/disjoint selection, transfer exclusion and single-record action guards passed.");return;}
        if(args.length!=1||!args[0].startsWith("jdbc:postgresql://127.0.0.1:55439/"))throw new IllegalArgumentException("Fresh test database on 55439 required");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Statement s=db.createStatement()){
            sql(db,"CREATE TABLE categories(name TEXT PRIMARY KEY,active BOOLEAN DEFAULT TRUE)");
            sql(db,"CREATE TABLE accounts(id BIGINT PRIMARY KEY,name TEXT,active BOOLEAN DEFAULT TRUE)");
            sql(db,"CREATE TABLE transactions(id BIGINT PRIMARY KEY,entry_date DATE,kind TEXT,category TEXT REFERENCES categories(name),amount NUMERIC(14,2),party TEXT,notes TEXT,account_id BIGINT REFERENCES accounts(id))");
            sql(db,"CREATE TABLE transaction_edits(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,transaction_id BIGINT NOT NULL,edited_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,edited_by TEXT NOT NULL,reason TEXT NOT NULL,before_record JSONB NOT NULL,after_record JSONB NOT NULL)");
            sql(db,"INSERT INTO categories VALUES ('Other',TRUE),('Supplies',TRUE),('Old',FALSE)");sql(db,"INSERT INTO accounts VALUES (1,'Checking',TRUE),(2,'Savings',TRUE),(3,'Archived',FALSE)");
            sql(db,"INSERT INTO transactions VALUES (1,'2026-01-01','Expense','Other',10,'Shop','original note',1),(2,'2026-01-02','Income','Other',20,'Client','income note',NULL),(3,'2026-01-03','Expense','Supplies',30,'Other shop','keep',2),(4,'2026-01-04','Expense','Old',40,'Archived shop','archived',3)");
            var rows=BulkTransactionEdit.load(db,List.of(2L,1L,3L));
            var change=new BulkTransactionEdit.Change("Supplies",2L);
            var preview=BulkEditDialog.previewRows(rows,change,"Savings");check(preview.getRowCount()==3&&preview.getValueAt(1,7).equals("Unassigned")&&preview.getValueAt(2,9).equals("Unchanged"),"Preview includes current/new choices and unchanged rows");
            var result=BulkTransactionEdit.save(db,rows,change,"Tester","  Correct import assignments  ");check(result.updated()==2&&result.unchanged()==1,"Only actual changes updated");
            check(number(db,"SELECT count(*) FROM transaction_edits")==2,"One audit per changed record");
            check(text(db,"SELECT reason FROM transaction_edits LIMIT 1").equals("Correct import assignments"),"Trimmed shared reason");
            check(text(db,"SELECT before_record->>'account_name' FROM transaction_edits WHERE transaction_id=2").equals("Unassigned")&&text(db,"SELECT after_record->>'account_name' FROM transaction_edits WHERE transaction_id=2").equals("Savings"),"Audit snapshots include before/after account names");
            check(number(db,"SELECT count(*) FROM transaction_edits WHERE edited_by='Tester' AND before_record->>'category'='Other' AND after_record->>'category'='Supplies'")==2,"Actor and category history preserved");
            check(number(db,"SELECT SUM(amount) FROM transactions")==100&&text(db,"SELECT notes FROM transactions WHERE id=1").equals("original note")&&text(db,"SELECT kind FROM transactions WHERE id=2").equals("Income"),"Amounts, notes and types preserved");
            var same=BulkTransactionEdit.save(db,BulkTransactionEdit.load(db,List.of(1L,2L)),change,"Tester","Already assigned");check(same.updated()==0&&number(db,"SELECT count(*) FROM transaction_edits")==2,"No-op rows do not create audits");
            var archived=BulkTransactionEdit.load(db,List.of(4L));BulkTransactionEdit.save(db,archived,new BulkTransactionEdit.Change("Other",null),"Tester","Category only");check(number(db,"SELECT account_id FROM transactions WHERE id=4")==3,"Unchanged archived account preserved");
            long audits=number(db,"SELECT count(*) FROM transaction_edits");
            var stale=BulkTransactionEdit.load(db,List.of(1L,2L));sql(db,"UPDATE transactions SET notes='Someone changed this' WHERE id=2");
            rejected(()->BulkTransactionEdit.save(db,stale,new BulkTransactionEdit.Change("Other",1L),"Tester","Stale test"),"Stale preview rejected atomically");
            check(number(db,"SELECT count(*) FROM transactions WHERE id IN (1,2) AND category='Supplies' AND account_id=2")==2&&number(db,"SELECT count(*) FROM transaction_edits")==audits,"Stale batch made no partial changes/audits");
            var fresh=BulkTransactionEdit.load(db,List.of(1L,2L));
            rejected(()->BulkTransactionEdit.save(db,fresh,new BulkTransactionEdit.Change("Old",null),"Tester","Inactive"),"Archived target category rejected");
            rejected(()->BulkTransactionEdit.save(db,fresh,new BulkTransactionEdit.Change(null,3L),"Tester","Inactive"),"Archived target account rejected");
            rejected(()->BulkTransactionEdit.save(db,fresh,new BulkTransactionEdit.Change("Other",null),"Tester","  "),"Reason required");
            rejected(()->BulkTransactionEdit.save(db,fresh,new BulkTransactionEdit.Change(null,null),"Tester","No fields"),"At least one field required");
            sql(db,"ALTER TABLE transaction_edits ADD CONSTRAINT fail_second CHECK(transaction_id<>2 OR reason<>'force failure') NOT VALID");
            rejected(()->BulkTransactionEdit.save(db,fresh,new BulkTransactionEdit.Change("Other",1L),"Tester","force failure"),"Audit failure aborts whole batch");
            check(number(db,"SELECT count(*) FROM transactions WHERE id IN (1,2) AND category='Supplies' AND account_id=2")==2&&number(db,"SELECT count(*) FROM transaction_edits")==audits,"Updates and earlier audit rolled back");
            var dateRows=BulkTransactionEdit.load(db,List.of(1L,2L));
            var day=java.time.LocalDate.of(2026,2,28);
            var dateOnly=new BulkTransactionEdit.Change(null,null,day);
            var datePreview=BulkEditDialog.previewRows(dateRows,dateOnly,null);
            check(datePreview.getValueAt(0,1).equals(dateRows.getFirst().date())&&datePreview.getValueAt(0,10).equals(day),"Preview includes before/after dates");
            var dates=BulkTransactionEdit.save(db,dateRows,dateOnly,"Tester","Correct dates");
            check(dates.updated()==2&&number(db,"SELECT count(*) FROM transactions WHERE id IN (1,2) AND entry_date='2026-02-28' AND category='Supplies' AND account_id=2")==2,"Date-only edit preserves category/account");
            check(number(db,"SELECT count(*) FROM transaction_edits WHERE reason='Correct dates' AND before_record->>'entry_date'<>after_record->>'entry_date' AND after_record->>'entry_date'='2026-02-28'")==2,"Date history for each changed row");
            check(BulkTransactionEdit.save(db,BulkTransactionEdit.load(db,List.of(1L,2L)),dateOnly,"Tester","Same dates").updated()==0,"Same dates produce no edits");
            var dated=BulkTransactionEdit.load(db,List.of(1L,2L));
            rejected(()->BulkTransactionEdit.save(db,dated,new BulkTransactionEdit.Change("Other",1L,day.plusDays(1)),"Tester","force failure"),"Combined date edit rolls back on audit failure");
            check(number(db,"SELECT count(*) FROM transactions WHERE id IN (1,2) AND entry_date='2026-02-28' AND category='Supplies' AND account_id=2")==2,"Dates and assignments all roll back");
            BulkTransactionEdit.save(db,BulkTransactionEdit.load(db,List.of(3L)),new BulkTransactionEdit.Change("Other",1L,day),"Tester","Combined change");
            check(number(db,"SELECT count(*) FROM transactions WHERE id=3 AND entry_date='2026-02-28' AND category='Other' AND account_id=1")==1,"Date/category/account save together");
            var staleDate=BulkTransactionEdit.load(db,List.of(1L,2L));sql(db,"UPDATE transactions SET entry_date='2026-03-01' WHERE id=2");
            rejected(()->BulkTransactionEdit.save(db,staleDate,dateOnly,"Tester","Stale date"),"Date changed after preview rejected");
            var deleted=BulkTransactionEdit.load(db,List.of(1L,2L));sql(db,"DELETE FROM transactions WHERE id=2");
            rejected(()->BulkTransactionEdit.save(db,deleted,new BulkTransactionEdit.Change("Other",null),"Tester","Deleted"),"Deleted selected record aborts entire batch");
            check(text(db,"SELECT category FROM transactions WHERE id=1").equals("Supplies")&&db.getAutoCommit(),"Connection restored and surviving record preserved");
            System.out.println("Bulk preview, atomic updates/audits, unchanged/archived originals, active targets, stale/deleted records and audit-failure rollback passed.");
        }
    }
}

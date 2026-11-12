package com.businessledger;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Run with a fresh disposable database URL on port 55439. */
public final class EditIntegrationCheck {
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !args[0].contains(":55439/")) throw new IllegalArgumentException("Use disposable port 55439.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","")) {
            SwingUtilities.invokeAndWait(() -> {
                BusinessLedger app=null;
                try {
                    Constructor<BusinessLedger> ctor=BusinessLedger.class.getDeclaredConstructor(Connection.class); ctor.setAccessible(true); app=ctor.newInstance(db);
                    long id,savings;
                    try(Statement s=db.createStatement();ResultSet r=s.executeQuery("INSERT INTO transactions(entry_date,kind,category,amount,party,notes,account_id) SELECT DATE '2026-02-02','Expense','Food',7,'Old payee','Original note',id FROM accounts WHERE name='Checking' RETURNING id")) { r.next();id=r.getLong(1); }
                    try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id FROM accounts WHERE name='Savings'")) { r.next();savings=r.getLong(1); }
                    Method edit=BusinessLedger.class.getDeclaredMethod("saveTransactionEdit",long.class,LocalDate.class,String.class,String.class,BigDecimal.class,String.class,String.class,Long.class,String.class); edit.setAccessible(true);
                    Object[] values={id,LocalDate.of(2027,3,4),"Income","Supplies",new BigDecimal("123.45"),"New customer","Updated note",savings,""};
                    boolean rejected=false;
                    try { edit.invoke(app,values); } catch(InvocationTargetException ex) { rejected=ex.getCause() instanceof IllegalArgumentException; }
                    check(rejected,"Blank reason rejected");
                    values[8]="Corrected receipt details"; edit.invoke(app,values);
                    Method refresh=BusinessLedger.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(app);
                    Field field=BusinessLedger.class.getDeclaredField("transactions");field.setAccessible(true);DefaultTableModel rows=(DefaultTableModel)field.get(app);
                    check(rows.getRowCount()==1 && rows.getValueAt(0,9).equals("Edited"),"Edited marker shown without duplicating record");
                    check(((Number)rows.getValueAt(0,0)).longValue()==id && rows.getValueAt(0,1).toString().equals("2027-03-04"),"ID preserved and date updated");
                    check(rows.getValueAt(0,2).equals("Income") && rows.getValueAt(0,3).equals("Supplies") && rows.getValueAt(0,5).equals("New customer") && rows.getValueAt(0,6).equals("Updated note") && rows.getValueAt(0,7).equals("Savings"),"All editable fields saved");
                    check(((BigDecimal)rows.getValueAt(0,8)).compareTo(new BigDecimal("123.45"))==0,"Balance recalculated");
                    try(PreparedStatement s=db.prepareStatement("SELECT reason,before_record->>'party',after_record->>'party',before_record->>'account_name',after_record->>'account_name',edited_by FROM transaction_edits WHERE transaction_id=?")) {
                        s.setLong(1,id);try(ResultSet r=s.executeQuery()) {check(r.next(),"Audit saved");check(r.getString(1).equals("Corrected receipt details") && r.getString(2).equals("Old payee") && r.getString(3).equals("New customer") && r.getString(4).equals("Checking") && r.getString(5).equals("Savings") && r.getString(6).equals("Local user"),"Before/after, reason, and editor preserved");}
                    }
                    rejected=false;try{edit.invoke(app,values);}catch(InvocationTargetException ex){rejected=ex.getCause() instanceof IllegalArgumentException;}check(rejected,"No-op edit rejected");
                    values[2]="Invalid type";rejected=false;try{edit.invoke(app,values);}catch(InvocationTargetException ex){rejected=ex.getCause() instanceof SQLException;}check(rejected,"Invalid edit rolled back");
                    try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT kind,(SELECT count(*) FROM transaction_edits) FROM transactions")) {r.next();check(r.getString(1).equals("Income") && r.getInt(2)==1,"Failed/no-op edits leave record and audit unchanged");}
                    System.out.println("Transaction edit, audit, marker, and rollback checks passed.");
                } catch(Exception ex){throw new RuntimeException(ex);}
                finally{if(app!=null)app.dispose();}
            });
        }
    }
}

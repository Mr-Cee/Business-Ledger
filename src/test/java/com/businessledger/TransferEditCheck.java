package com.businessledger;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public final class TransferEditCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Object field(Object app,String name)throws Exception{Field f=BusinessLedger.class.getDeclaredField(name);f.setAccessible(true);return f.get(app);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1 || !args[0].contains(":55439/"))throw new IllegalArgumentException("Use fresh test database on port 55439.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","")){
            SwingUtilities.invokeAndWait(()->{
                BusinessLedger app=null;
                try{
                    Constructor<BusinessLedger> ctor=BusinessLedger.class.getDeclaredConstructor(Connection.class);ctor.setAccessible(true);app=ctor.newInstance(db);
                    long transferId,checking,savings;
                    try(Statement s=db.createStatement()){
                        s.executeUpdate("UPDATE accounts SET opening_balance=1000 WHERE name='Checking'");s.executeUpdate("UPDATE accounts SET opening_balance=-200 WHERE name='Credit'");
                        try(ResultSet r=s.executeQuery("SELECT id FROM accounts WHERE name='Checking'")){r.next();checking=r.getLong(1);}
                        try(ResultSet r=s.executeQuery("SELECT id FROM accounts WHERE name='Savings'")){r.next();savings=r.getLong(1);}
                        try(ResultSet r=s.executeQuery("INSERT INTO transfers(transfer_date,from_account_id,to_account_id,amount,notes) SELECT DATE '2026-11-01',a.id,b.id,50,'Original' FROM accounts a,accounts b WHERE a.name='Checking' AND b.name='Credit' RETURNING id")){r.next();transferId=r.getLong(1);}
                        s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount,party,notes,account_id) SELECT DATE '2026-11-02','Expense','Food',10,'Shop','',id FROM accounts WHERE name='Checking'");
                    }
                    Method edit=BusinessLedger.class.getDeclaredMethod("saveTransferEdit",long.class,LocalDate.class,long.class,long.class,BigDecimal.class,String.class,String.class);edit.setAccessible(true);
                    Object[] values={transferId,LocalDate.of(2026,11,3),checking,savings,new BigDecimal("75.00"),"Corrected note",""};
                    boolean rejected=false;try{edit.invoke(app,values);}catch(InvocationTargetException ex){rejected=ex.getCause() instanceof IllegalArgumentException;}check(rejected,"Reason required");
                    values[6]="Corrected bank statement";edit.invoke(app,values);
                    Method refresh=BusinessLedger.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(app);
                    DefaultTableModel history=(DefaultTableModel)field(app,"transfers"),ledger=(DefaultTableModel)field(app,"transactions");
                    check(history.getRowCount()==1 && history.getValueAt(0,6).equals("Edited"),"Transfer history marker and stable row count");
                    int transferRows=0;
                    for(int row=0;row<ledger.getRowCount();row++){
                        if(ledger.getValueAt(row,2).toString().startsWith("Transfer ")){
                            transferRows++;check(ledger.getValueAt(row,9).equals("Edited") && ledger.getValueAt(row,1).toString().equals("2026-11-03") && ((BigDecimal)ledger.getValueAt(row,4)).compareTo(new BigDecimal("75"))==0,"Both ledger rows show changed date, amount, marker");
                        }else check(ledger.getValueAt(row,9).equals(""),"Transfer edit does not mark a transaction with matching numeric ID");
                    }
                    check(transferRows==2,"Editing transfer does not duplicate its two ledger rows");
                    try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT name,balance FROM account_balances ORDER BY name")){
                        while(r.next()){String name=r.getString(1);BigDecimal expected=switch(name){case "Checking"->new BigDecimal("915");case "Credit"->new BigDecimal("-200");default->new BigDecimal("75");};check(r.getBigDecimal(2).compareTo(expected)==0,"Balance updates for old and new transfer accounts");}
                    }
                    try(PreparedStatement s=db.prepareStatement("SELECT reason,before_record->>'to_account_name',after_record->>'to_account_name',before_record->>'amount',after_record->>'amount',edited_by FROM transfer_edits WHERE transfer_id=?")){
                        s.setLong(1,transferId);try(ResultSet r=s.executeQuery()){r.next();check(r.getString(1).equals("Corrected bank statement") && r.getString(2).equals("Credit") && r.getString(3).equals("Savings") && r.getString(4).equals("50.00") && r.getString(5).equals("75.00") && r.getString(6).equals("Local user"),"Transfer audit stores original and updated values");}
                    }
                    rejected=false;try{edit.invoke(app,values);}catch(InvocationTargetException ex){rejected=ex.getCause() instanceof IllegalArgumentException;}check(rejected,"Unchanged edit rejected");
                    try(Statement s=db.createStatement()){s.executeUpdate("ALTER TABLE transfer_edits ADD CONSTRAINT reject_reason CHECK(reason<>'blocked') NOT VALID");}
                    values[4]=new BigDecimal("76");values[6]="blocked";rejected=false;
                    try{edit.invoke(app,values);}catch(InvocationTargetException ex){rejected=ex.getCause() instanceof SQLException;}check(rejected,"Audit failure rejects edit");
                    try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT amount,(SELECT count(*) FROM transfer_edits) FROM transfers")){r.next();check(r.getBigDecimal(1).compareTo(new BigDecimal("75"))==0 && r.getInt(2)==1,"Transfer and history roll back together");}
                    System.out.println("Transfer edit, history, markers, balances, and atomic rollback checks passed.");
                }catch(Exception ex){throw new RuntimeException(ex);}finally{if(app!=null)app.dispose();}
            });
        }
    }
}

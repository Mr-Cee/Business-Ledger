package com.businessledger;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;

public final class AccountLedgerCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static BigDecimal money(String value){return new BigDecimal(value);}
    static void equal(BigDecimal value,String expected,String message){check(value.compareTo(money(expected))==0,message+": "+value);}
    static Object field(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    static JButton button(Container parent,String text){for(Component component:parent.getComponents()){if(component instanceof JButton b&&text.equals(b.getText()))return b;if(component instanceof Container child){JButton result=button(child,text);if(result!=null)return result;}}return null;}
    static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container container)layout(container);}
    public static void main(String[] args)throws Exception{
        var row=new AccountLedgerPanel.Entry(1,LocalDate.of(2026,1,2),"Transfer Out","Transfer",money("20"),"To: Savings","",money("80"),"");
        var data=new AccountLedgerPanel.Data(1,"Checking","Checking",true,money("100"),List.of(row));
        equal(data.summary(null,null).closing(),"80","Transfer out affects balance");
        equal(data.summary(LocalDate.of(2026,2,1),null).opening(),"80","Prior movement carried forward");
        if(args.length==0){System.out.println("Account ledger summary checks passed; database checks require a disposable 55439 database.");return;}
        if(args.length!=1||!args[0].startsWith("jdbc:postgresql://127.0.0.1:55439/"))throw new IllegalArgumentException("Fresh test database on 55439 required.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Statement statement=db.createStatement()){
            statement.executeUpdate("CREATE TABLE ledger_settings(name TEXT PRIMARY KEY,value TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE accounts(id BIGINT PRIMARY KEY,name TEXT,account_type TEXT,opening_balance NUMERIC(14,2),active BOOLEAN)");
            statement.executeUpdate("CREATE TABLE transactions(id BIGINT,account_id BIGINT,entry_date DATE,kind TEXT,category TEXT,amount NUMERIC(14,2),party TEXT DEFAULT '',notes TEXT DEFAULT '')");
            statement.executeUpdate("CREATE TABLE transfers(id BIGINT,from_account_id BIGINT,to_account_id BIGINT,transfer_date DATE,amount NUMERIC(14,2),notes TEXT DEFAULT '')");
            statement.executeUpdate("CREATE TABLE transaction_edits(transaction_id BIGINT)");statement.executeUpdate("CREATE TABLE transfer_edits(transfer_id BIGINT)");
            statement.executeUpdate("CREATE TABLE mileage(trip_date DATE,miles NUMERIC)");
            statement.executeUpdate("INSERT INTO accounts VALUES(1,'Checking','Checking',100,TRUE),(2,'Savings','Savings',200,TRUE),(3,'Credit','Credit',-300,TRUE),(4,'Old','Cash',25,FALSE),(5,'Empty','Cash',10,TRUE)");
            statement.executeUpdate("INSERT INTO transactions(id,account_id,entry_date,kind,category,amount,party) VALUES(1,1,'2025-12-31','Income','Other',50,'Prior'),(2,1,'2026-01-02','Expense','Supplies',25,'Shop'),(3,1,'2026-01-02','Income','Other',100,'Customer'),(4,3,'2026-01-03','Expense','Supplies',40,'Card shop'),(5,2,'2026-01-05','Income','Other',999,'Other account'),(6,1,'2026-02-01','Expense','Supplies',10,'Future'),(7,NULL,'2026-01-01','Income','Other',777,'Unassigned'),(8,4,'2026-01-03','Expense','Supplies',5,'Archived')");
            statement.executeUpdate("INSERT INTO transfers(id,from_account_id,to_account_id,transfer_date,amount) VALUES(1,1,2,'2026-01-02',20),(2,2,1,'2026-01-03',5),(3,1,3,'2026-01-04',50)");
            statement.executeUpdate("INSERT INTO transaction_edits VALUES(1)");statement.executeUpdate("INSERT INTO transfer_edits VALUES(1)");
            new IncomeScope("Paycheck").save(db);
            LocalDate start=LocalDate.of(2026,1,1),end=LocalDate.of(2026,1,31);
            var account=AccountLedgerPanel.load(db,1);check(account.entries().size()==7,"Only chosen account entries");
            equal(account.entries().get(1).balance(),"130","Same-day transfer precedes expense/income");
            equal(account.entries().get(2).balance(),"105","Expense running balance");equal(account.entries().get(3).balance(),"205","Income running balance");
            check(account.entries().get(0).edited().equals("Edited")&&account.entries().get(1).edited().equals("Edited"),"Separate transaction/transfer audit IDs");
            var month=account.summary(start,end);equal(month.opening(),"150","Carry-forward opening");equal(month.in(),"105","All income and transfer in despite income scope");equal(month.out(),"95","Expenses and transfers out");equal(month.closing(),"160","Closing snapshot");
            equal(account.summary(null,null).closing(),"150","All recorded dates include future entry");
            equal(AccountLedgerPanel.load(db,3).summary(null,null).closing(),"-290","Negative credit balance and payment");
            check(!AccountLedgerPanel.load(db,4).active(),"Archived account supported");
            equal(AccountLedgerPanel.load(db,5).summary(start,end).closing(),"10","Empty account opening retained");
            Object[] dashboard=DashboardPanel.load(db,start,end).accounts().stream().filter(values->((Number)values[3]).longValue()==1).findFirst().orElseThrow();equal((BigDecimal)dashboard[2],"160","Dashboard snapshot matches ledger");
            Files.createDirectories(Path.of("target/account-ledger"));
            SwingUtilities.invokeAndWait(()->{try{
                AccountLedgerPanel.Entry[] chosen={null};AccountLedgerPanel panel=new AccountLedgerPanel(db,1,start,end,entry->chosen[0]=entry,entry->chosen[0]=entry);
                JTable table=(JTable)field(panel,"table");LedgerFilters filters=(LedgerFilters)field(panel,"filters");
                check(table.getRowCount()==5,"Dashboard date range applied");
                JTextField search=(JTextField)field(filters,"search");search.setText("To: Savings");filters.applyNow();check(table.getRowCount()==1,"Search specific transfer");
                equal((BigDecimal)table.getModel().getValueAt(table.convertRowIndexToModel(0),8),"130","Search preserves full running balance");
                table.setRowSelectionInterval(0,0);button(panel,"View edit history").doClick();check(chosen[0].transfer()&&chosen[0].id()==1,"Audit action uses transfer identity");
                filters.clear();check(table.getRowCount()==7,"Clear filters retains account restriction");filters.setDates(start,end);
                table.getRowSorter().toggleSortOrder(4);table.getRowSorter().toggleSortOrder(4);
                for(int view=0;view<table.getRowCount();view++){int model=table.convertRowIndexToModel(view);if(table.getModel().getValueAt(model,2).equals("Transfer Out")&&((Long)table.getModel().getValueAt(model,0))==1){table.setRowSelectionInterval(view,view);break;}}
                button(panel,"Edit selected").doClick();check(chosen[0].transfer()&&chosen[0].id()==1,"Sorted editing targets correct record");
                for(AppTheme.Style style:new AppTheme.Style[]{AppTheme.Style.OCEAN,AppTheme.Style.MIDNIGHT}){AppTheme.apply(style);AccountLedgerPanel themed=new AccountLedgerPanel(db,1,start,end,entry->{},entry->{});themed.setSize(650,650);for(int pass=0;pass<3;pass++)layout(themed);BufferedImage image=new BufferedImage(650,650,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();themed.printAll(graphics);graphics.dispose();ImageIO.write(image,"png",Path.of("target/account-ledger/"+style.name().toLowerCase()+".png").toFile());themed.stopPending();}
                panel.stopPending();
            }catch(Exception ex){throw new RuntimeException(ex);}});
            statement.executeUpdate("UPDATE transfers SET amount=30,transfer_date='2026-02-02' WHERE id=1");
            var changed=AccountLedgerPanel.load(db,1);equal(changed.summary(start,end).closing(),"180","Edited transfer moved outside period");equal(changed.summary(null,null).closing(),"140","Edited transfer actual balance");
            System.out.println("Account ledger database/UI checks passed: isolation, transfers, ordering, carry-forward dates, negative/empty/archived accounts, audit IDs, search/sort/edit routing, dashboard reconciliation, and light/dark narrow rendering.");
        }
    }
}

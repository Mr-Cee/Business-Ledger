package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;

public final class DashboardCheck {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1||!args[0].contains(":55439/"))throw new IllegalArgumentException("Disposable test database required.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Statement s=db.createStatement()){
            s.executeUpdate("CREATE TABLE ledger_settings(name TEXT PRIMARY KEY,value TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE accounts(id BIGINT,name TEXT,account_type TEXT,opening_balance NUMERIC,active BOOLEAN)");
            s.executeUpdate("CREATE TABLE transactions(account_id BIGINT,entry_date DATE,kind TEXT,category TEXT,amount NUMERIC)");
            s.executeUpdate("CREATE TABLE transfers(from_account_id BIGINT,to_account_id BIGINT,transfer_date DATE,amount NUMERIC)");
            s.executeUpdate("CREATE TABLE mileage(trip_date DATE,miles NUMERIC)");
            s.executeUpdate("INSERT INTO accounts VALUES (1,'Checking','Checking',100,TRUE),(2,'Savings','Savings',200,TRUE),(3,'Old cash','Cash',0,FALSE)");
            s.executeUpdate("INSERT INTO transactions VALUES (1,'2025-12-31','Income','Other',10),(1,'2026-01-01','Income','Paycheck',1000.10),(1,'2026-01-31','Expense','Food',100.05),(1,'2026-02-01','Expense','Food',999)");
            s.executeUpdate("INSERT INTO transfers VALUES (1,2,'2026-01-15',250),(2,1,'2026-02-01',50)");
            s.executeUpdate("INSERT INTO mileage VALUES ('2026-01-01',12.25),('2026-01-31',20.50),('2026-02-01',999),('2024-01-01',1)");
            LocalDate start=LocalDate.of(2026,1,1),end=LocalDate.of(2026,1,31);DashboardPanel.Data data=DashboardPanel.load(db,start,end);
            check(data.activity().income().compareTo(new BigDecimal("1000.10"))==0,"Income excludes transfers and prior dates");
            check(data.activity().expenses().compareTo(new BigDecimal("100.05"))==0,"Expense boundaries");
            check(data.miles().compareTo(new BigDecimal("32.75"))==0,"Mileage inclusive dates");
            check(data.accounts().size()==2,"Archived zero account omitted");
            check(((BigDecimal)data.accounts().get(0)[2]).compareTo(new BigDecimal("760.05"))==0,"Checking includes prior activity and transfer, excludes future");
            check(((BigDecimal)data.accounts().get(1)[2]).compareTo(new BigDecimal("450"))==0,"Savings transfer balance");
            check(DashboardPanel.load(db,LocalDate.of(2023,1,1),LocalDate.of(2023,1,31)).activity().income().signum()==0,"Empty period");
            try{DashboardPanel.load(db,end,start);throw new AssertionError("Invalid dates accepted");}catch(IllegalArgumentException expected){}
            SwingUtilities.invokeAndWait(()->{try{
                for(AppTheme.Style theme:new AppTheme.Style[]{AppTheme.Style.OCEAN,AppTheme.Style.MIDNIGHT}){
                    AppTheme.apply(theme);
                    DashboardPanel dashboard=new DashboardPanel(db);dashboard.render(data,end);
                    for(int width:new int[]{600,1100}){
                        dashboard.setSize(width,800);for(int i=0;i<4;i++)layout(dashboard);
                        BufferedImage image=new BufferedImage(width,800,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();dashboard.printAll(g);g.dispose();ImageIO.write(image,"png",new File("target/dashboard-"+theme.name().toLowerCase()+"-"+width+".png"));
                        check(cardsInside(dashboard),"Summary card clipped at "+width);
                    }
                }
                JTextField from=new JTextField(),through=new JTextField();DateRangeControls range=new DateRangeControls(db,from,through,()->{},true);
                range.range.setSelectedIndex(10);range.applyToDates();check(from.getText().equals("2024-01-01"),"All dates includes mileage-only history");
            }catch(Exception ex){throw new RuntimeException(ex);}});
            System.out.println("Dashboard totals, transfer exclusion, balances, mileage, date validation, empty periods, and responsive theme rendering passed.");
        }
    }
    private static boolean cardsInside(Container root){
        for(Component child:root.getComponents()){
            if(child instanceof WrapPanel panel){for(Component card:panel.getComponents())if(card.getPreferredSize().height==120 && card.getX()+card.getWidth()>panel.getWidth())return false;}
            if(child instanceof Container container&&!cardsInside(container))return false;
        }return true;
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container container)layout(container);}
}

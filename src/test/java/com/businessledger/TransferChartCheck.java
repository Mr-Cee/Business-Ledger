package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class TransferChartCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void amount(BigDecimal value,String expected,String message){check(value.compareTo(new BigDecimal(expected))==0,message+": "+value);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1||!args[0].contains(":55439/"))throw new IllegalArgumentException("Fresh disposable database required.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Statement s=db.createStatement()){
            s.executeUpdate("CREATE TABLE accounts(id BIGINT PRIMARY KEY,name TEXT,active BOOLEAN)");
            s.executeUpdate("CREATE TABLE transfers(from_account_id BIGINT REFERENCES accounts(id),to_account_id BIGINT REFERENCES accounts(id),transfer_date DATE,amount NUMERIC(14,2))");
            s.executeUpdate("INSERT INTO accounts VALUES (1,'Checking',TRUE),(2,'Savings',TRUE),(3,'Old credit',FALSE),(4,'Unused cash',TRUE)");
            s.executeUpdate("INSERT INTO transfers VALUES (1,2,'2026-01-01',100.10),(1,2,'2026-01-31',50.05),(2,1,'2026-01-15',20),(1,3,'2026-01-16',30),(3,2,'2026-01-17',5),(1,2,'2025-12-31',999),(1,2,'2026-02-01',999)");
            LocalDate start=LocalDate.of(2026,1,1),end=LocalDate.of(2026,1,31);TransferChart.Data data=TransferChart.load(db,start,end);
            amount(data.total(),"205.15","Each transfer counted once with inclusive dates");check(data.count()==5&&data.routes().size()==4,"Counts and directed route groups");
            TransferChart.Account checking=data.accounts().stream().filter(a->a.id()==1).findFirst().orElseThrow();
            amount(checking.incoming(),"20","Checking incoming");amount(checking.outgoing(),"180.15","Checking outgoing");amount(checking.net(),"-160.15","Checking net movement");check(checking.count()==4,"Per-account transfer count");
            check(data.accounts().stream().anyMatch(a->a.name().contains("(archived)")),"Archived accounts included");
            check(data.accounts().stream().filter(a->a.id()==4).findFirst().orElseThrow().count()==0,"Zero-movement account included");
            amount(data.accounts().stream().map(TransferChart.Account::incoming).reduce(BigDecimal.ZERO,BigDecimal::add),"205.15","Incoming totals reconcile");
            amount(data.accounts().stream().map(TransferChart.Account::outgoing).reduce(BigDecimal.ZERO,BigDecimal::add),"205.15","Outgoing totals reconcile");
            amount(data.accounts().stream().map(TransferChart.Account::net).reduce(BigDecimal.ZERO,BigDecimal::add),"0","Net account movements reconcile to zero");
            amount(data.routes().get(0).amount(),"150.15","Repeated same-direction transfers aggregate");
            check(data.routes().stream().anyMatch(r->r.from().equals("Savings")&&r.to().equals("Checking")),"Reverse route distinct");
            check(TransferChart.load(db,LocalDate.of(2024,1,1),LocalDate.of(2024,1,31)).count()==0,"Empty date range");
            SwingUtilities.invokeAndWait(()->{try{
                for(AppTheme.Style theme:new AppTheme.Style[]{AppTheme.Style.OCEAN,AppTheme.Style.MIDNIGHT}){
                    AppTheme.apply(theme);TransferChart chart=new TransferChart();
                    for(boolean routes:new boolean[]{false,true})for(int width:new int[]{600,1100}){
                        chart.setData(data,routes);chart.setSize(width,chart.getPreferredSize().height);
                        String info=chart.getToolTipText(new MouseEvent(chart,MouseEvent.MOUSE_MOVED,0,0,40,100,0,false));
                        check(info!=null&&info.contains("Transfers:")&&info.contains("$"),"Hover exact amounts and counts");
                        BufferedImage image=new BufferedImage(width,chart.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();chart.printAll(g);g.dispose();ImageIO.write(image,"png",new File("target/transfers-"+theme.name().toLowerCase()+"-"+(routes?"routes":"accounts")+"-"+width+".png"));
                    }
                    chart.setData(TransferChart.empty(),false);check(chart.getToolTipText(new MouseEvent(chart,MouseEvent.MOUSE_MOVED,0,0,40,100,0,false))==null,"No stale tooltip");
                }
                ChartsPanel charts=new ChartsPanel(db);((JTextField)SettingsIntegrationCheck.field(charts,"from")).setText(start.toString());((JTextField)SettingsIntegrationCheck.field(charts,"through")).setText(end.toString());
                ((JComboBox<?>)SettingsIntegrationCheck.field(charts,"view")).setSelectedIndex(5);
                DefaultTableModel table=(DefaultTableModel)SettingsIntegrationCheck.field(charts,"table");check(table.getRowCount()==4&&table.getColumnCount()==5,"Account table rendered");
                check(table.getColumnClass(1)==BigDecimal.class&&table.getColumnClass(4)==Long.class,"Account numeric sorting");
                ((JComboBox<?>)SettingsIntegrationCheck.field(charts,"transferView")).setSelectedIndex(1);
                check(table.getRowCount()==4&&table.getColumnCount()==4&&table.getColumnClass(1)==String.class&&table.getColumnClass(3)==Long.class,"Route table rendered with correct column types");
                ((JTable)SettingsIntegrationCheck.field(charts,"numbers")).getRowSorter().toggleSortOrder(2);
                ((JComboBox<?>)SettingsIntegrationCheck.field(charts,"transferView")).setSelectedIndex(0);check(table.getColumnCount()==5,"Switching modes after sorting");
                ((JTextField)SettingsIntegrationCheck.field(charts,"from")).setText("bad date");charts.refreshSafely();check(table.getRowCount()==0,"Invalid dates clear previous results");
            }catch(Exception ex){throw new RuntimeException(ex);}});
            System.out.println("Transfer date boundaries, exact totals, directed routes, archived/empty accounts, reconciliation, hover, responsive themes, sorting, and Charts integration passed.");
        }
    }
}

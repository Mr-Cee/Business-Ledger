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
import java.time.*;
import java.util.*;
import java.util.List;

public final class PayeePieCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void amount(BigDecimal value,String expected,String message){check(value.compareTo(new BigDecimal(expected))==0,message+": "+value);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1||!args[0].contains(":55439/"))throw new IllegalArgumentException("Fresh disposable database required.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Statement s=db.createStatement()){
            s.executeUpdate("CREATE TABLE ledger_settings(name TEXT PRIMARY KEY,value TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE transactions(entry_date DATE,kind TEXT,category TEXT,amount NUMERIC(14,2),party TEXT)");
            s.executeUpdate("CREATE TABLE transfers(transfer_date DATE,amount NUMERIC(14,2))");
            s.executeUpdate("INSERT INTO transfers VALUES ('2026-01-15',99999)");
            for(int i=1;i<=10;i++)s.executeUpdate("INSERT INTO transactions VALUES ('2026-01-01','Expense','Supplies',"+(i*100)+",'Payee "+i+"')");
            s.executeUpdate("INSERT INTO transactions VALUES ('2026-01-31','Expense','Food',0.01,'  Payee 10  '),('2026-01-02','Expense','Food',25,''),('2026-01-03','Expense','Food',10,'   '),('2026-01-04','Income','Paycheck',99999,'Payee 10'),('2025-12-31','Expense','Other',99999,'Outside'),('2026-02-01','Expense','Other',99999,'Outside')");
            LocalDate start=LocalDate.of(2026,1,1),end=LocalDate.of(2026,1,31);
            List<PayeePieChart.Slice> slices=PayeePieChart.load(db,start,end);
            check(slices.size()==9,"Top eight plus Other");check(slices.get(0).label().equals("Payee 10"),"Sorted by spend with trimmed payee names");
            amount(slices.get(0).amount(),"1000.01","Exact amount and inclusive date boundaries");check(slices.get(0).count()==2,"Expense count excludes income");
            amount(slices.get(8).amount(),"335","Other includes remaining payees and unspecified names");check(slices.get(8).count()==4,"Other expense count");
            amount(slices.stream().map(PayeePieChart.Slice::amount).reduce(BigDecimal.ZERO,BigDecimal::add),"5535.01","Slices cover every expense, excluding transfers and outside dates");
            check(PayeePieChart.load(db,LocalDate.of(2024,1,1),LocalDate.of(2024,1,31)).isEmpty(),"Empty date range");
            SwingUtilities.invokeAndWait(()->{try{
                for(AppTheme.Style theme:new AppTheme.Style[]{AppTheme.Style.OCEAN,AppTheme.Style.MIDNIGHT}){
                    AppTheme.apply(theme);PayeePieChart pie=new PayeePieChart();pie.setSlices(slices);
                    for(int width:new int[]{600,1100}){
                        pie.setSize(width,pie.getPreferredSize().height);pie.setSize(width,pie.getPreferredSize().height);
                        Set<String> hover=new HashSet<>();for(int x=15;x<width;x+=15)for(int y=15;y<pie.getHeight();y+=15){String info=pie.getToolTipText(new MouseEvent(pie,MouseEvent.MOUSE_MOVED,0,0,x,y,0,false));if(info!=null)hover.add(info);}
                        check(hover.size()==9,"Every slice or legend row has hover information at "+width);
                        check(hover.stream().anyMatch(t->t.contains("$1,000.01")&&t.contains("Expenses: 2")&&t.contains("%")),"Tooltip amount, count, and share");
                        BufferedImage image=new BufferedImage(width,pie.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();pie.printAll(g);g.dispose();ImageIO.write(image,"png",new File("target/payee-pie-"+theme.name().toLowerCase()+"-"+width+".png"));
                    }
                    pie.setSlices(List.of(new PayeePieChart.Slice("<html>Full name",new BigDecimal("10"),1)));
                    amount(pie.percent(new PayeePieChart.Slice("one",new BigDecimal("10"),1)),"100","Single payee share");
                    String tooltip=null;for(int x=20;x<pie.getWidth()&&tooltip==null;x+=20)for(int y=20;y<pie.getHeight()&&tooltip==null;y+=20)tooltip=pie.getToolTipText(new MouseEvent(pie,MouseEvent.MOUSE_MOVED,0,0,x,y,0,false));
                    check(tooltip!=null&&tooltip.startsWith("Payee: <html>"),"Payee text is not treated as tooltip markup");
                    pie.setSlices(List.of());check(pie.getToolTipText(new MouseEvent(pie,MouseEvent.MOUSE_MOVED,0,0,100,100,0,false))==null,"No stale empty tooltip");
                }
                ChartsPanel charts=new ChartsPanel(db);((JTextField)SettingsIntegrationCheck.field(charts,"from")).setText(start.toString());((JTextField)SettingsIntegrationCheck.field(charts,"through")).setText(end.toString());
                ((JComboBox<?>)SettingsIntegrationCheck.field(charts,"view")).setSelectedIndex(4);
                DefaultTableModel table=(DefaultTableModel)SettingsIntegrationCheck.field(charts,"table");check(table.getRowCount()==9&&table.getColumnCount()==3,"Chart selector renders pie and table");
                check(table.getColumnClass(1)==BigDecimal.class&&table.getColumnClass(2)==BigDecimal.class,"Numeric table sorting");
                ((JTextField)SettingsIntegrationCheck.field(charts,"from")).setText("bad date");charts.refreshSafely();check(table.getRowCount()==0,"Invalid dates clear previous data");
            }catch(Exception ex){throw new RuntimeException(ex);}});
            System.out.println("Top payee grouping, exact totals, date boundaries, expense-only filtering, hover details, single/empty data, responsive themes, and Charts integration passed.");
        }
    }
}

package com.businessledger;

import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class BalanceChartCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static BigDecimal at(BalanceChart.Data data,long account,int point){return data.series().stream().filter(s->s.id()==account).findFirst().orElseThrow().balances().get(point);}
    static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container child)layout(child);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1 || !args[0].contains(":55439/"))throw new IllegalArgumentException("Use fresh test database on port 55439.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Statement s=db.createStatement()){
            s.executeUpdate("CREATE TABLE accounts(id BIGINT PRIMARY KEY,name TEXT,opening_balance NUMERIC(14,2),active BOOLEAN)");
            s.executeUpdate("CREATE TABLE transactions(account_id BIGINT,entry_date DATE,kind TEXT,amount NUMERIC(14,2))");
            s.executeUpdate("CREATE TABLE transfers(from_account_id BIGINT,to_account_id BIGINT,transfer_date DATE,amount NUMERIC(14,2))");
            s.executeUpdate("INSERT INTO accounts VALUES (1,'Checking',1000,TRUE),(2,'Savings',200,TRUE),(3,'Credit',-300,TRUE),(4,'Dormant',50,FALSE)");
            s.executeUpdate("INSERT INTO transactions VALUES (1,'2025-12-31','Income',100),(1,'2026-01-01','Expense',25),(3,'2026-01-03','Expense',40),(1,'2026-01-05','Income',50),(1,'2026-02-01','Expense',100),(NULL,'2026-01-04','Income',999)");
            s.executeUpdate("INSERT INTO transfers VALUES (1,2,'2026-01-02',100)");
            s.executeUpdate("ALTER TABLE transactions ADD COLUMN category TEXT DEFAULT 'Other'");
            BalanceChart.Data daily=BalanceChart.load(db,LocalDate.of(2026,1,1),LocalDate.of(2026,1,5),BalanceChart.Frequency.DAILY);
            check(daily.dates().size()==5 && daily.series().size()==4,"Daily dates and all accounts including archived");
            check(at(daily,1,0).compareTo(new BigDecimal("1075"))==0,"Opening balance and movements before start carried forward");
            check(at(daily,1,1).compareTo(new BigDecimal("975"))==0 && at(daily,2,1).compareTo(new BigDecimal("300"))==0,"Both transfer sides included");
            check(at(daily,1,4).compareTo(new BigDecimal("1025"))==0 && at(daily,3,4).compareTo(new BigDecimal("-340"))==0,"Income, negative credit balances, end boundary, and future exclusion");
            check(at(daily,1,1).equals(at(daily,1,2)) && at(daily,4,4).compareTo(new BigDecimal("50"))==0,"No-activity dates carry balances");
            BalanceChart.Data monthly=BalanceChart.load(db,LocalDate.of(2025,12,20),LocalDate.of(2026,2,10),BalanceChart.Frequency.MONTHLY);
            check(monthly.dates().equals(java.util.List.of(LocalDate.of(2025,12,31),LocalDate.of(2026,1,31),LocalDate.of(2026,2,10))),"Monthly closing dates with partial final period");
            check(at(monthly,1,2).compareTo(new BigDecimal("925"))==0,"Monthly closing balance");
            BalanceChart.Data yearly=BalanceChart.load(db,LocalDate.of(2025,1,1),LocalDate.of(2026,2,10),BalanceChart.Frequency.YEARLY);
            check(yearly.dates().equals(java.util.List.of(LocalDate.of(2025,12,31),LocalDate.of(2026,2,10))) && at(yearly,1,1).compareTo(new BigDecimal("925"))==0,"Yearly closing balances");
            check(BalanceChart.load(db,LocalDate.of(2026,1,2),LocalDate.of(2026,1,2),BalanceChart.Frequency.DAILY).dates().size()==1,"Single-day range");
            SwingUtilities.invokeAndWait(()->{
                try{
                    for(String key:new String[]{"Label.font","Button.font","TextField.font","ComboBox.font","Table.font","Panel.font","TextArea.font"})UIManager.put(key,new javax.swing.plaf.FontUIResource("Dialog",Font.PLAIN,18));
                    ChartsPanel panel=new ChartsPanel(db);((JTextField)field(panel,"from")).setText("2026-01-01");((JTextField)field(panel,"through")).setText("2026-01-05");((JComboBox<?>)field(panel,"view")).setSelectedIndex(3);
                    check(((javax.swing.table.DefaultTableModel)field(panel,"table")).getRowCount()==5,"Balance chart exact-value table populated");
                    JComboBox<?> accounts=(JComboBox<?>)field(panel,"balanceAccount");check(accounts.getItemCount()==5,"All accounts plus individual accounts listed");
                    accounts.setSelectedIndex(2);
                    javax.swing.table.DefaultTableModel filtered=(javax.swing.table.DefaultTableModel)field(panel,"table");
                    check(filtered.getColumnCount()==2 && filtered.getColumnName(1).equals("Credit"),"Table shows only selected account");
                    BalanceChart.Data plotted=(BalanceChart.Data)field(field(panel,"balanceChart"),"data");check(plotted.series().size()==1 && plotted.series().get(0).name().equals("Credit"),"Chart shows only selected line");
                    ((JComboBox<?>)field(panel,"frequency")).setSelectedIndex(1);check(accounts.getSelectedItem().toString().equals("Credit") && filtered.getColumnCount()==2 && filtered.getRowCount()==1,"Account retained when interval changes");
                    ((JComboBox<?>)field(panel,"frequency")).setSelectedIndex(0);accounts.setSelectedIndex(0);check(filtered.getColumnCount()==5,"All accounts restores table and lines");
                    JComboBox<?> ranges=(JComboBox<?>)field(panel,"range");ranges.setSelectedIndex(10);
                    check(((JTextField)field(panel,"from")).getText().equals("2025-12-31") && ((JTextField)field(panel,"through")).getText().equals("2026-02-01"),"All recorded dates queries saved history");
                    ranges.setSelectedIndex(8);((JComboBox<?>)field(panel,"rangeYear")).getEditor().setItem("2026");((JComboBox<?>)field(panel,"rangeQuarter")).setSelectedIndex(0);
                    ((JComboBox<?>)field(panel,"view")).setSelectedIndex(0);
                    ChartsPanel.Data quarterData=(ChartsPanel.Data)field(panel,"data");check(quarterData.income().compareTo(new BigDecimal("1049"))==0 && quarterData.expenses().compareTo(new BigDecimal("165"))==0,"Quarter filter applies to income/expense charts including unassigned transactions");
                    ((JComboBox<?>)field(panel,"view")).setSelectedIndex(3);check(ranges.getSelectedIndex()==8 && filtered.getRowCount()==90,"Quarter range retained on balance chart");
                    ((JTextField)field(panel,"from")).setText("2026-01-01");((JTextField)field(panel,"through")).setText("2026-01-05");panel.refreshSafely();
                    for(int width:new int[]{600,1000}){
                        panel.setSize(width,760);for(int pass=0;pass<5;pass++)layout(panel);
                        BalanceChart line=(BalanceChart)field(panel,"balanceChart");check(line.getWidth()==line.getParent().getWidth() && line.getHeight()==line.getParent().getHeight(),"Full line graph fits viewport at width "+width);
                        BufferedImage image=new BufferedImage(width,760,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();ImageIO.write(image,"png",Path.of("target/balance-chart-"+width+".png").toFile());
                    }
                }catch(Exception ex){throw new RuntimeException(ex);}
            });
            s.executeUpdate("UPDATE transfers SET amount=150");
            BalanceChart.Data edited=BalanceChart.load(db,LocalDate.of(2026,1,2),LocalDate.of(2026,1,2),BalanceChart.Frequency.DAILY);
            check(at(edited,1,0).compareTo(new BigDecimal("925"))==0 && at(edited,2,0).compareTo(new BigDecimal("350"))==0,"Edited transfers recalculate both account lines");
            System.out.println("Daily/monthly/yearly balances, carry-forward, transfers, and rendering checks passed.");
        }
    }
}

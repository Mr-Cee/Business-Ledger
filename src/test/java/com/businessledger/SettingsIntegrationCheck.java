package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;

public final class SettingsIntegrationCheck {
    static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static Object call(Object target,String name)throws Exception{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(target);}
    static BusinessLedger open(Connection db)throws Exception{Constructor<BusinessLedger> c=BusinessLedger.class.getDeclaredConstructor(Connection.class);c.setAccessible(true);return c.newInstance(db);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void select(BusinessLedger app,String name)throws Exception{
        JComboBox<?> box=(JComboBox<?>)field(app,"templates");for(int i=0;i<box.getItemCount();i++)if(box.getItemAt(i).toString().equals(name)){box.setSelectedIndex(i);return;}throw new AssertionError(name);
    }
    static JButton save(Container root){for(Component child:root.getComponents()){if(child instanceof JButton b&&b.getText().equals("Save settings"))return b;if(child instanceof Container c){JButton found=save(c);if(found!=null)return found;}}return null;}
    public static void main(String[] args)throws Exception{
        System.setProperty("ledger.home","target/settings-dialog-check");
        if(args.length!=1||!args[0].contains(":55439/"))throw new IllegalArgumentException("Fresh disposable test database required.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","")){
            SwingUtilities.invokeAndWait(()->{BusinessLedger app=null;try{
                AppTheme.apply(AppTheme.Style.OCEAN);app=open(db);
                check(app.getJMenuBar().getMenuCount()==2&&app.getJMenuBar().getMenu(0).getText().equals("Settings")&&app.getJMenuBar().getMenu(1).getText().equals("Help"),"Settings and Help menus");
                check(app.getJMenuBar().getMenu(0).getItem(0).getAccelerator()!=null,"Settings shortcut");
                JTabbedPane tabs=(JTabbedPane)app.getContentPane().getComponent(0);
                check(tabs.getTabCount()==6,"Six data tabs; settings moved out");
                JDialog dialog=SettingsPanel.createDialog(app,db,()->{});
                try{
                    SettingsPanel dialogSettings=(SettingsPanel)dialog.getContentPane().getComponent(0);
                    ((JRadioButton)field(dialogSettings,"specific")).doClick();
                    ((JComboBox<?>)field(dialogSettings,"theme")).setSelectedItem(AppTheme.Style.MIDNIGHT);
                    check(AppTheme.current()==AppTheme.Style.MIDNIGHT,"Theme applies from settings dialog");
                    check(((JRadioButton)field(dialogSettings,"specific")).isSelected(),"Theme change preserves unsaved income selection");
                    check(IncomeScope.load(db).category()==null,"Theme change does not save income selection");
                    AppTheme.apply(AppTheme.Style.OCEAN);AppTheme.initialize();
                    check(AppTheme.current()==AppTheme.Style.MIDNIGHT,"Theme selection persists");
                    check(dialog.isModal(),"Settings dialog owned and modal");
                }finally{dialog.dispose();}
                try(Statement s=db.createStatement()){
                    s.executeUpdate("INSERT INTO categories(name) VALUES ('Gift income')");
                    s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount,account_id) SELECT '2026-01-10','Income','Paycheck',100,id FROM accounts WHERE name='Checking'");
                    s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount,account_id) SELECT '2026-01-11','Income','Gift income',50,id FROM accounts WHERE name='Checking'");
                    s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount,account_id) SELECT '2026-01-12','Expense','Gift income',20,id FROM accounts WHERE name='Checking'");
                    s.executeUpdate("INSERT INTO transfers(transfer_date,from_account_id,to_account_id,amount) SELECT '2026-01-15',a.id,b.id,10 FROM accounts a,accounts b WHERE a.name='Checking' AND b.name='Savings'");
                }
                LocalDate start=LocalDate.of(2026,1,1),end=LocalDate.of(2026,1,31);
                check(IncomeScope.load(db).category()==null,"Default all categories");
                check(ChartsPanel.load(db,start,end).income().compareTo(new BigDecimal("150"))==0,"All income categories counted");
                SettingsPanel settings=new SettingsPanel(db,()->{});((JRadioButton)field(settings,"specific")).doClick();
                ((JComboBox<?>)field(settings,"category")).setSelectedItem("Paycheck");save(settings).doClick();
                check("Paycheck".equals(IncomeScope.load(db).category()),"Settings save button persists category");
                ChartsPanel.Data chart=ChartsPanel.load(db,start,end);
                check(chart.income().compareTo(new BigDecimal("100"))==0,"Paycheck-only chart income");
                check(chart.expenses().compareTo(new BigDecimal("20"))==0,"Expenses in other categories retained");
                check(chart.months().get(YearMonth.of(2026,1))[0].compareTo(new BigDecimal("100"))==0,"Monthly bars filtered");
                DashboardPanel.Data dashboard=DashboardPanel.load(db,start,end);
                check(dashboard.activity().income().compareTo(new BigDecimal("100"))==0,"Dashboard filter");
                check(((BigDecimal)dashboard.accounts().get(0)[2]).compareTo(new BigDecimal("120"))==0,"Account balance includes all income and transfer");
                BalanceChart.Data balances=BalanceChart.load(db,start,end,BalanceChart.Frequency.MONTHLY);
                check(balances.series().get(0).balances().get(0).compareTo(new BigDecimal("120"))==0,"Balance graph unaffected");
                ((JTextField)field(app,"from")).setText(start.toString());((JTextField)field(app,"through")).setText(end.toString());
                select(app,"All Income");call(app,"runReport");DefaultTableModel report=(DefaultTableModel)field(app,"report");
                check(report.getRowCount()==1&&"Paycheck".equals(report.getValueAt(0,3)),"Detailed report category filter");
                check(field(app,"reportSummary").toString().contains("Income category: Paycheck"),"Export summary identifies scope");
                select(app,"All Transactions");call(app,"runReport");check(report.getRowCount()==2,"Mixed report preserves expenses");
                select(app,"All In/Out");call(app,"runReport");
                BigDecimal in=BigDecimal.ZERO,out=BigDecimal.ZERO;for(int row=0;row<report.getRowCount();row++){in=in.add((BigDecimal)report.getValueAt(row,1));out=out.add((BigDecimal)report.getValueAt(row,2));}
                check(in.compareTo(new BigDecimal("100"))==0&&out.compareTo(new BigDecimal("20"))==0,"All In/Out respects scope, excludes transfers");
                try(Statement s=db.createStatement()){s.executeUpdate("INSERT INTO report_templates(name,detail) VALUES ('Summary check',FALSE)");}
                Method reload=BusinessLedger.class.getDeclaredMethod("reloadTemplates",Long.class);reload.setAccessible(true);reload.invoke(app,new Object[]{null});
                select(app,"Summary check");call(app,"runReport");check(report.getRowCount()==2,"Grouped summary respects scope");
                new IncomeScope("Gift income").save(db);check(ChartsPanel.load(db,start,end).income().compareTo(new BigDecimal("50"))==0,"Alternative category supported");
                new IncomeScope("Paycheck").save(db);app.dispose();app=open(db);check("Paycheck".equals(IncomeScope.load(db).category()),"Setting survives reopening app");
                settings=new SettingsPanel(db,()->{});check(((JRadioButton)field(settings,"specific")).isSelected(),"Saved selection restored");
                for(int width:new int[]{600,1100}){settings.setSize(width,600);for(int i=0;i<4;i++)layout(settings);BufferedImage image=new BufferedImage(width,600,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();settings.printAll(g);g.dispose();ImageIO.write(image,"png",new File("target/settings-"+width+".png"));}
                ((JRadioButton)field(settings,"all")).doClick();save(settings).doClick();check(ChartsPanel.load(db,start,end).income().compareTo(new BigDecimal("150"))==0,"Restoring all income categories");
                try{new IncomeScope("Not a category").save(db);throw new AssertionError("Invalid category accepted");}catch(IllegalArgumentException expected){}
                new IncomeScope("Paycheck").save(db);
                System.out.println("Settings menu/dialog, theme persistence, income settings, charts, dashboard, reports, balances, and reopening checks passed.");
            }catch(Exception ex){throw new RuntimeException(ex);}finally{if(app!=null)app.dispose();}});
        }
        try(Connection reopened=DriverManager.getConnection(args[0],"ledger_test","")){
            check("Paycheck".equals(IncomeScope.load(reopened).category()),"Preference survives new database session");
        }
    }
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
}

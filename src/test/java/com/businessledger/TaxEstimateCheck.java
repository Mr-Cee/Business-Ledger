package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;

public final class TaxEstimateCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void amount(BigDecimal value,String expected,String message){check(value.compareTo(new BigDecimal(expected))==0,message+": "+value);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1||!args[0].contains(":55439/"))throw new IllegalArgumentException("Disposable database required.");
        System.setProperty("ledger.home","target/tax-settings-check");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","")){
            SwingUtilities.invokeAndWait(()->{BusinessLedger app=null;try{
                AppTheme.apply(AppTheme.Style.OCEAN);app=SettingsIntegrationCheck.open(db);
                LocalDate today=LocalDate.now();int year=today.getYear();
                amount(TaxEstimate.load(db,today).amount(),"0","No income estimates zero");
                amount(TaxEstimate.rate(db),"24","Default percentage");
                try(Statement s=db.createStatement()){
                    s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount) VALUES ('"+year+"-01-01','Income','Paycheck',1000.03),('"+year+"-12-31','Income','Other',500),('"+year+"-02-01','Expense','Paycheck',999),('"+(year-1)+"-12-31','Income','Paycheck',999),('"+(year+1)+"-01-01','Income','Paycheck',999)");
                    s.executeUpdate("INSERT INTO transfers(transfer_date,from_account_id,to_account_id,amount) SELECT '"+year+"-02-01',a.id,b.id,500 FROM accounts a,accounts b WHERE a.name='Checking' AND b.name='Savings'");
                }
                TaxEstimate.Estimate estimate=TaxEstimate.load(db,today);amount(estimate.income(),"1500.03","Full current year only");amount(estimate.amount(),"360.01","Default estimate rounded to cents");
                DashboardPanel.Data oldRange=DashboardPanel.load(db,LocalDate.of(year-1,1,1),LocalDate.of(year-1,12,31));
                amount(oldRange.tax().amount(),"360.01","Tax card independent of dashboard dates");
                SettingsPanel settings=new SettingsPanel(db,()->{});
                JTextField rate=(JTextField)SettingsIntegrationCheck.field(settings,"taxRate");check(rate.getText().equals("24"),"Settings default");
                ((JRadioButton)SettingsIntegrationCheck.field(settings,"specific")).doClick();((JComboBox<?>)SettingsIntegrationCheck.field(settings,"category")).setSelectedItem("Paycheck");
                rate.setText("17.25");SettingsIntegrationCheck.save(settings).doClick();
                amount(TaxEstimate.rate(db),"17.25","Custom rate saved");amount(TaxEstimate.load(db,today).amount(),"172.51","Income category respected");
                for(String invalid:new String[]{"-1","100.01","24.123","abc",""}){
                    ((JRadioButton)SettingsIntegrationCheck.field(settings,"all")).doClick();rate.setText(invalid);SettingsIntegrationCheck.save(settings).doClick();
                    amount(TaxEstimate.rate(db),"17.25","Invalid rate did not save");check("Paycheck".equals(IncomeScope.load(db).category()),"Invalid rate did not partially save income scope");
                }
                TaxEstimate.saveRate(db,new BigDecimal("0"));amount(TaxEstimate.load(db,today).amount(),"0","Zero percent allowed");
                TaxEstimate.saveRate(db,new BigDecimal("100"));amount(TaxEstimate.load(db,today).amount(),"1000.03","100 percent allowed");
                TaxEstimate.saveRate(db,new BigDecimal("17.25"));settings.reload();check(rate.getText().equals("17.25"),"Saved rate restored in settings");
                DashboardPanel dashboard=new DashboardPanel(db);dashboard.render(DashboardPanel.load(db,today.withDayOfYear(1),today),today);
                check(((JLabel)SettingsIntegrationCheck.field(dashboard,"estimatedTax")).getText().equals("$172.51"),"Dashboard card amount");
                check(((JLabel)SettingsIntegrationCheck.field(dashboard,"taxCaption")).getText().contains(Integer.toString(year)),"Current year identified");
                for(int width:new int[]{600,1100}){
                    dashboard.setSize(width,850);for(int i=0;i<4;i++)SettingsIntegrationCheck.layout(dashboard);
                    BufferedImage image=new BufferedImage(width,850,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();dashboard.printAll(g);g.dispose();ImageIO.write(image,"png",new File("target/tax-dashboard-"+width+".png"));
                }
                settings.setSize(650,740);for(int i=0;i<4;i++)SettingsIntegrationCheck.layout(settings);
                BufferedImage preview=new BufferedImage(650,740,BufferedImage.TYPE_INT_RGB);Graphics2D g=preview.createGraphics();settings.printAll(g);g.dispose();ImageIO.write(preview,"png",new File("target/tax-settings.png"));
                System.out.println("Current-year taxes, 24% default, income scope, rounding, date independence, validation, and settings UI passed.");
            }catch(Exception ex){throw new RuntimeException(ex);}finally{if(app!=null)app.dispose();}});
        }
        try(Connection reopened=DriverManager.getConnection(args[0],"ledger_test","")){amount(TaxEstimate.rate(reopened),"17.25","Rate survives new database session");}
    }
}

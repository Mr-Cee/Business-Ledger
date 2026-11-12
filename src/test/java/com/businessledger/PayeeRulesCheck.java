package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Creates only named disposable databases on test port 55439. */
public final class PayeeRulesCheck {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static long number(Connection db,String sql)throws SQLException{return BackupIntegrationCheck.number(db,sql);}
    static void sql(Connection db,String sql)throws SQLException{try(Statement s=db.createStatement()){s.executeUpdate(sql);}}
    public static void main(String[] args)throws Exception{
        String base="jdbc:postgresql://127.0.0.1:55439/";
        Files.createDirectories(Path.of("target/payee-check"));System.setProperty("ledger.home",Path.of("target/payee-check").toAbsolutePath().toString());
        try(Connection admin=DriverManager.getConnection(base+"postgres","ledger_test","");Statement s=admin.createStatement()){
            for(String name:new String[]{"payee_source","payee_target","backup_source","backup_target"})s.executeUpdate("CREATE DATABASE "+name+" ENCODING 'UTF8' TEMPLATE template0");
        }
        try(Connection db=DriverManager.getConnection(base+"payee_source","ledger_test","");Connection other=DriverManager.getConnection(base+"payee_target","ledger_test","")){
            BackupIntegrationCheck.initialize(db);BackupIntegrationCheck.initialize(other);
            sql(db,"INSERT INTO transactions(entry_date,kind,category,amount,party,account_id) VALUES ('2026-01-02','Expense','Travel',5,'Acme Shop',1),('2026-01-01','Expense','Supplies',8,' acme  SHOP ',2),('2026-01-03','Income','Services',9,'Acme Shop',2),('2026-01-02','Expense','Software',10,'ACME SHOP',2)");
            var snap=PayeeRules.load(db);var history=snap.find(" ACME \t SHOP ","Expense");
            check(history!=null&&!history.saved()&&history.category().equals("Software")&&history.accountId()==2,"History uses date then ID; ignores case/spacing");
            check(snap.find("Acme","Expense")==null,"No substring matches");
            check(snap.find(" \t ","Expense")==null,"Blank input never suggests a payee");
            check(snap.find("acme shop","Income").category().equals("Services"),"Separate income history");
            PayeeRules.save(db,"Acme Shop","Expense","Supplies",1);
            PayeeRules.save(db," ACME   SHOP ","Expense","Travel",1);
            check(number(db,"SELECT COUNT(*) FROM payee_rules")==1,"Normalized upsert replaces rule");
            check(PayeeRules.load(db).find("acme shop","Expense").saved(),"Saved rule wins over history");
            check(PayeeRules.load(other).find("acme shop","Expense")==null,"Profile isolation");
            String quoted="O'Brien %_ ; DROP TABLE accounts;";PayeeRules.save(db,quoted,"Expense","Supplies",1);
            check(PayeeRules.load(db).find(quoted,"Expense")!=null&&number(db,"SELECT COUNT(*) FROM accounts")==3,"Bound SQL preserves punctuation safely");
            boolean rejected=false;try{PayeeRules.save(db,"   ","Expense","Travel",1);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Blank payee rejected");
            sql(db,"UPDATE accounts SET active=FALSE WHERE id=1");
            check(!PayeeRules.load(db).find("acme shop","Expense").available(),"Archived rule remains visible and blocks fallback");
            rejected=false;try{PayeeRules.save(db,"Other","Expense","Travel",1);}catch(SQLException expected){rejected=true;}check(rejected,"Archived account cannot be saved");
            sql(db,"UPDATE accounts SET active=TRUE WHERE id=1");sql(db,"UPDATE categories SET active=FALSE WHERE name='Travel'");
            check(!PayeeRules.load(db).find("acme shop","Expense").available(),"Archived category disables apply");
            sql(db,"UPDATE categories SET active=TRUE WHERE name='Travel'");
            // Same transactional rename sequence used by Manage categories.
            db.setAutoCommit(false);sql(db,"INSERT INTO categories(name) VALUES ('Business travel')");sql(db,"UPDATE transactions SET category='Business travel' WHERE category='Travel'");sql(db,"UPDATE payee_rules SET category='Business travel' WHERE category='Travel'");sql(db,"DELETE FROM categories WHERE name='Travel'");db.commit();db.setAutoCommit(true);
            check(PayeeRules.load(db).find("acme shop","Expense").category().equals("Business travel"),"Rules survive category rename");
            SwingUtilities.invokeAndWait(()->{try{
                JTextField payee=new JTextField();JComboBox<String> kind=new JComboBox<>(new String[]{"Expense","Income"}),category=new JComboBox<>(new String[]{"Supplies","Business travel","Software","Services"});
                var accounts=new JComboBox<BusinessLedger.Account>(new BusinessLedger.Account[]{new BusinessLedger.Account(1,"Checking","Checking",true),new BusinessLedger.Account(2,"Savings","Savings",true)});
                PayeeSuggestions panel=new PayeeSuggestions(db,payee,kind,category,accounts);panel.refresh();payee.setText("acme shop");
                check(category.getSelectedItem().equals("Supplies")&&accounts.getSelectedIndex()==0,"Typing never changes choices");
                SettingsIntegrationCheck.call(panel,"apply");check(category.getSelectedItem().equals("Business travel")&&accounts.getSelectedIndex()==0,"Explicit apply uses saved rule");
                kind.setSelectedItem("Income");check(category.getSelectedItem().equals("Business travel"),"Changing kind only changes suggestion");SettingsIntegrationCheck.call(panel,"apply");check(category.getSelectedItem().equals("Services")&&accounts.getSelectedIndex()==1,"Income apply uses income history");
                payee.setText("acme");check(!((JButton)SettingsIntegrationCheck.field(panel,"apply")).isEnabled(),"Partial payee cannot apply stale result");
                for(AppTheme.Style style:new AppTheme.Style[]{AppTheme.Style.OCEAN,AppTheme.Style.MIDNIGHT}){AppTheme.apply(style);SwingUtilities.updateComponentTreeUI(panel);payee.setText("acme shop");panel.setSize(650,150);layout(panel);BufferedImage image=new BufferedImage(650,150,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();ImageIO.write(image,"png",Path.of("target/payee-check/"+style.name()+".png").toFile());}
            }catch(Exception ex){throw new RuntimeException(ex);}});
            SwingUtilities.invokeAndWait(()->{BusinessLedger app=null;try{
                app=SettingsIntegrationCheck.open(db);app.setSize(1100,700);app.addNotify();app.validate();JTabbedPane tabs=(JTabbedPane)app.getContentPane().getComponent(0);tabs.setSelectedIndex(1);layout(app);layout(app);
                BufferedImage image=new BufferedImage(1100,700,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();app.getRootPane().printAll(graphics);graphics.dispose();ImageIO.write(image,"png",Path.of("target/payee-check/full-form.png").toFile());
            }catch(Exception ex){throw new RuntimeException(ex);}finally{if(app!=null)app.dispose();}});
            check(number(db,"SELECT COUNT(*) FROM transactions")==4,"Suggestions/rule saves do not create transactions");
            Path backup=Path.of("target/payee-check/new.ledgerbackup");new LedgerBackup(db).create(backup,"Payee test");new LedgerBackup(other).restore(backup);
            check(number(other,"SELECT COUNT(*) FROM payee_rules")==2&&PayeeRules.load(other).find("acme shop","Expense").category().equals("Business travel"),"Rules survive backup round trip");
            // A real version-1 layout contains all original data files but no rule file.
            Path old=Path.of("target/payee-check/v1.ledgerbackup");rewrite(backup,old,true,false);LedgerBackup.inspect(old);new LedgerBackup(other).restore(old);
            check(number(other,"SELECT COUNT(*) FROM payee_rules")==0&&number(other,"SELECT COUNT(*) FROM transactions")==4,"Old backups restore with empty saved rules and preserved history");
            Path missing=Path.of("target/payee-check/missing.ledgerbackup");rewrite(backup,missing,false,false);rejected=false;try{new LedgerBackup(other).restore(missing);}catch(Exception expected){rejected=true;}check(rejected&&number(other,"SELECT COUNT(*) FROM transactions")==4,"New backup missing rules rejected before mutation");
            Path damaged=Path.of("target/payee-check/damaged.ledgerbackup");rewrite(backup,damaged,false,true);rejected=false;try{new LedgerBackup(other).restore(damaged);}catch(Exception expected){rejected=true;}check(rejected&&number(other,"SELECT COUNT(*) FROM payee_rules")==0,"Corrupt rule payload rolls back restore");
            PayeeRules.remove(db,PayeeRules.load(db).find("acme shop","Expense"));check(!PayeeRules.load(db).find("acme shop","Expense").saved(),"Removing rule restores history fallback");
        }
        BackupIntegrationCheck.main(new String[]{base+"backup_source",base+"backup_target"});
        System.out.println("Payee exact matching, history precedence, type/profile isolation, active choices, rename, explicit UI apply, backup v1/v2 and rollback checks passed.");
        System.exit(0);
    }
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container next)layout(next);}
    static void rewrite(Path input,Path output,boolean v1,boolean corrupt)throws Exception{
        try(ZipFile zip=new ZipFile(input.toFile());ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(output))){for(ZipEntry entry:Collections.list(zip.entries())){
            if(entry.getName().equals("payee_rules.data")&&!corrupt)continue;
            byte[] data;try(InputStream in=zip.getInputStream(entry)){data=in.readAllBytes();}
            if(v1&&entry.getName().equals("manifest.properties")){Properties p=new Properties();p.load(new ByteArrayInputStream(data));p.setProperty("version","1");p.remove("payee_rules.sha256");ByteArrayOutputStream bytes=new ByteArrayOutputStream();p.store(bytes,"");data=bytes.toByteArray();}
            if(corrupt&&entry.getName().equals("payee_rules.data"))data[data.length-1]^=1;
            out.putNextEntry(new ZipEntry(entry.getName()));out.write(data);out.closeEntry();
        }}
    }
}

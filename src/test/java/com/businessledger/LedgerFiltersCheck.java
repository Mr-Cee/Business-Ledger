package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class LedgerFiltersCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static JTextField text(LedgerFilters filters,String name)throws Exception{return (JTextField)SettingsIntegrationCheck.field(filters,name);}
    static JComboBox<?> combo(LedgerFilters filters,String name)throws Exception{return (JComboBox<?>)SettingsIntegrationCheck.field(filters,name);}
    static void select(JComboBox<?> box,String value){for(int i=0;i<box.getItemCount();i++)if(box.getItemAt(i).toString().equals(value)){box.setSelectedIndex(i);return;}throw new AssertionError("Missing option "+value);}
    static Object[] row(long id,String day,String type,String category,String amount,String party,String notes,String account,String balance,String edited){return new Object[]{id,LocalDate.parse(day),type,category,new BigDecimal(amount),party,notes,account,new BigDecimal(balance),edited};}
    static Object[][] rows(){return new Object[][]{
        row(1,"2026-01-01","Income","Paycheck","1000","Client","Contract","Checking","1000",""),
        row(2,"2026-01-02","Expense","Food","30","Café","Team lunch","Checking","970",""),
        row(3,"2026-01-03","Expense","Supplies","10","A+B [shop]","Paper (blue)","Savings","40",""),
        row(4,"2026-01-04","Expense","Food","50","Market","Groceries","Checking","920",""),
        row(5,"2026-01-05","Transfer Out","Transfer","100","To: Savings","Emergency","Checking","820",""),
        row(5,"2026-01-05","Transfer In","Transfer","100","From: Checking","Emergency","Savings","140",""),
        row(6,"2026-02-01","Expense","Food","30","Café","Lunch","Savings","110","Edited")};}
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{try{
            AppTheme.apply(AppTheme.Style.OCEAN);
            DefaultTableModel model=new DefaultTableModel(rows(),new Object[]{"ID","Date","Type","Category","Amount","Payee / customer","Notes","Account","Running balance ($)","Edited"}){
                public Class<?> getColumnClass(int col){return col==0?Long.class:col==4||col==8?BigDecimal.class:String.class;}
            };
            BusinessLedger.LedgerTable table=new BusinessLedger.LedgerTable(model);table.colorRows=true;table.setAutoCreateRowSorter(true);LedgerFilters filters=new LedgerFilters(table);
            check(table.getRowCount()==7,"All rows initially");
            text(filters,"search").setText("cAfÉ LUNCH");filters.applyNow();check(table.getRowCount()==2,"Case-insensitive all-word search");
            text(filters,"search").setText("food checking");filters.applyNow();check(table.getRowCount()==2,"Words across category and account");
            text(filters,"search").setText("[shop]");filters.applyNow();check(table.getRowCount()==1,"Literal brackets");table.setRowSelectionInterval(0,0);
            Method selected=BusinessLedger.class.getDeclaredMethod("selectedTransactionId",JTable.class);selected.setAccessible(true);
            check((long)selected.invoke(null,table)==3,"Edit/delete resolve filtered row to correct ID");
            check(table.getValueAt(0,8).equals(new BigDecimal("40"))&&model.getValueAt(2,8).equals(new BigDecimal("40")),"Running balance unchanged by search");
            check(table.prepareRenderer(table.getCellRenderer(0,0),0,0)!=null,"Renderer supports filtered rows");
            text(filters,"search").setText("(");filters.applyNow();check(table.getRowCount()==1,"Literal regex punctuation does not error");
            filters.clear();select(combo(filters,"type"),"Transfers");filters.applyNow();check(table.getRowCount()==2,"Both transfer directions");
            select(combo(filters,"type"),"Transfer In");filters.applyNow();check(table.getRowCount()==1,"Specific transfer direction");table.setRowSelectionInterval(0,0);
            try{selected.invoke(null,table);throw new AssertionError("Transfer allowed through transaction editor");}catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException,"Transfer edit guard preserved");}
            filters.clear();text(filters,"from").setText("2026-01-05");text(filters,"through").setText("2026-01-05");filters.applyNow();check(table.getRowCount()==2,"Inclusive date boundaries");
            text(filters,"minimum").setText("100");text(filters,"maximum").setText("100.00");filters.applyNow();check(table.getRowCount()==2,"Inclusive amount boundaries");
            select(combo(filters,"account"),"Savings");filters.applyNow();check(table.getRowCount()==1,"Combined account, date, amount filters");
            text(filters,"minimum").setText("100.001");filters.applyNow();check(table.getRowCount()==0&&((JTextArea)SettingsIntegrationCheck.field(filters,"status")).getText().contains("attention"),"Invalid cash value clears stale results");
            filters.clear();text(filters,"from").setText("2026-02-30");filters.applyNow();check(table.getRowCount()==0,"Invalid date rejected");
            text(filters,"from").setText("2026-02-01");text(filters,"through").setText("2026-01-01");filters.applyNow();check(table.getRowCount()==0,"Reversed dates rejected");
            filters.clear();text(filters,"minimum").setText("50");text(filters,"maximum").setText("10");filters.applyNow();check(table.getRowCount()==0,"Reversed amount range rejected");
            filters.clear();select(combo(filters,"category"),"Food");select(combo(filters,"account"),"Checking");filters.applyNow();check(table.getRowCount()==2,"Category and account match");
            model.setRowCount(0);for(Object[] row:rows())model.addRow(row);filters.applyNow();check(table.getRowCount()==2,"Selections survive refreshed model");
            model.addRow(row(7,"2026-03-01","Expense","New category","1","New shop","New item","New account","9",""));filters.applyNow();select(combo(filters,"category"),"New category");select(combo(filters,"account"),"New account");filters.applyNow();check(table.getRowCount()==1,"New category/account choices appear after refresh");
            filters.clear();check(table.getRowCount()==8,"Clear restores every row");
            text(filters,"search").setText("edited");filters.applyNow();check(table.getRowCount()==1,"Edited marker searchable");
            filters.clear();JPanel page=new JPanel(new BorderLayout());page.add(filters,BorderLayout.NORTH);page.add(new JScrollPane(table));filters.installShortcut(page);
            check(page.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("control F"))!=null,"Ctrl+F shortcut installed");
            for(AppTheme.Style theme:new AppTheme.Style[]{AppTheme.Style.OCEAN,AppTheme.Style.MIDNIGHT}){
                AppTheme.apply(theme);SwingUtilities.updateComponentTreeUI(page);
                for(int width:new int[]{600,1100})for(boolean expanded:new boolean[]{false,true}){
                    JToggleButton more=(JToggleButton)SettingsIntegrationCheck.field(filters,"more");if(more.isSelected()!=expanded)more.doClick();
                    page.setSize(width,850);for(int i=0;i<4;i++)SettingsIntegrationCheck.layout(page);
                    BufferedImage image=new BufferedImage(width,850,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();page.printAll(g);g.dispose();ImageIO.write(image,"png",new File("target/ledger-filters-"+theme.name().toLowerCase()+"-"+width+"-"+(expanded?"expanded":"collapsed")+".png"));
                }
            }
            filters.clear();System.out.println("Ledger literal/multiword search, combined filters, inclusive ranges, validation, refresh, ID mapping, balance preservation, shortcuts, and responsive theme rendering passed.");
        }catch(Exception ex){throw new RuntimeException(ex);}});
    }
}

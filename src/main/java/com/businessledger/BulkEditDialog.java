package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.List;

final class BulkEditDialog {
    private record Account(long id,String name){@Override public String toString(){return name;}}
    static DefaultTableModel previewRows(List<BulkTransactionEdit.Row> rows,BulkTransactionEdit.Change change,String accountName){
        DefaultTableModel model=new DefaultTableModel(new String[]{"ID","Date","Type","Amount ($)","Payee / customer","Current category","New category","Current account","New account","Change"},0){
            @Override public boolean isCellEditable(int row,int col){return false;}
            @Override public Class<?> getColumnClass(int col){return col==0?Long.class:col==3?java.math.BigDecimal.class:Object.class;}
        };
        for(var row:rows)model.addRow(new Object[]{row.id(),row.date(),row.type(),row.amount(),row.payee(),row.category(),change.category(row),row.account(),change.accountId()==null?row.account():accountName,change.changes(row)?"Update":"Unchanged"});
        return model;
    }
    static boolean show(JFrame owner,Connection db,List<Long> ids,String user)throws SQLException{
        List<BulkTransactionEdit.Row> rows=BulkTransactionEdit.load(db,ids);
        JCheckBox categoryEnabled=new JCheckBox("Change category"),accountEnabled=new JCheckBox("Change account");
        JComboBox<String> category=new JComboBox<>();JComboBox<Account> account=new JComboBox<>();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT name FROM categories WHERE active ORDER BY name")){while(r.next())category.addItem(r.getString(1));}
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id,name FROM accounts WHERE active ORDER BY name")){while(r.next())account.addItem(new Account(r.getLong(1),r.getString(2)));}
        category.setEnabled(false);account.setEnabled(false);
        categoryEnabled.addActionListener(e->category.setEnabled(categoryEnabled.isSelected()));accountEnabled.addActionListener(e->account.setEnabled(accountEnabled.isSelected()));
        JTextArea reason=new JTextArea(3,28);reason.setLineWrap(true);reason.setWrapStyleWord(true);
        JPanel fields=new JPanel(new GridLayout(0,2,8,8));fields.add(categoryEnabled);fields.add(category);fields.add(accountEnabled);fields.add(account);fields.add(new JLabel("Reason (required)"));fields.add(new JScrollPane(reason));
        JTextArea help=text(rows.size()+" selected transactions. Check the fields to change. Unchecked fields keep their individual values. Date, type, amount, payee and notes are preserved. Each changed row receives an edit-history entry.",3);
        JPanel form=new JPanel(new BorderLayout(8,8));form.add(help,BorderLayout.NORTH);form.add(fields);
        while(JOptionPane.showOptionDialog(owner,form,"Bulk edit transactions",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Cancel","Preview changes"},"Cancel")==1){
            Account target=(Account)account.getSelectedItem();
            var change=new BulkTransactionEdit.Change(categoryEnabled.isSelected()?(String)category.getSelectedItem():null,accountEnabled.isSelected()&&target!=null?target.id():null);
            if((categoryEnabled.isSelected()&&category.getSelectedItem()==null)||(accountEnabled.isSelected()&&target==null)){
                JOptionPane.showMessageDialog(owner,"Add an active category/account first.");continue;
            }
            if((change.category()==null&&change.accountId()==null)||reason.getText().isBlank()){
                JOptionPane.showMessageDialog(owner,"Choose a category or account to change and enter a reason.");continue;
            }
            long changed=rows.stream().filter(change::changes).count();
            if(changed==0){JOptionPane.showMessageDialog(owner,"No values would change. Choose different values or cancel.");continue;}
            JTable table=new JTable(previewRows(rows,change,target==null?null:target.name()));table.setAutoCreateRowSorter(true);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==0?65:145);
            Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(Math.min(1100,screen.width-120),Math.min(400,screen.height-230)));
            JPanel preview=new JPanel(new BorderLayout(8,8));preview.add(text(changed+" transactions will change; "+(rows.size()-changed)+" already have these values. Scroll sideways for all before/after columns.\nReason: "+reason.getText().strip(),3),BorderLayout.NORTH);preview.add(scroll);
            if(JOptionPane.showOptionDialog(owner,preview,"Review bulk changes",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,new String[]{"Back","Save changes"},"Back")!=1)continue;
            BulkTransactionEdit.Result result=BulkTransactionEdit.save(db,rows,change,user,reason.getText());
            JOptionPane.showMessageDialog(owner,result.updated()+" transactions updated; "+result.unchanged()+" unchanged. Edit history saved for each changed transaction.");return true;
        }
        return false;
    }
    private static JTextArea text(String value,int rows){JTextArea area=new JTextArea(value,rows,0);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);return area;}
}

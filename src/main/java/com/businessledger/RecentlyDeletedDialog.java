package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.*;
import java.util.List;

final class RecentlyDeletedDialog {
    static long selectedRecoveryId(JTable table){
        if(table.getSelectedRowCount()!=1)throw new IllegalArgumentException("Select one deleted entry to restore.");
        return ((Number)table.getModel().getValueAt(table.convertRowIndexToModel(table.getSelectedRow()),0)).longValue();
    }
    static boolean show(JFrame owner,Connection db,String user)throws SQLException{
        JDialog dialog=new JDialog(owner,"Recently deleted",true);boolean[] changed={false};
        DefaultTableModel model=new DefaultTableModel(new String[]{"Recovery ID","Deleted at","Deleted by","Record type","Original ID","Entry date","Amount / miles","Payee / purpose","Category","Account / from","To account","Notes / route"},0){@Override public boolean isCellEditable(int row,int col){return false;}@Override public Class<?> getColumnClass(int col){return col==0||col==4?Long.class:Object.class;}};
        JTable table=new JTable(model);table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==0||i==4?85:i==1?190:150);
        Runnable reload=()->{try{model.setRowCount(0);for(var entry:DeletedRecords.list(db))model.addRow(new Object[]{entry.id(),entry.deletedAt(),entry.deletedBy(),entry.type(),entry.recordId(),entry.date(),entry.value(),entry.payee(),entry.category(),entry.kind()==DeletedRecords.Kind.MILEAGE?"":entry.account(),entry.second(),entry.notes()});}catch(SQLException ex){throw new RuntimeException(ex);}};
        try{reload.run();}catch(RuntimeException ex){dialog.dispose();if(ex.getCause() instanceof SQLException cause)throw cause;throw ex;}
        JTextArea help=new JTextArea("Restore entries deleted after version 1.9.0 was installed. Restoring changes balances and reports and reconnects existing edit history. Transfers restore both account sides together. Archived accounts/categories stay archived.\nEntries persist across restarts and are included in new backups. Older deletions cannot be recovered here. Scroll sideways for details.",3,0);help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);
        JButton restore=new JButton("Restore selected"),refresh=new JButton("Refresh"),close=new JButton("Close");restore.setEnabled(false);table.getSelectionModel().addListSelectionListener(e->restore.setEnabled(table.getSelectedRowCount()==1));
        restore.addActionListener(e->{try{
            long id=selectedRecoveryId(table);int row=table.convertRowIndexToModel(table.getSelectedRow());
            DeletedRecords.Matches matches=DeletedRecords.matches(db,id);Set<Long> approved=new HashSet<>();
            if(!matches.rows().isEmpty()&&!DuplicateDetection.confirm(dialog,matches.key(),matches.rows()))return;
            for(var match:matches.rows())approved.add(match.id());
            String detail="Restore "+model.getValueAt(row,3)+" #"+model.getValueAt(row,4)+" dated "+model.getValueAt(row,5)+"?\nAmount / miles: "+model.getValueAt(row,6)+"\nPayee / purpose: "+model.getValueAt(row,7)+"\nAccount / from: "+model.getValueAt(row,9)+(model.getValueAt(row,10).toString().isEmpty()?"":"\nTo: "+model.getValueAt(row,10))+"\nBalances and reports will include this entry again.";
            if(JOptionPane.showOptionDialog(dialog,detail,"Restore deleted entry",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,new String[]{"Cancel","Restore"},"Cancel")!=1)return;
            DeletedRecords.restore(db,id,user,approved);changed[0]=true;reload.run();JOptionPane.showMessageDialog(dialog,"Entry restored with its original ID.");
        }catch(Exception ex){JOptionPane.showMessageDialog(dialog,ex.getMessage(),"Unable to restore",JOptionPane.ERROR_MESSAGE);}});
        refresh.addActionListener(e->{try{reload.run();}catch(RuntimeException ex){JOptionPane.showMessageDialog(dialog,ex.getMessage());}});close.addActionListener(e->dialog.dispose());
        WrapPanel buttons=new WrapPanel();buttons.add(restore);buttons.add(refresh);buttons.add(close);JPanel content=new JPanel(new BorderLayout(8,8));content.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));content.add(help,BorderLayout.NORTH);content.add(new JScrollPane(table));content.add(buttons,BorderLayout.SOUTH);dialog.setContentPane(content);HelpGuide.bind(dialog.getRootPane(),()->"Recently deleted");dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();dialog.setSize(Math.min(1150,screen.width-60),Math.min(650,screen.height-90));dialog.setLocationRelativeTo(owner);dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);dialog.setVisible(true);return changed[0];
    }
}

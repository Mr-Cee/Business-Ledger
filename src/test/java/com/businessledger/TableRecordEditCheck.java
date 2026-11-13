package com.businessledger;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.event.*;
import java.util.*;

public final class TableRecordEditCheck {
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JTable table=new JTable(new DefaultTableModel(new Object[][]{{10,"Zulu"},{20,"Alpha"},{30,"Beta"}},new Object[]{"ID","Name"}));
            table.setAutoCreateRowSorter(true);table.setSize(300,120);
            TableRowSorter<?> sorter=(TableRowSorter<?>)table.getRowSorter();
            sorter.setSortKeys(List.of(new RowSorter.SortKey(1,SortOrder.ASCENDING)));
            sorter.setRowFilter(RowFilter.regexFilter("Alpha|Beta",1));
            List<Integer> edited=new ArrayList<>();JButton edit=new JButton();
            edit.addActionListener(e->{if(table.getSelectedRowCount()!=1)throw new AssertionError("Single clicked row required");edited.add((Integer)table.getValueAt(table.getSelectedRow(),0));});
            TableRecordEdit.install(table,edit);
            var listener=table.getMouseListeners()[table.getMouseListeners().length-1];
            table.setRowSelectionInterval(0,1);
            listener.mouseClicked(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,0,0,10,table.getRowHeight()+2,2,false,MouseEvent.BUTTON1));
            if(!edited.equals(List.of(30)))throw new AssertionError("Sorted/filtered clicked ID");
            for(int mods:new int[]{InputEvent.CTRL_DOWN_MASK,InputEvent.SHIFT_DOWN_MASK})listener.mouseClicked(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,0,mods,10,2,2,false,MouseEvent.BUTTON1));
            listener.mouseClicked(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,0,0,10,100,2,false,MouseEvent.BUTTON1));
            listener.mouseClicked(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,0,0,10,2,1,false,MouseEvent.BUTTON1));
            listener.mouseClicked(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,0,0,10,2,2,false,MouseEvent.BUTTON3));
            if(edited.size()!=1)throw new AssertionError("Selection gestures and blank space must not edit");
        });
        System.out.println("Sorted/filtered clicked record, single selection and ignored selection/blank/right-click gestures passed.");
    }
}

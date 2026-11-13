package com.businessledger;

import javax.swing.*;
import java.awt.event.*;

/** Opens the existing editor for the clicked view row. */
final class TableRecordEdit {
    static void install(JTable table,JButton edit) {
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if(!SwingUtilities.isLeftMouseButton(event)||event.getClickCount()!=2
                        ||event.isControlDown()||event.isShiftDown()||event.isAltDown()||event.isMetaDown())return;
                int row=table.rowAtPoint(event.getPoint());
                if(row<0||table.columnAtPoint(event.getPoint())<0||!edit.isEnabled())return;
                table.setRowSelectionInterval(row,row);
                edit.doClick(0);
            }
        });
    }
}

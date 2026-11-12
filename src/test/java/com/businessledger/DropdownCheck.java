package com.businessledger;

import javax.swing.*;
import java.awt.*;

public final class DropdownCheck {
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{try{
            for(AppTheme.Style theme:AppTheme.Style.values()){
                AppTheme.apply(theme);
                for(int fontSize:new int[]{14,22,28}){
                    JComboBox<String> box=new JComboBox<>(new String[]{"Income","Expense","Checking account","Expenses"});
                    box.setFont(new Font("Segoe UI",Font.PLAIN,fontSize));
                    box.setSize(box.getPreferredSize());box.doLayout();
                    ThemeComboBoxUI ui=(ThemeComboBoxUI)box.getUI();
                    int needed=box.getFontMetrics(box.getFont()).stringWidth("Checking account")+10;
                    if(ui.rectangleForCurrentValue().width<needed)throw new AssertionError("Selected text clipped at "+fontSize);
                    box.addItem("A longer savings account name");box.setSize(box.getPreferredSize());box.doLayout();
                    needed=box.getFontMetrics(box.getFont()).stringWidth("A longer savings account name")+10;
                    if(ui.rectangleForCurrentValue().width<needed)throw new AssertionError("Added item clipped");
                    if(box.getMinimumSize().width<needed+29)throw new AssertionError("Minimum width too small");
                }
            }
            System.out.println("Dropdown widths passed for all themes, 14/22/28 point fonts, and newly added names.");
        }catch(Exception ex){throw new RuntimeException(ex);}});
    }
}

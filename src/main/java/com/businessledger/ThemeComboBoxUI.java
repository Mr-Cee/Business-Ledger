package com.businessledger;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;

/** Keeps collapsed dropdowns legible in both light and dark palettes. */
public final class ThemeComboBoxUI extends BasicComboBoxUI {
    public static ComponentUI createUI(JComponent component){return new ThemeComboBoxUI();}
    @Override public Dimension getPreferredSize(JComponent component){
        Dimension size=getDisplaySize();
        FontMetrics fm=component.getFontMetrics(component.getFont());int textWidth=0;
        for(int i=0;i<comboBox.getItemCount();i++){
            Object item=comboBox.getItemAt(i);if(item!=null)textWidth=Math.max(textWidth,fm.stringWidth(item.toString()));
        }
        Object selected=comboBox.getSelectedItem();if(selected!=null)textWidth=Math.max(textWidth,fm.stringWidth(selected.toString()));
        Insets insets=component.getInsets();
        return new Dimension(Math.max(size.width+32,textWidth+44+insets.left+insets.right),Math.max(size.height+8,fm.getHeight()+12));
    }
    @Override public Dimension getMinimumSize(JComponent component){return getPreferredSize(component);}
    @Override protected LayoutManager createLayoutManager(){return new LayoutManager(){
        public void addLayoutComponent(String name,Component component){}
        public void removeLayoutComponent(Component component){}
        public Dimension preferredLayoutSize(Container parent){return parent.getPreferredSize();}
        public Dimension minimumLayoutSize(Container parent){return parent.getMinimumSize();}
        public void layoutContainer(Container parent){
            int width=parent.getWidth(),height=parent.getHeight();
            if(arrowButton!=null)arrowButton.setBounds(Math.max(0,width-25),1,24,Math.max(0,height-2));
            if(editor!=null)editor.setBounds(3,2,Math.max(0,width-30),Math.max(0,height-4));
        }
    };}
    @Override protected Rectangle rectangleForCurrentValue(){return new Rectangle(2,1,Math.max(0,comboBox.getWidth()-29),Math.max(0,comboBox.getHeight()-2));}
    @Override protected JButton createArrowButton(){
        JButton button=new BasicArrowButton(SwingConstants.SOUTH,AppTheme.surface(),AppTheme.grid(),AppTheme.text(),AppTheme.surface()){
            @Override public Dimension getPreferredSize(){return new Dimension(24,24);}
        };button.setFocusable(false);return button;
    }
    @Override public void paintCurrentValue(Graphics g,Rectangle bounds,boolean focused){
        Object value=comboBox.getSelectedItem();String label=value==null?"":value.toString();
        g.setFont(comboBox.getFont());g.setColor(comboBox.isEnabled()?AppTheme.text():AppTheme.muted());
        FontMetrics fm=g.getFontMetrics();int width=Math.max(0,bounds.width-10);
        if(fm.stringWidth(label)>width){while(!label.isEmpty() && fm.stringWidth(label+"...")>width)label=label.substring(0,label.length()-1);label+="...";}
        g.drawString(label,bounds.x+5,bounds.y+(bounds.height-fm.getHeight())/2+fm.getAscent());
    }
}

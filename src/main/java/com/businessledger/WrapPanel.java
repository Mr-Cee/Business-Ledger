package com.businessledger;

import javax.swing.*;
import java.awt.*;

/** FlowLayout with a width-aware preferred height so wrapped controls remain visible. */
final class WrapPanel extends JPanel {
    WrapPanel() {
        super(new FlowLayout(FlowLayout.LEFT,8,6));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            private int lastWidth=-1;
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if(getWidth()!=lastWidth){lastWidth=getWidth();if(getParent() instanceof JComponent p)p.revalidate();}
            }
        });
    }
    void addField(String label,JComponent input) {
        JPanel pair=new JPanel(new BorderLayout(5,0)); pair.setOpaque(false);
        JLabel caption=new JLabel(label) {
            @Override public Dimension getPreferredSize() {
                Dimension size=super.getPreferredSize();
                // Nimbus can paint text slightly wider than its preferred measurement,
                // especially with Windows display scaling. Keep slack inside the label
                // rather than a border, which would remove that space when painting.
                size.width+=8;
                return size;
            }
        };
        caption.setLabelFor(input);
        pair.add(caption,BorderLayout.WEST);pair.add(input);add(pair);
    }
    @Override public Dimension getPreferredSize() {
        synchronized(getTreeLock()) {
            int available=0;
            for(Container p=getParent();p!=null;p=p.getParent())if(p.getWidth()>0){available=p.getWidth();break;}
            if(available==0)available=getWidth()>0?getWidth():1000;
            Insets insets=getInsets();FlowLayout layout=(FlowLayout)getLayout();
            int gap=layout.getHgap(),vgap=layout.getVgap(),limit=Math.max(1,available-insets.left-insets.right-2*gap);
            int x=0,rowHeight=0,totalHeight=vgap*2,maxWidth=0;
            for(Component c:getComponents())if(c.isVisible()) {
                Dimension size=c.getPreferredSize();
                if(x>0 && x+gap+size.width>limit){maxWidth=Math.max(maxWidth,x);totalHeight+=rowHeight+vgap;x=0;rowHeight=0;}
                x+=(x>0?gap:0)+size.width;rowHeight=Math.max(rowHeight,size.height);
            }
            return new Dimension(Math.max(maxWidth,x)+insets.left+insets.right+2*gap,totalHeight+rowHeight+insets.top+insets.bottom);
        }
    }
    @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
}

package com.businessledger;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;

public final class ResponsiveLayoutCheck {
    static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container child)layout(child);}
    static void controlsInside(Container root,Container panel){
        for(Component c:root.getComponents()){
            if(!c.isVisible())continue;
            if(c instanceof JComboBox<?> || (c instanceof JButton b && b.getText()!=null && !b.getText().isBlank())){
                Rectangle bounds=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),panel);
                if(!new Rectangle(0,0,panel.getWidth(),panel.getHeight()).contains(bounds))throw new AssertionError("Control outside panel: "+c+" "+bounds);
                Container parent=c.getParent();if(!new Rectangle(0,0,parent.getWidth(),parent.getHeight()).contains(c.getBounds()))throw new AssertionError("Control clipped by parent: "+c);
            }
            if(c instanceof Container child)controlsInside(child,panel);
        }
    }
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                java.util.List<Object> fontKeys=new ArrayList<>();UIManager.getDefaults().forEach((key,value)->{if(value instanceof Font)fontKeys.add(key);});
                for(Object key:fontKeys)UIManager.put(key,new FontUIResource("Dialog",Font.PLAIN,20));
                for(String key:new String[]{"Label.font","Button.font","TextField.font","ComboBox.font","Table.font","Panel.font","TextArea.font"})UIManager.put(key,new FontUIResource("Dialog",Font.PLAIN,20));
                ChartsPanel panel=new ChartsPanel(null);
                Field range=ChartsPanel.class.getDeclaredField("range");range.setAccessible(true);((JComboBox<?>)range.get(panel)).setSelectedIndex(7);
                Field data=ChartsPanel.class.getDeclaredField("data");data.setAccessible(true);
                data.set(panel,new ChartsPanel.Data(new BigDecimal("37249.51"),new BigDecimal("45293.77"),Map.of(),Map.of()));
                java.lang.reflect.Method render=ChartsPanel.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(panel);
                Field field=ChartsPanel.class.getDeclaredField("chart");field.setAccessible(true);ChartsPanel.BarChart chart=(ChartsPanel.BarChart)field.get(panel);
                chart.setBars(java.util.List.of(new ChartsPanel.Bar("Income",new BigDecimal("37249.51"),new Color(45,137,92)),new ChartsPanel.Bar("Expenses",new BigDecimal("45293.77"),new Color(193,75,75)),new ChartsPanel.Bar("Net",new BigDecimal("-8044.26"),new Color(55,104,168))));
                for(int width:new int[]{1200,900,600}){
                    panel.setSize(width,720);for(int pass=0;pass<5;pass++)layout(panel);
                    controlsInside(panel,panel);
                    JViewport viewport=(JViewport)chart.getParent();
                    if(chart.getWidth()!=viewport.getWidth())throw new AssertionError("Chart does not fit viewport at width "+width);
                    BufferedImage image=new BufferedImage(width,720,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();ImageIO.write(image,"png",Path.of("target/responsive-"+width+".png").toFile());
                }
                System.out.println("Responsive controls and chart widths passed at 600, 900, and 1200 pixels with large fonts.");
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
}

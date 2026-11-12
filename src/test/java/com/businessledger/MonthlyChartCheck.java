package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.*;
import javax.imageio.ImageIO;

public final class MonthlyChartCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                Map<YearMonth,BigDecimal[]> months=new TreeMap<>();BigDecimal income=BigDecimal.ZERO,expenses=BigDecimal.ZERO;
                for(int month=1;month<=12;month++){BigDecimal in=new BigDecimal(month==2?"15541.89":"7251.54").add(BigDecimal.valueOf(month*50)),out=new BigDecimal(month==2?"4249.77":"11889.07").subtract(BigDecimal.valueOf(month*100));months.put(YearMonth.of(2026,month),new BigDecimal[]{in,out});income=income.add(in);expenses=expenses.add(out);}
                ChartsPanel panel=new ChartsPanel(null);((JComboBox<?>)field(panel,"view")).setSelectedIndex(2);
                Field data=ChartsPanel.class.getDeclaredField("data");data.setAccessible(true);data.set(panel,new ChartsPanel.Data(income,expenses,Map.of(),months));Method render=ChartsPanel.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(panel);
                DefaultTableModel table=(DefaultTableModel)field(panel,"table");check(table.getRowCount()==12 && table.getColumnCount()==4,"One table row per month with Income/Expenses/Net");
                check(table.getValueAt(0,3).equals(months.get(YearMonth.of(2026,1))[0].subtract(months.get(YearMonth.of(2026,1))[1])),"Exact monthly net in table");
                MonthlyChart chart=(MonthlyChart)field(panel,"monthlyChart");check(((JScrollPane)field(panel,"chartScroll")).getViewport().getView()==chart,"Monthly chart uses grouped renderer");
                chart.setFont(new Font("Dialog",Font.PLAIN,14));JViewport viewport=new JViewport();viewport.setView(chart);
                for(int width:new int[]{600,1200}){
                    viewport.setSize(width,320);for(int pass=0;pass<4;pass++)viewport.doLayout();check(chart.getWidth()==width,"Grouped chart tracks width");
                    Dimension size=chart.getPreferredSize();chart.setSize(width,size.height);BufferedImage image=new BufferedImage(width,size.height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();chart.paint(g);g.dispose();ImageIO.write(image,"png",Path.of("target/monthly-grouped-"+width+".png").toFile());
                }
                chart.setMonths(Map.of(YearMonth.of(2024,2),new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO}));chart.setSize(600,300);BufferedImage image=new BufferedImage(600,300,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();chart.paint(g);g.dispose();check(chart.getPreferredSize().height>0,"Zero-activity month renders");
                System.out.println("Grouped monthly chart, responsive rendering, zero months, and exact table totals passed.");
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
}

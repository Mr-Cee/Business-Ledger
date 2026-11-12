package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import javax.imageio.ImageIO;

/** Renders real Swing controls and custom charts in every palette without a database. */
public final class ThemeCheck {
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{try{
            for(AppTheme.Style style:AppTheme.Style.values()){
                AppTheme.apply(style);
                JPanel root=new JPanel(new BorderLayout(12,12));root.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
                JPanel tools=new WrapPanel();tools.add(new JLabel("Business Ledger"));tools.add(new JComboBox<>(AppTheme.Style.values()));
                tools.add(new JTextField("2026-01-01",10));tools.add(new JButton("Add transaction"));tools.add(new JCheckBox("Color rows",true));root.add(tools,BorderLayout.NORTH);
                MonthlyChart chart=new MonthlyChart();chart.setMonths(Map.of(YearMonth.of(2026,1),new BigDecimal[]{new BigDecimal("7251.54"),new BigDecimal("11889.07")},YearMonth.of(2026,2),new BigDecimal[]{new BigDecimal("15541.89"),new BigDecimal("4249.77")}));
                JTable table=new BusinessLedger.LedgerTable(new DefaultTableModel(new Object[][]{{"1","2026-01-01","Income","Paycheck","7251.54"},{"2","2026-01-02","Expense","Food","89.07"}},new Object[]{"ID","Date","Type","Category","Amount"}));
                ((BusinessLedger.LedgerTable)table).colorRows=true;
                JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,chart,new JScrollPane(table));split.setDividerLocation(300);
                JTabbedPane tabs=new JTabbedPane();tabs.addTab("Charts",split);tabs.addTab("Income & expenses",new JPanel());root.add(tabs);
                root.setSize(950,520);layout(root);
                if(!chart.getBackground().equals(AppTheme.surface()))throw new AssertionError("Chart palette");
                if(table.getRowHeight()<28)throw new AssertionError("Row spacing");
                if(!table.prepareRenderer(table.getCellRenderer(0,0),0,0).getBackground().equals(AppTheme.money(true)))throw new AssertionError("Income color");
                BufferedImage image=new BufferedImage(950,520,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();root.printAll(g);g.dispose();
                ImageIO.write(image,"png",new File("target/theme-"+style.name().toLowerCase()+".png"));
            }
            System.setProperty("ledger.home","target/theme-settings-check");
            AppTheme.save(AppTheme.Style.FOREST);
            AppTheme.apply(AppTheme.Style.MIDNIGHT);AppTheme.initialize();
            if(AppTheme.current()!=AppTheme.Style.FOREST)throw new AssertionError("Theme persistence");
            System.out.println("All four themes rendered; palettes, row spacing, income colors, and saved preference passed.");
        }catch(Exception ex){throw new RuntimeException(ex);}});
    }
    private static void layout(Container container){container.doLayout();for(Component child:container.getComponents())if(child instanceof Container nested)layout(nested);}
}

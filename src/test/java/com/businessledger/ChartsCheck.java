package com.businessledger;

import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.util.List;

public final class ChartsCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1 || !args[0].contains(":55439/"))throw new IllegalArgumentException("Use fresh test database on port 55439.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Statement s=db.createStatement()){
            s.executeUpdate("CREATE TABLE ledger_settings(name TEXT PRIMARY KEY,value TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE transactions(entry_date DATE,kind TEXT,category TEXT,amount NUMERIC(14,2))");
            s.executeUpdate("INSERT INTO transactions VALUES ('2026-01-01','Income','Paycheck',100.10),('2026-01-31','Expense','Food',50.05),('2026-03-31','Expense','Supplies',200.20),('2025-12-31','Income','Other',999),('2026-04-01','Expense','Other',999)");
            ChartsPanel.Data data=ChartsPanel.load(db,LocalDate.of(2026,1,1),LocalDate.of(2026,3,31));
            check(data.income().compareTo(new BigDecimal("100.10"))==0,"Income inclusive date boundary");
            check(data.expenses().compareTo(new BigDecimal("250.25"))==0,"Exact expense decimal totals");
            check(data.categories().size()==2 && data.categories().get("Food").compareTo(new BigDecimal("50.05"))==0,"Category totals");
            check(data.months().size()==3 && data.months().get(YearMonth.of(2026,2))[0].signum()==0 && data.months().get(YearMonth.of(2026,2))[1].signum()==0,"Empty months included as zero");
            check(ChartsPanel.load(db,LocalDate.of(2024,1,1),LocalDate.of(2024,1,31)).expenses().signum()==0,"Empty period");
            SwingUtilities.invokeAndWait(()->{
                ChartsPanel.BarChart chart=new ChartsPanel.BarChart();
                chart.setBars(List.of(new ChartsPanel.Bar("Income",data.income(),new Color(45,137,92)),new ChartsPanel.Bar("Expenses",data.expenses(),new Color(193,75,75)),new ChartsPanel.Bar("Net",data.income().subtract(data.expenses()),new Color(55,104,168))));
                chart.setSize(1000,260);BufferedImage image=new BufferedImage(1000,260,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();chart.paint(g);g.dispose();
                try{ImageIO.write(image,"png",Path.of("target/chart-preview.png").toFile());}catch(Exception ex){throw new RuntimeException(ex);}
            });
            System.out.println("Chart totals, dates, categories, empty months, and rendering checks passed.");
        }
    }
}

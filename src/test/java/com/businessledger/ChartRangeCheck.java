package com.businessledger;

import java.time.LocalDate;
import java.lang.reflect.Field;
import javax.swing.*;

public final class ChartRangeCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void range(int mode,LocalDate today,int year,int month,int quarter,String start,String end){ChartsPanel.DateRange value=ChartsPanel.periodRange(mode,today,year,month,quarter);check(value.start().toString().equals(start) && value.end().toString().equals(end),"Calendar range "+mode);}
    public static void main(String[] args)throws Exception{
        LocalDate today=LocalDate.of(2026,1,15);
        range(1,today,2026,1,1,"2026-01-01","2026-01-31");
        range(2,today,2026,1,1,"2025-12-01","2025-12-31");
        range(3,today,2026,1,1,"2026-01-01","2026-03-31");
        range(4,today,2026,1,1,"2025-10-01","2025-12-31");
        range(5,today,2026,1,1,"2026-01-01","2026-12-31");
        range(6,today,2026,1,1,"2025-01-01","2025-12-31");
        range(7,today,2024,2,1,"2024-02-01","2024-02-29");
        range(8,today,2024,1,2,"2024-04-01","2024-06-30");
        range(9,today,2024,1,1,"2024-01-01","2024-12-31");
        range(11,today,2024,1,1,"2024-01-01","2024-03-31");
        range(12,today,2024,1,1,"2024-04-01","2024-06-30");
        range(13,today,2024,1,1,"2024-07-01","2024-09-30");
        range(14,today,2024,1,1,"2024-10-01","2024-12-31");
        SwingUtilities.invokeAndWait(()->{
            try{
                ChartsPanel panel=new ChartsPanel(null);
                JComboBox<?> range=(JComboBox<?>)field(panel,"range");range.setSelectedIndex(7);
                @SuppressWarnings("unchecked") JComboBox<Integer> year=(JComboBox<Integer>)field(panel,"rangeYear");year.setSelectedItem(2024);
                ((JComboBox<?>)field(panel,"rangeMonth")).setSelectedIndex(1);
                JTextField from=(JTextField)field(panel,"from"),through=(JTextField)field(panel,"through");
                check(from.getText().equals("2024-02-01") && through.getText().equals("2024-02-29") && range.getSelectedIndex()==7,"Month/year selectors fill dates without reverting to Custom");
                JComboBox<?> view=(JComboBox<?>)field(panel,"view");
                for(int index=0;index<view.getItemCount();index++){view.setSelectedIndex(index);check(from.getText().equals("2024-02-01") && through.getText().equals("2024-02-29"),"Selected range shared across chart views");}
                from.setText("2024-02-10");check(range.getSelectedIndex()==0,"Manual date edit switches to Custom");
                System.out.println("Month/quarter/year ranges, leap years, rollover, shared views, and custom-date checks passed.");
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
}

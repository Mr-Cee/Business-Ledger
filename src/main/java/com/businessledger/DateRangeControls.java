package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.time.*;
import java.util.Locale;

/** Report range controls use the same calendar calculations as the Charts tab. */
final class DateRangeControls extends JPanel {
    final JComboBox<String> range=new JComboBox<>(new String[]{"Custom dates","This month","Last month","This quarter","Last quarter","This year","Last year","Specific month","Specific quarter","Specific year","All recorded dates","Q1 (Jan–Mar)","Q2 (Apr–Jun)","Q3 (Jul–Sep)","Q4 (Oct–Dec)"});
    final JComboBox<String> month=new JComboBox<>(),quarter=new JComboBox<>(new String[]{"Q1 (Jan–Mar)","Q2 (Apr–Jun)","Q3 (Jul–Sep)","Q4 (Oct–Dec)"});
    final JComboBox<Integer> year=new JComboBox<>();
    private final Connection db;
    private final JTextField from,through;
    private boolean updating;
    private final boolean includeMileage;
    DateRangeControls(Connection db,JTextField from,JTextField through,Runnable changed){
        this(db,from,through,changed,false);
    }
    DateRangeControls(Connection db,JTextField from,JTextField through,Runnable changed,boolean includeMileage){
        super(new BorderLayout());this.db=db;this.from=from;this.through=through;
        this.includeMileage=includeMileage;
        LocalDate today=LocalDate.now();
        for(Month value:Month.values())month.addItem(value.getDisplayName(java.time.format.TextStyle.FULL,Locale.getDefault()));
        month.setSelectedIndex(today.getMonthValue()-1);quarter.setSelectedIndex((today.getMonthValue()-1)/3);
        for(int value=today.getYear()-20;value<=today.getYear()+5;value++)year.addItem(value);
        year.setEditable(true);year.setSelectedItem(today.getYear());year.setToolTipText("Choose a year, or type another year and press Enter.");
        JPanel months=part("Month",month),quarters=part("Quarter",quarter),years=part("Year",year);
        months.setVisible(false);quarters.setVisible(false);years.setVisible(false);
        WrapPanel bar=new WrapPanel();bar.addField("Date range",range);bar.add(months);bar.add(quarters);bar.add(years);add(bar);
        Runnable apply=()->{changed.run();try{applyToDates();}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Unable to apply date range",JOptionPane.ERROR_MESSAGE);}};
        range.addActionListener(e->{int mode=range.getSelectedIndex();months.setVisible(mode==7);quarters.setVisible(mode==8);years.setVisible((mode>=7 && mode<=9)||mode>=11);revalidate();if(mode!=0)apply.run();});
        month.addActionListener(e->{if(range.getSelectedIndex()==7)apply.run();});
        quarter.addActionListener(e->{if(range.getSelectedIndex()==8)apply.run();});
        year.addActionListener(e->{int mode=range.getSelectedIndex();if((mode>=7 && mode<=9)||mode>=11)apply.run();});
        javax.swing.event.DocumentListener edited=new javax.swing.event.DocumentListener(){
            private void change(){if(!updating){range.setSelectedIndex(0);changed.run();}}
            public void insertUpdate(javax.swing.event.DocumentEvent e){change();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){change();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){change();}
        };
        from.getDocument().addDocumentListener(edited);through.getDocument().addDocumentListener(edited);
    }
    private static JPanel part(String label,JComponent field){JPanel p=new JPanel(new BorderLayout(5,0));p.add(new JLabel(label),BorderLayout.WEST);p.add(field);return p;}
    void applyToDates()throws SQLException{
        int mode=range.getSelectedIndex();if(mode==0)return;
        LocalDate today=LocalDate.now();ChartsPanel.DateRange dates;
        if(mode==10){
            try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT MIN(day),MAX(day) FROM (SELECT entry_date AS day FROM transactions UNION ALL SELECT transfer_date FROM transfers"+(includeMileage?" UNION ALL SELECT trip_date FROM mileage":"")+") dates")){
                r.next();dates=r.getDate(1)==null?new ChartsPanel.DateRange(today.withDayOfYear(1),today):new ChartsPanel.DateRange(r.getDate(1).toLocalDate(),r.getDate(2).toLocalDate());
            }
        }else{
            int selected=today.getYear();
            if((mode>=7 && mode<=9)||mode>=11){
                try{selected=Integer.parseInt(year.getEditor().getItem().toString().trim());}catch(NumberFormatException ex){throw new IllegalArgumentException("Enter a valid year from 1 to 9999.");}
                if(selected<1||selected>9999)throw new IllegalArgumentException("Enter a year from 1 to 9999.");
            }
            dates=ChartsPanel.periodRange(mode,today,selected,month.getSelectedIndex()+1,quarter.getSelectedIndex()+1);
        }
        updating=true;
        try{from.setText(dates.start().toString());through.setText(dates.end().toString());}finally{updating=false;}
    }
}

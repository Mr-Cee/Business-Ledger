package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.*;

/** Read-only overview of the signed-in ledger. Transfers never count as income or expenses. */
final class DashboardPanel extends JPanel {
    private static final NumberFormat MONEY=NumberFormat.getCurrencyInstance(Locale.US);
    private final Connection db;
    private final JTextField from=new JTextField(LocalDate.now().withDayOfYear(1).toString(),10),through=new JTextField(LocalDate.now().toString(),10);
    private final JLabel income=new JLabel("—"),expenses=new JLabel("—"),net=new JLabel("—"),miles=new JLabel("—");
    private final JLabel estimatedTax=new JLabel("-"),taxCaption=new JLabel("Current year - 24% of income");
    private final JTextArea status=new JTextArea(" ",2,0);
    private final JLabel balancesTitle=new JLabel("Account balances");
    private final MonthlyChart monthly=new MonthlyChart();
    private final DefaultTableModel accounts=model("Account","Type","Balance ($)"),categories=model("Expense category","Total ($)");
    private final DateRangeControls ranges;
    private final javax.swing.Timer delayed;
    record Data(ChartsPanel.Data activity,BigDecimal miles,java.util.List<Object[]> accounts,TaxEstimate.Estimate tax){}

    DashboardPanel(Connection db){
        super(new BorderLayout(12,12));this.db=db;
        delayed=new javax.swing.Timer(250,e->refreshSafely());delayed.setRepeats(false);
        ranges=new DateRangeControls(db,from,through,()->delayed.restart(),true);
        JPanel header=new JPanel(new BorderLayout(0,6));
        JLabel title=new JLabel("Your business at a glance");title.setFont(title.getFont().deriveFont(Font.BOLD,22f));
        JPanel heading=new JPanel(new BorderLayout(0,6));heading.add(title,BorderLayout.NORTH);heading.add(new UpdateNotice());
        header.add(heading,BorderLayout.NORTH);
        JPanel filters=new JPanel(new BorderLayout());filters.add(ranges,BorderLayout.NORTH);
        WrapPanel dates=new WrapPanel();dates.addField("From",new BusinessLedger.DateChooser(from));dates.addField("Through (inclusive)",new BusinessLedger.DateChooser(through));
        JButton refresh=new JButton("Refresh dashboard");refresh.addActionListener(e->refreshSafely());dates.add(refresh);filters.add(dates);header.add(filters);add(header,BorderLayout.NORTH);
        WrapPanel cards=new WrapPanel();cards.add(card("Income",income,"Selected dates · excludes transfers"));cards.add(card("Expenses",expenses,"Selected dates · excludes transfers"));
        cards.add(card("Net cash flow",net,"Income minus expenses"));cards.add(card("Business mileage",miles,"Miles logged in selected dates"));
        cards.add(card("Estimated taxes due",estimatedTax,taxCaption));
        JPanel sections=new JPanel();sections.setLayout(new BoxLayout(sections,BoxLayout.Y_AXIS));
        sections.add(section("Monthly income & expenses",new JScrollPane(monthly),340));sections.add(Box.createVerticalStrut(12));
        JPanel balances=section(null,new JScrollPane(new JTable(accounts)),230);balancesTitle.setFont(balancesTitle.getFont().deriveFont(Font.BOLD,16f));balances.add(balancesTitle,BorderLayout.NORTH);sections.add(balances);sections.add(Box.createVerticalStrut(12));
        sections.add(section("Top 5 expense categories",new JScrollPane(new JTable(categories)),230));
        Body body=new Body();body.add(cards,BorderLayout.NORTH);body.add(sections);
        JScrollPane scroll=new JScrollPane(body);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroll.getVerticalScrollBar().setUnitIncrement(24);add(scroll);
        status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setBackground(getBackground());
        status.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));add(status,BorderLayout.SOUTH);
    }
    private static DefaultTableModel model(String... columns){return new DefaultTableModel(columns,0){
        @Override public boolean isCellEditable(int row,int column){return false;}
        @Override public Class<?> getColumnClass(int column){return column==getColumnCount()-1?BigDecimal.class:String.class;}
    };}
    private static JPanel card(String title,JLabel value,String caption){
        return card(title,value,new JLabel(caption));
    }
    private static JPanel card(String title,JLabel value,JLabel help){
        JPanel panel=new JPanel(new BorderLayout(0,8));panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppTheme.grid()),BorderFactory.createEmptyBorder(16,16,16,16)));
        value.setFont(value.getFont().deriveFont(Font.BOLD,24f));panel.add(new JLabel(title),BorderLayout.NORTH);panel.add(value);help.setFont(help.getFont().deriveFont(11f));panel.add(help,BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(245,120));return panel;
    }
    private static JPanel section(String title,JComponent contents,int height){
        JPanel panel=new JPanel(new BorderLayout(8,10));panel.setBorder(BorderFactory.createEmptyBorder(12,8,8,8));
        if(title!=null){JLabel label=new JLabel(title);label.setFont(label.getFont().deriveFont(Font.BOLD,16f));panel.add(label,BorderLayout.NORTH);}
        panel.add(contents);panel.setPreferredSize(new Dimension(600,height));panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));return panel;
    }
    static Data load(Connection db,LocalDate start,LocalDate end)throws SQLException{
        if(start.isAfter(end))throw new IllegalArgumentException("From date must be on or before the through date.");
        if(java.time.temporal.ChronoUnit.MONTHS.between(start.withDayOfMonth(1),end.withDayOfMonth(1))>1200)throw new IllegalArgumentException("Choose a date range of at most 100 years.");
        ChartsPanel.Data activity=ChartsPanel.load(db,start,end);BigDecimal miles;
        try(PreparedStatement s=db.prepareStatement("SELECT COALESCE(SUM(miles),0) FROM mileage WHERE trip_date BETWEEN ? AND ?")){
            s.setObject(1,start);s.setObject(2,end);try(ResultSet r=s.executeQuery()){r.next();miles=r.getBigDecimal(1);}
        }
        java.util.List<Object[]> accounts=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement("""
            WITH events AS (
                SELECT account_id,entry_date AS day,CASE WHEN kind='Income' THEN amount ELSE -amount END AS amount FROM transactions
                UNION ALL SELECT from_account_id,transfer_date,-amount FROM transfers
                UNION ALL SELECT to_account_id,transfer_date,amount FROM transfers
            ), flows AS (SELECT account_id,SUM(amount) AS amount FROM events WHERE day<=? GROUP BY account_id)
            SELECT a.name,a.account_type,a.opening_balance+COALESCE(f.amount,0),a.active
            FROM accounts a LEFT JOIN flows f ON f.account_id=a.id ORDER BY a.active DESC,a.name
            """)){
            s.setObject(1,end);try(ResultSet r=s.executeQuery()){while(r.next()){
                BigDecimal balance=r.getBigDecimal(3);if(r.getBoolean(4)||balance.signum()!=0)accounts.add(new Object[]{r.getString(1)+(r.getBoolean(4)?"":" (archived)"),r.getString(2),balance});
            }}
        }
        return new Data(activity,miles,accounts,TaxEstimate.load(db,LocalDate.now()));
    }
    void refreshSafely(){
        delayed.stop();
        try{ranges.applyToDates();LocalDate start=LocalDate.parse(from.getText().trim()),end=LocalDate.parse(through.getText().trim());render(load(db,start,end),end);
            status.setText("Updated: "+start+" through "+end+". "+IncomeScope.load(db).description()+". Balances include prior activity, opening balances and transfers.");
        }catch(Exception ex){clear();status.setText("Unable to load dashboard: "+ex.getMessage());}
    }
    void stopPendingRefresh(){delayed.stop();}
    void render(Data data,LocalDate end){
        TaxEstimate.Estimate tax=data.tax();estimatedTax.setText(MONEY.format(tax.amount()));
        taxCaption.setText("Current year ("+tax.year()+") at "+tax.rate().stripTrailingZeros().toPlainString()+"%");
        estimatedTax.setToolTipText(tax.year()+" income: "+MONEY.format(tax.income())+" x "+tax.rate().stripTrailingZeros().toPlainString()+"%. Uses the income category setting; excludes expenses and tax payments. Independent of dashboard dates.");
        income.setText(MONEY.format(data.activity().income()));expenses.setText(MONEY.format(data.activity().expenses()));
        net.setText(MONEY.format(data.activity().income().subtract(data.activity().expenses())));miles.setText(NumberFormat.getNumberInstance(Locale.US).format(data.miles())+" mi");
        monthly.setMonths(data.activity().months());accounts.setRowCount(0);for(Object[] row:data.accounts())accounts.addRow(row);
        categories.setRowCount(0);data.activity().categories().entrySet().stream().sorted(Map.Entry.<String,BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(5).forEach(e->categories.addRow(new Object[]{e.getKey(),e.getValue()}));
        balancesTitle.setText("Account balances as of "+end+" · signed balances");
    }
    private void clear(){for(JLabel label:new JLabel[]{income,expenses,net,miles,estimatedTax})label.setText("-");estimatedTax.setToolTipText(null);monthly.setMonths(Map.of());accounts.setRowCount(0);categories.setRowCount(0);}
    private static final class Body extends JPanel implements Scrollable{
        Body(){super(new BorderLayout(0,12));}
        public Dimension getPreferredScrollableViewportSize(){return new Dimension(800,600);}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(24,r.height-24);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
}

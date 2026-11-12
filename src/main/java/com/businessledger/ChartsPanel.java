package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/** Local charts drawn from this profile's transaction table, using exact decimal totals. */
final class ChartsPanel extends JPanel {
    private static final Color GREEN = new Color(45,137,92), RED = new Color(193,75,75), BLUE = new Color(55,104,168);
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);
    record Bar(String label,BigDecimal value,Color color) {}
    record Data(BigDecimal income,BigDecimal expenses,Map<String,BigDecimal> categories,Map<YearMonth,BigDecimal[]> months) {}
    private final Connection db;
    private final JTextField from = new JTextField(LocalDate.now().withDayOfYear(1).toString(),10), through = new JTextField(LocalDate.now().toString(),10);
    private final JComboBox<String> range = new JComboBox<>(new String[]{"Custom dates","This month","Last month","This quarter","Last quarter","This year","Last year","Specific month","Specific quarter","Specific year","All recorded dates","Q1 (Jan–Mar)","Q2 (Apr–Jun)","Q3 (Jul–Sep)","Q4 (Oct–Dec)"});
    private final JComboBox<String> rangeMonth = new JComboBox<>(), rangeQuarter = new JComboBox<>(new String[]{"Q1 (Jan–Mar)","Q2 (Apr–Jun)","Q3 (Jul–Sep)","Q4 (Oct–Dec)"});
    private final JComboBox<Integer> rangeYear = new JComboBox<>();
    private final JPanel monthGroup,quarterGroup,yearGroup;
    private boolean applyingRange;
    record DateRange(LocalDate start,LocalDate end) {}
    private final JComboBox<String> view = new JComboBox<>(new String[]{"Income vs expenses", "Expenses by category", "Monthly income & expenses", "Account balances over time", "Top payees (pie)", "Account transfers"});
    private final JComboBox<String> transferView=new JComboBox<>(new String[]{"By account","Between accounts"});
    private final WrapPanel transferOptions=new WrapPanel();
    private final TransferChart transferChart=new TransferChart();
    private TransferChart.Data transferData;
    private final JComboBox<String> frequency = new JComboBox<>(new String[]{"Daily", "Monthly", "Yearly"});
    private record BalanceAccount(Long id,String name){@Override public String toString(){return name;}}
    private final JComboBox<BalanceAccount> balanceAccount = new JComboBox<>();
    private boolean updatingAccounts;
    private final WrapPanel balanceControls = new WrapPanel(), legend = new WrapPanel();
    private final JPanel balanceOptions = new JPanel(new BorderLayout());
    private final BalanceChart balanceChart = new BalanceChart();
    private BalanceChart.Data balances;
    private JScrollPane chartScroll;
    private final JLabel incomeLabel = new JLabel("Income: —"), expenseLabel = new JLabel("Expenses: —"), netLabel = new JLabel("Net: —");
    private final JTextArea status = new JTextArea("Choose dates and click Refresh charts.",3,0);
    private final BarChart chart = new BarChart();
    private final MonthlyChart monthlyChart = new MonthlyChart();
    private final PayeePieChart payeePie=new PayeePieChart();
    private List<PayeePieChart.Slice> payees=List.of();
    private final DefaultTableModel table = new DefaultTableModel(new String[]{"Label","Amount ($)"},0) {
        @Override public boolean isCellEditable(int row,int col) { return false; }
        @Override public Class<?> getColumnClass(int col) {
            if(view.getSelectedIndex()==5){if(col==0||(transferView.getSelectedIndex()==1&&col==1))return String.class;if(col==getColumnCount()-1)return Long.class;}
            return col>0 ? BigDecimal.class : String.class;
        }
    };
    private Data data;
    private final JTable numbers = new JTable(table);
    ChartsPanel(Connection db) {
        super(new BorderLayout(10,10)); this.db=db;
        LocalDate today=LocalDate.now();
        for(Month month:Month.values())rangeMonth.addItem(month.getDisplayName(java.time.format.TextStyle.FULL,Locale.getDefault()));
        rangeMonth.setSelectedIndex(today.getMonthValue()-1);rangeQuarter.setSelectedIndex((today.getMonthValue()-1)/3);
        for(int year=today.getYear()-20;year<=today.getYear()+5;year++)rangeYear.addItem(year);
        rangeYear.setEditable(true);rangeYear.setSelectedItem(today.getYear());rangeYear.setToolTipText("Choose a year, or type another year and press Enter.");
        monthGroup=rangePart("Month",rangeMonth);quarterGroup=rangePart("Quarter",rangeQuarter);yearGroup=rangePart("Year",rangeYear);
        monthGroup.setVisible(false);quarterGroup.setVisible(false);yearGroup.setVisible(false);
        WrapPanel ranges=new WrapPanel();ranges.addField("Date range",range);ranges.add(monthGroup);ranges.add(quarterGroup);ranges.add(yearGroup);
        WrapPanel dates=new WrapPanel(); dates.addField("From",new BusinessLedger.DateChooser(from)); dates.addField("Through (inclusive)",new BusinessLedger.DateChooser(through));
        JButton refresh=new JButton("Refresh charts"); refresh.addActionListener(e -> refreshSafely()); dates.add(refresh); dates.add(view);
        WrapPanel summary=new WrapPanel();summary.add(incomeLabel);summary.add(expenseLabel);summary.add(netLabel);
        balanceAccount.addItem(new BalanceAccount(null,"All accounts"));
        balanceControls.addField("Balance interval",frequency);balanceControls.addField("Account",balanceAccount);balanceOptions.add(balanceControls,BorderLayout.NORTH);balanceOptions.add(legend);balanceOptions.setVisible(false);
        JPanel dateControls=new JPanel(new BorderLayout());dateControls.add(ranges,BorderLayout.NORTH);dateControls.add(dates);
        transferOptions.addField("Transfer view",transferView);transferOptions.setVisible(false);
        JPanel extra=new JPanel(new BorderLayout());extra.add(balanceOptions,BorderLayout.NORTH);extra.add(transferOptions);
        JPanel top=new JPanel(new BorderLayout(0,8));top.add(dateControls,BorderLayout.NORTH);top.add(summary);top.add(extra,BorderLayout.SOUTH);add(top,BorderLayout.NORTH);
        numbers.setAutoCreateRowSorter(true);
        numbers.setRowHeight(Math.max(numbers.getRowHeight(),numbers.getFontMetrics(numbers.getFont()).getHeight()+6));
        chartScroll=new JScrollPane(chart);chartScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);chartScroll.setMinimumSize(new Dimension(0,80));
        JScrollPane numberScroll=new JScrollPane(numbers);numberScroll.setMinimumSize(new Dimension(0,70));
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,chartScroll,numberScroll);split.setResizeWeight(.7);split.setMinimumSize(new Dimension(0,160));add(split);
        status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setBackground(getBackground());add(status,BorderLayout.SOUTH);
        view.addActionListener(e -> {balanceOptions.setVisible(view.getSelectedIndex()==3);transferOptions.setVisible(view.getSelectedIndex()==5);refreshSafely();});
        transferView.addActionListener(e->{if(view.getSelectedIndex()==5&&transferData!=null)renderTransfers();});
        frequency.addActionListener(e -> {if(view.getSelectedIndex()==3)refreshSafely();});
        balanceAccount.addActionListener(e -> {if(!updatingAccounts && balances!=null && view.getSelectedIndex()==3)renderBalances();});
        range.addActionListener(e -> {
            int mode=range.getSelectedIndex();monthGroup.setVisible(mode==7);quarterGroup.setVisible(mode==8);yearGroup.setVisible((mode>=7 && mode<=9) || mode>=11);revalidate();
            if(mode!=0)refreshSafely();
        });
        rangeMonth.addActionListener(e -> {if(range.getSelectedIndex()==7)refreshSafely();});
        rangeQuarter.addActionListener(e -> {if(range.getSelectedIndex()==8)refreshSafely();});
        rangeYear.addActionListener(e -> {int mode=range.getSelectedIndex();if((mode>=7 && mode<=9) || mode>=11)refreshSafely();});
        javax.swing.event.DocumentListener edited=new javax.swing.event.DocumentListener() {
            private void changed(){if(!applyingRange){range.setSelectedIndex(0);invalidateData();}}
            public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}
        };
        from.getDocument().addDocumentListener(edited);through.getDocument().addDocumentListener(edited);
    }
    private static JPanel rangePart(String label,JComponent input){JPanel group=new JPanel(new BorderLayout(5,0));group.add(new JLabel(label),BorderLayout.WEST);group.add(input);return group;}
    static DateRange periodRange(int mode,LocalDate today,int year,int month,int quarter){
        LocalDate start;LocalDate end;
        switch(mode){
            case 1,2->{YearMonth value=YearMonth.from(today).minusMonths(mode==2?1:0);start=value.atDay(1);end=value.atEndOfMonth();}
            case 3,4->{start=LocalDate.of(today.getYear(),((today.getMonthValue()-1)/3)*3+1,1);if(mode==4)start=start.minusMonths(3);end=start.plusMonths(3).minusDays(1);}
            case 5,6->{int chosen=today.getYear()-(mode==6?1:0);start=LocalDate.of(chosen,1,1);end=LocalDate.of(chosen,12,31);}
            case 7->{YearMonth value=YearMonth.of(year,month);start=value.atDay(1);end=value.atEndOfMonth();}
            case 8->{if(quarter<1 || quarter>4)throw new IllegalArgumentException("Choose a quarter from 1 to 4.");start=LocalDate.of(year,(quarter-1)*3+1,1);end=start.plusMonths(3).minusDays(1);}
            case 9->{start=LocalDate.of(year,1,1);end=LocalDate.of(year,12,31);}
            case 11,12,13,14->{start=LocalDate.of(year,(mode-11)*3+1,1);end=start.plusMonths(3).minusDays(1);}
            default->throw new IllegalArgumentException("Choose a calendar date range.");
        }
        return new DateRange(start,end);
    }
    private void updateRangeDates()throws SQLException {
        int mode=range.getSelectedIndex();if(mode==0)return;
        LocalDate today=LocalDate.now();DateRange chosen;
        if(mode==10){
            try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT MIN(day),MAX(day) FROM (SELECT entry_date AS day FROM transactions UNION ALL SELECT transfer_date FROM transfers) dates")){
                r.next();chosen=r.getDate(1)==null?new DateRange(today.withDayOfYear(1),today):new DateRange(r.getDate(1).toLocalDate(),r.getDate(2).toLocalDate());
            }
        }else{
            int year=today.getYear();
            if((mode>=7 && mode<=9) || mode>=11){try{year=Integer.parseInt(rangeYear.getEditor().getItem().toString().trim());}catch(NumberFormatException ex){throw new IllegalArgumentException("Enter a valid year from 1 to 9999.");}if(year<1 || year>9999)throw new IllegalArgumentException("Enter a year from 1 to 9999.");}
            chosen=periodRange(mode,today,year,rangeMonth.getSelectedIndex()+1,rangeQuarter.getSelectedIndex()+1);
        }
        applyingRange=true;
        try{from.setText(chosen.start().toString());through.setText(chosen.end().toString());}finally{applyingRange=false;}
    }
    private void invalidateData() {transferData=null;transferChart.setData(TransferChart.empty(),false);payees=List.of();payeePie.setSlices(List.of());data=null;balances=null;incomeLabel.setText("Income: -");expenseLabel.setText("Expenses: -");netLabel.setText("Net: -");table.setRowCount(0);chart.setBars(List.of());monthlyChart.setMonths(Map.of());balanceChart.setData(new BalanceChart.Data(List.of(),List.of()));status.setText("Dates changed - click Refresh charts.");}
    void settingsChanged(){invalidateData();status.setText("Income settings changed - click Refresh charts.");}
    void refreshSafely() {
        try {
            updateRangeDates();
            LocalDate start=LocalDate.parse(from.getText().trim()),end=LocalDate.parse(through.getText().trim());
            if(start.isAfter(end))throw new IllegalArgumentException("From date must be on or before the through date.");
            if(ChronoUnit.MONTHS.between(YearMonth.from(start),YearMonth.from(end))>1199)throw new IllegalArgumentException("Choose a chart period of 100 years or less.");
            if(view.getSelectedIndex()==5){
                transferData=TransferChart.load(db,start,end);renderTransfers();
                status.setText("Transfers for "+start+" through "+end+". Total transferred counts each transfer once. In/Out are movements, not account balances.\nTransfers remain excluded from income/expense charts and reports. Hover a row for exact amounts and counts.");
            }else if(view.getSelectedIndex()==3){
                balances=BalanceChart.load(db,start,end,BalanceChart.Frequency.values()[frequency.getSelectedIndex()]);reloadBalanceAccounts();renderBalances();
                status.setText("Closing balances: "+start+" through "+end+". Includes opening balances, prior activity and transfers.\nMonthly/yearly points use period-end balances (final point uses Through date). Hover or use the table for exact values. Unassigned transactions excluded.");
            }else{
                data=load(db,start,end);if(view.getSelectedIndex()==4)payees=PayeePieChart.load(db,start,end);render();
                status.setText("Charts for "+start+" through "+end+". "+IncomeScope.load(db).description()+".\n"+(view.getSelectedIndex()==4?"Expenses only; transfers excluded. Top 8 payees, with remaining payees grouped as Other. Blank names = Unspecified payee. Hover a slice or legend row for details.":"Green = income; red = expenses; blue = net. Transfers and opening balances are excluded. Exact values are listed below the chart."));
            }
        }catch(Exception ex){invalidateData();status.setText("Unable to load charts: "+ex.getMessage());}
    }
    static Data load(Connection db,LocalDate start,LocalDate end)throws SQLException {
        IncomeScope scope=IncomeScope.load(db);
        BigDecimal income=BigDecimal.ZERO,expenses=BigDecimal.ZERO;
        Map<String,BigDecimal> categories=new TreeMap<>();Map<YearMonth,BigDecimal[]> months=new TreeMap<>();
        for(YearMonth month=YearMonth.from(start);!month.isAfter(YearMonth.from(end));month=month.plusMonths(1))months.put(month,new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});
        try(PreparedStatement s=db.prepareStatement("SELECT date_trunc('month',entry_date)::date,kind,category,SUM(amount) FROM transactions WHERE entry_date BETWEEN ? AND ?"+scope.sql()+" GROUP BY 1,2,3 ORDER BY 1,2,3")) {
            s.setObject(1,start);s.setObject(2,end);
            if(scope.category()!=null)s.setString(3,scope.category());
            try(ResultSet r=s.executeQuery()) {while(r.next()) {
                YearMonth month=YearMonth.from(r.getDate(1).toLocalDate());boolean incoming="Income".equals(r.getString(2));BigDecimal amount=r.getBigDecimal(4);
                BigDecimal[] bucket=months.get(month);bucket[incoming?0:1]=bucket[incoming?0:1].add(amount);
                if(incoming)income=income.add(amount);else{expenses=expenses.add(amount);categories.merge(r.getString(3),amount,BigDecimal::add);}
            }}
        }
        return new Data(income,expenses,categories,months);
    }
    private void render() {
        if(data==null)return;
        chartScroll.setViewportView(chart);numbers.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);table.setColumnIdentifiers(new String[]{"Label","Amount ($)"});
        incomeLabel.setText("Income: "+MONEY.format(data.income()));expenseLabel.setText("Expenses: "+MONEY.format(data.expenses()));netLabel.setText("Net: "+MONEY.format(data.income().subtract(data.expenses())));
        if(view.getSelectedIndex()==4){
            chartScroll.setViewportView(payeePie);payeePie.setSlices(payees);table.setRowCount(0);table.setColumnIdentifiers(new String[]{"Payee","Spent ($)","Share of expenses (%)"});
            for(PayeePieChart.Slice slice:payees)table.addRow(new Object[]{slice.label(),slice.amount(),payeePie.percent(slice)});return;
        }
        if(view.getSelectedIndex()==2){
            chartScroll.setViewportView(monthlyChart);monthlyChart.setMonths(data.months());table.setRowCount(0);table.setColumnIdentifiers(new String[]{"Month","Income ($)","Expenses ($)","Net ($)"});
            data.months().forEach((month,values)->table.addRow(new Object[]{month.toString(),values[0],values[1],values[0].subtract(values[1])}));return;
        }
        List<Bar> bars=new ArrayList<>();
        if(view.getSelectedIndex()==0){bars.add(new Bar("Income",data.income(),GREEN));bars.add(new Bar("Expenses",data.expenses(),RED));bars.add(new Bar("Net",data.income().subtract(data.expenses()),BLUE));}
        else if(view.getSelectedIndex()==1){data.categories().entrySet().stream().sorted(Map.Entry.<String,BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).forEach(e -> bars.add(new Bar(e.getKey(),e.getValue(),RED)));}
        table.setRowCount(0);for(Bar bar:bars)table.addRow(new Object[]{bar.label(),bar.value()});chart.setBars(bars);
    }
    private void reloadBalanceAccounts(){
        BalanceAccount selected=(BalanceAccount)balanceAccount.getSelectedItem();Long id=selected==null?null:selected.id();
        updatingAccounts=true;
        try{
            balanceAccount.removeAllItems();balanceAccount.addItem(new BalanceAccount(null,"All accounts"));
            for(BalanceChart.Series series:balances.series()){
                BalanceAccount account=new BalanceAccount(series.id(),series.name());balanceAccount.addItem(account);
                if(Objects.equals(id,account.id()))balanceAccount.setSelectedItem(account);
            }
        }finally{updatingAccounts=false;}
    }
    private void renderTransfers(){
        boolean routes=transferView.getSelectedIndex()==1;chartScroll.setViewportView(transferChart);transferChart.setData(transferData,routes);numbers.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);table.setRowCount(0);
        if(routes){
            table.setColumnIdentifiers(new String[]{"From account","To account","Transferred ($)","Transfers"});
            for(TransferChart.Route route:transferData.routes())table.addRow(new Object[]{route.from(),route.to(),route.amount(),route.count()});
        }else{
            table.setColumnIdentifiers(new String[]{"Account","Transferred in ($)","Transferred out ($)","Net movement ($)","Transfers"});
            for(TransferChart.Account account:transferData.accounts())table.addRow(new Object[]{account.name(),account.incoming(),account.outgoing(),account.net(),account.count()});
        }
        incomeLabel.setText("Total transferred: "+MONEY.format(transferData.total()));expenseLabel.setText("Transfers: "+transferData.count());netLabel.setText("Account routes: "+transferData.routes().size());
    }
    private void renderBalances(){
        BalanceAccount selected=(BalanceAccount)balanceAccount.getSelectedItem();Long id=selected==null?null:selected.id();
        List<BalanceChart.Series> shown=balances.series().stream().filter(series->id==null || series.id()==id).toList();
        chartScroll.setViewportView(balanceChart);balanceChart.setData(new BalanceChart.Data(balances.dates(),shown));legend.removeAll();
        List<String> columns=new ArrayList<>();columns.add("Closing date");BigDecimal closing=BigDecimal.ZERO;
        for(BalanceChart.Series series:shown){
            columns.add(series.name());JLabel label=new JLabel("■ "+series.name());label.setForeground(BalanceChart.color(series.id()));legend.add(label);
            if(!series.balances().isEmpty())closing=closing.add(series.balances().get(series.balances().size()-1));
        }
        table.setRowCount(0);table.setColumnIdentifiers(columns.toArray());
        for(int point=0;point<balances.dates().size();point++){
            Object[] row=new Object[columns.size()];row[0]=balances.dates().get(point).toString();
            for(int account=0;account<shown.size();account++)row[account+1]=shown.get(account).balances().get(point);table.addRow(row);
        }
        numbers.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);for(int col=0;col<numbers.getColumnCount();col++)numbers.getColumnModel().getColumn(col).setPreferredWidth(col==0?130:180);
        incomeLabel.setText("Accounts: "+shown.size());expenseLabel.setText((id==null?"Combined closing balance: ":"Closing balance: ")+MONEY.format(closing));netLabel.setText(frequency.getSelectedItem()+" closing balances");
        legend.revalidate();legend.repaint();
    }
    static final class BarChart extends JPanel implements Scrollable {
        private List<Bar> bars=List.of();
        BarChart(){setBackground(AppTheme.surface());}
        void setBars(List<Bar> values){bars=List.copyOf(values);setPreferredSize(new Dimension(850,Math.max(240,values.size()*42+80)));revalidate();repaint();}
        public Dimension getPreferredScrollableViewportSize(){return new Dimension(600,280);}
        public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 42;}
        public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(42,visible.height-42);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
        @Override protected void paintComponent(Graphics graphics){
            setBackground(AppTheme.surface());super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(getFont());
                if(bars.isEmpty()){g.setColor(AppTheme.text());g.drawString("No matching expense categories. Choose dates and refresh to load data.",24,48);return;}
                FontMetrics metrics=g.getFontMetrics();int valueWidth=0;
                for(Bar b:bars)valueWidth=Math.max(valueWidth,metrics.stringWidth(MONEY.format(b.value())));
                int left=Math.min(210,Math.max(80,getWidth()/4));
                int right=Math.max(left+1,getWidth()-valueWidth-28),width=right-left;
                double min=0,max=0;for(Bar b:bars){min=Math.min(min,b.value().doubleValue());max=Math.max(max,b.value().doubleValue());}if(max==min)max=min+1;
                int zero=left+(int)((0-min)/(max-min)*width);
                g.setColor(AppTheme.grid());g.drawLine(zero,32,zero,48+bars.size()*42);
                for(int i=0;i<bars.size();i++){
                    Bar b=bars.get(i);int y=48+i*42,x=left+(int)((b.value().doubleValue()-min)/(max-min)*width);
                    g.setColor(AppTheme.text());String label=b.label();FontMetrics fm=g.getFontMetrics();
                    while(fm.stringWidth(label)>left-28 && label.length()>1)label=label.substring(0,label.length()-1);
                    if(!label.equals(b.label()))label=label.substring(0,Math.max(0,label.length()-1))+"…";
                    g.drawString(label,14,y+17);g.setColor(b.color());g.fillRoundRect(Math.min(zero,x),y,Math.max(b.value().signum()==0?0:1,Math.abs(x-zero)),24,5,5);
                    g.setColor(AppTheme.text());g.drawString(MONEY.format(b.value()),right+12,y+17);
                }
                g.setColor(AppTheme.muted());g.drawString("$0",zero-8,24);
            }finally{g.dispose();}
        }
    }
}

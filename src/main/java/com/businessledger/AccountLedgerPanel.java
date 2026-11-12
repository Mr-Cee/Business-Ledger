package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/** Account history retains every event before calculating running balances or filtering rows. */
final class AccountLedgerPanel extends JPanel {
    record Entry(long id,LocalDate day,String kind,String category,BigDecimal amount,String party,String notes,BigDecimal balance,String edited) {
        boolean transfer(){return kind.startsWith("Transfer ");}
        boolean incoming(){return kind.equals("Income")||kind.equals("Transfer In");}
        Object[] row(String account){return new Object[]{id,day,kind,category,amount,party,notes,account,balance,edited};}
    }
    record Summary(BigDecimal opening,BigDecimal in,BigDecimal out,BigDecimal closing){}
    record Data(long id,String name,String type,boolean active,BigDecimal opening,List<Entry> entries){
        Summary summary(LocalDate start,LocalDate end){
            if(start!=null&&end!=null&&start.isAfter(end))throw new IllegalArgumentException("From must be on or before Through.");
            BigDecimal carry=opening,in=BigDecimal.ZERO,out=BigDecimal.ZERO;
            for(Entry entry:entries){
                BigDecimal delta=entry.incoming()?entry.amount():entry.amount().negate();
                if(start!=null&&entry.day().isBefore(start)){carry=carry.add(delta);continue;}
                if(end!=null&&entry.day().isAfter(end))continue;
                if(entry.incoming())in=in.add(entry.amount());else out=out.add(entry.amount());
            }
            return new Summary(carry,in,out,carry.add(in).subtract(out));
        }
    }
    @FunctionalInterface interface EntryAction {void run(Entry entry)throws Exception;}
    private final Connection db;
    private final long accountId;
    private Data data;
    private final JLabel heading=new JLabel();
    private final JLabel openingValue=new JLabel(),inValue=new JLabel(),outValue=new JLabel(),closingValue=new JLabel();
    private final DefaultTableModel rows=new DefaultTableModel(new String[]{"ID","Date","Type","Category","Amount ($)","Payee / customer","Notes","Account","Running balance ($)","Edited"},0){
        @Override public boolean isCellEditable(int row,int column){return false;}
        @Override public Class<?> getColumnClass(int column){return column==0?Long.class:column==1?LocalDate.class:column==4||column==8?BigDecimal.class:String.class;}
    };
    private final JTable table=new BusinessLedger.LedgerTable(rows);
    private final LedgerFilters filters;

    AccountLedgerPanel(Connection db,long accountId,LocalDate start,LocalDate end,EntryAction edit,EntryAction history)throws SQLException{
        super(new BorderLayout(8,8));this.db=db;this.accountId=accountId;
        setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD,20f));
        WrapPanel balances=new WrapPanel();for(JLabel label:new JLabel[]{openingValue,inValue,outValue,closingValue})balances.add(label);
        JTextArea explanation=new JTextArea("Balances include all account activity and transfers. Search, type, category, and amount filters only narrow the rows.",2,0);explanation.setEditable(false);explanation.setLineWrap(true);explanation.setWrapStyleWord(true);explanation.setBackground(getBackground());
        JPanel header=new JPanel(new BorderLayout(0,6));header.add(heading,BorderLayout.NORTH);header.add(balances);header.add(explanation,BorderLayout.SOUTH);add(header,BorderLayout.NORTH);
        table.setAutoCreateRowSorter(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.removeColumn(table.getColumnModel().getColumn(7));
        table.moveColumn(table.convertColumnIndexToView(8),5);table.moveColumn(table.convertColumnIndexToView(3),6);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths={60,105,115,140,115,180,220,0,150,75};for(int column=0;column<table.getColumnCount();column++)table.getColumnModel().getColumn(column).setPreferredWidth(widths[table.convertColumnIndexToModel(column)]);
        try(Statement statement=db.createStatement();ResultSet result=statement.executeQuery("SELECT value FROM ledger_settings WHERE name='color_ledger_rows'")){if(result.next())((BusinessLedger.LedgerTable)table).colorRows=Boolean.parseBoolean(result.getString(1));}
        table.getTableHeader().setToolTipText("Running balances follow original date order: transfers first on each day, then transactions by ID. Sorting and filtering do not change balances.");
        filters=new LedgerFilters(table,false);filters.installShortcut(this);filters.onApplied(this::showSummary);
        JPanel records=new JPanel(new BorderLayout());records.add(filters,BorderLayout.NORTH);records.add(new JScrollPane(table));add(records);
        WrapPanel actions=new WrapPanel();
        JButton refresh=new JButton("Refresh ledger");refresh.addActionListener(e->reloadSafely());actions.add(refresh);
        JButton editButton=new JButton("Edit selected");editButton.addActionListener(e->act(edit));actions.add(editButton);
        JButton historyButton=new JButton("View edit history");historyButton.addActionListener(e->act(history));actions.add(historyButton);
        add(actions,BorderLayout.SOUTH);reload();filters.setDates(start,end);
    }
    private Entry selected(){int view=table.getSelectedRow();if(view<0)throw new IllegalArgumentException("Select a record first.");return data.entries().get(table.convertRowIndexToModel(view));}
    private void act(EntryAction action){try{action.run(selected());reload();}catch(Exception ex){error(ex);}}
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Account ledger",JOptionPane.ERROR_MESSAGE);}
    private void reloadSafely(){try{reload();}catch(SQLException ex){error(ex);}}
    void reload()throws SQLException{
        Data loaded=load(db,accountId);data=loaded;heading.setText(data.name()+" — "+data.type()+(data.active()?"":" (archived)"));heading.setToolTipText(heading.getText());
        rows.setRowCount(0);for(Entry entry:data.entries())rows.addRow(entry.row(data.name()));filters.applyNow();
    }
    private void showSummary(){
        if(data==null)return;
        try{
            LocalDate start=filters.startDate(),end=filters.endDate();Summary value=data.summary(start,end);NumberFormat money=NumberFormat.getCurrencyInstance(Locale.US);
            openingValue.setText((start==null?"Opening balance":("Balance before "+start))+": "+money.format(value.opening()));
            inValue.setText("Period money in: "+money.format(value.in()));outValue.setText("Period money out: "+money.format(value.out()));
            closingValue.setText((end==null?"Recorded balance (all dates)":("Balance as of "+end))+": "+money.format(value.closing()));
        }catch(IllegalArgumentException ex){openingValue.setText("Correct the date filters to display period balances.");inValue.setText("");outValue.setText("");closingValue.setText("");}
    }
    void stopPending(){filters.stopPending();}

    static Data load(Connection db,long accountId)throws SQLException{
        try(PreparedStatement statement=db.prepareStatement("""
            WITH selected AS (SELECT id,name,account_type,active,opening_balance FROM accounts WHERE id=?), events AS (
                SELECT id,entry_date AS day,1 AS event_kind,CASE WHEN kind='Income' THEN amount ELSE -amount END AS delta,
                       kind,category,amount,party,notes FROM transactions WHERE account_id=?
                UNION ALL
                SELECT x.id,x.transfer_date,0,-x.amount,'Transfer Out','Transfer',x.amount,'To: '||a.name,x.notes
                FROM transfers x JOIN accounts a ON a.id=x.to_account_id WHERE x.from_account_id=?
                UNION ALL
                SELECT x.id,x.transfer_date,0,x.amount,'Transfer In','Transfer',x.amount,'From: '||a.name,x.notes
                FROM transfers x JOIN accounts a ON a.id=x.from_account_id WHERE x.to_account_id=?
            ), running AS (
                SELECT events.*,SUM(delta) OVER (ORDER BY day,event_kind,id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS movement FROM events
            )
            SELECT a.id,a.name,a.account_type,a.active,a.opening_balance,r.id,r.day,r.kind,r.category,r.amount,r.party,r.notes,
                   a.opening_balance+r.movement,
                   CASE WHEN (r.event_kind=1 AND EXISTS(SELECT 1 FROM transaction_edits e WHERE e.transaction_id=r.id))
                          OR (r.event_kind=0 AND EXISTS(SELECT 1 FROM transfer_edits e WHERE e.transfer_id=r.id)) THEN 'Edited' ELSE '' END
            FROM selected a LEFT JOIN running r ON TRUE ORDER BY r.day,r.event_kind,r.id
            """)){
            for(int index=1;index<=4;index++)statement.setLong(index,accountId);
            try(ResultSet result=statement.executeQuery()){
                if(!result.next())throw new SQLException("That account no longer exists.");
                String name=result.getString(2),type=result.getString(3);boolean active=result.getBoolean(4);BigDecimal opening=result.getBigDecimal(5);List<Entry> entries=new ArrayList<>();
                do{if(result.getObject(6)!=null)entries.add(new Entry(result.getLong(6),result.getDate(7).toLocalDate(),result.getString(8),result.getString(9),result.getBigDecimal(10),result.getString(11),result.getString(12),result.getBigDecimal(13),result.getString(14)));}while(result.next());
                return new Data(accountId,name,type,active,opening,List.copyOf(entries));
            }
        }
    }
}

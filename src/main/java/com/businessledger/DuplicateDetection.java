package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/** Possible matches only: categories and notes deliberately do not identify a purchase. */
final class DuplicateDetection {
    record Key(LocalDate date,String type,BigDecimal amount,Long account,String payee){
        Key {amount=amount.stripTrailingZeros();payee=payee.strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    }
    record Match(long id,String payee,String category,String notes,String account){}
    static Key key(CsvImport.Entry entry){return new Key(entry.date(),entry.type(),entry.amount(),entry.account(),entry.party());}
    static Map<Key,String> existing(Connection db)throws SQLException{
        Map<Key,String> matches=new HashMap<>();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id,entry_date,kind,amount,account_id,party FROM transactions ORDER BY id")){
            while(r.next())matches.putIfAbsent(new Key(r.getDate(2).toLocalDate(),r.getString(3),r.getBigDecimal(4),(Long)r.getObject(5),r.getString(6)),"Ledger transaction #"+r.getLong(1));
        }
        return matches;
    }
    static List<Match> find(Connection db,Key key)throws SQLException{
        List<Match> matches=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement("SELECT t.id,t.party,t.category,t.notes,COALESCE(a.name,'Unassigned') FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.entry_date=? AND t.kind=? AND t.amount=? AND t.account_id IS NOT DISTINCT FROM CAST(? AS BIGINT) ORDER BY t.id DESC")){
            s.setObject(1,key.date());s.setString(2,key.type());s.setBigDecimal(3,key.amount());s.setObject(4,key.account());
            try(ResultSet r=s.executeQuery()){while(r.next()){
                Key candidate=new Key(key.date(),key.type(),key.amount(),key.account(),r.getString(2));
                if(candidate.equals(key))matches.add(new Match(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)));
            }}
        }
        return List.copyOf(matches);
    }
    static boolean confirm(Component parent,Key key,List<Match> matches){
        if(matches.isEmpty())return true;
        DefaultTableModel rows=new DefaultTableModel(new String[]{"Existing ID","Payee / customer","Category","Notes","Account"},0){@Override public boolean isCellEditable(int row,int col){return false;}};
        for(Match match:matches)rows.addRow(new Object[]{match.id(),match.payee(),match.category(),match.notes(),match.account()});
        JTable table=new JTable(rows);table.setAutoCreateRowSorter(true);JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(740,180));
        JTextArea explanation=new JTextArea(matches.size()+" possible duplicate(s) on "+key.date()+" for $"+key.amount().toPlainString()+" ("+key.type()+").\nDate, amount, account, type and payee match. Category and notes may differ.\nReview the existing entries. Add anyway only if this is a separate transaction.");
        explanation.setEditable(false);explanation.setLineWrap(true);explanation.setWrapStyleWord(true);explanation.setRows(4);
        JPanel content=new JPanel(new BorderLayout(8,8));content.add(explanation,BorderLayout.NORTH);content.add(scroll);
        return JOptionPane.showOptionDialog(parent,content,"Possible duplicate transaction",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,new String[]{"Cancel","Add anyway"},"Cancel")==1;
    }
}

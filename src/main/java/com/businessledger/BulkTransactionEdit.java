package com.businessledger;

import javax.swing.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Date/category/account changes with optimistic preview checks and atomic per-record audits. */
final class BulkTransactionEdit {
    record Row(long id,LocalDate date,String type,BigDecimal amount,String payee,String notes,
               String category,Long accountId,String account,String snapshot){}
    record Change(String category,Long accountId,LocalDate date){
        Change(String category,Long accountId){this(category,accountId,null);}
        LocalDate date(Row row){return date==null?row.date():date;}
        String category(Row row){return category==null?row.category():category;}
        Long accountId(Row row){return accountId==null?row.accountId():accountId;}
        boolean changes(Row row){return !Objects.equals(date(row),row.date())||!Objects.equals(category(row),row.category())||!Objects.equals(accountId(row),row.accountId());}
    }
    record Result(int updated,int unchanged){}
    private static final String SELECT="SELECT t.id,t.entry_date,t.kind,t.amount,t.party,t.notes,t.category,t.account_id,COALESCE(a.name,'Unassigned'),(to_jsonb(t) || jsonb_build_object('account_name',COALESCE(a.name,'Unassigned')))::text FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.id=?";

    static List<Long> selectedIds(JTable table){
        List<Long> ids=new ArrayList<>();
        for(int view:table.getSelectedRows()){
            int row=table.convertRowIndexToModel(view);String type=String.valueOf(table.getModel().getValueAt(row,2));
            if(!type.equals("Income")&&!type.equals("Expense"))throw new IllegalArgumentException("Select only Income and Expense transactions for bulk editing. Manage transfers in Accounts & transfers.");
            ids.add(((Number)table.getModel().getValueAt(row,0)).longValue());
        }
        if(ids.isEmpty())throw new IllegalArgumentException("Select transactions first. Use Ctrl-click or Shift-click to select several rows.");
        return List.copyOf(ids);
    }
    static List<Row> load(Connection db,List<Long> ids)throws SQLException{
        return load(db,ids,false);
    }
    private static List<Row> load(Connection db,List<Long> ids,boolean lock)throws SQLException{
        if(ids.isEmpty())throw new IllegalArgumentException("Select transactions first.");
        TreeSet<Long> ordered=new TreeSet<>(ids);if(ordered.size()!=ids.size())throw new IllegalArgumentException("Select each transaction only once.");
        List<Row> rows=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement(SELECT+(lock?" FOR UPDATE OF t":""))){
            for(long id:ordered){s.setLong(1,id);try(ResultSet r=s.executeQuery()){
                if(!r.next())throw new SQLException("Transaction #"+id+" no longer exists. Refresh and select again.");
                rows.add(new Row(r.getLong(1),r.getDate(2).toLocalDate(),r.getString(3),r.getBigDecimal(4),r.getString(5),r.getString(6),r.getString(7),(Long)r.getObject(8),r.getString(9),r.getString(10)));
            }}
        }
        return List.copyOf(rows);
    }
    static Result save(Connection db,List<Row> reviewed,Change change,String user,String reason)throws SQLException{
        if(change.category()==null&&change.accountId()==null&&change.date()==null)throw new IllegalArgumentException("Choose a date, category or account to change.");
        if(reason==null||reason.isBlank())throw new IllegalArgumentException("Enter a reason for the bulk edit.");
        if(!db.getAutoCommit())throw new SQLException("Finish the current operation before bulk editing.");
        db.setAutoCommit(false);
        try{
            if(change.category()!=null)try(PreparedStatement s=db.prepareStatement("SELECT name FROM categories WHERE name=? AND active FOR SHARE")){
                s.setString(1,change.category());try(ResultSet r=s.executeQuery()){if(!r.next())throw new SQLException("Choose an active category. Nothing changed.");}
            }
            if(change.accountId()!=null)try(PreparedStatement s=db.prepareStatement("SELECT id FROM accounts WHERE id=? AND active FOR SHARE")){
                s.setLong(1,change.accountId());try(ResultSet r=s.executeQuery()){if(!r.next())throw new SQLException("Choose an active account. Nothing changed.");}
            }
            List<Row> current=load(db,reviewed.stream().map(Row::id).toList(),true);
            Map<Long,Row> expected=new HashMap<>();for(Row row:reviewed)expected.put(row.id(),row);
            for(Row row:current)if(!row.snapshot().equals(expected.get(row.id()).snapshot()))
                throw new SQLException("Transaction #"+row.id()+" changed after loading. Nothing changed; refresh and review the selection again.");
            int updated=0;
            try(PreparedStatement update=db.prepareStatement("UPDATE transactions SET category=?,account_id=?,entry_date=? WHERE id=?");
                PreparedStatement audit=db.prepareStatement("INSERT INTO transaction_edits(transaction_id,edited_by,reason,before_record,after_record) SELECT t.id,?,?,CAST(? AS JSONB),to_jsonb(t) || jsonb_build_object('account_name',COALESCE(a.name,'Unassigned')) FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.id=?")){
                for(Row row:current)if(change.changes(row)){
                    update.setString(1,change.category(row));update.setObject(2,change.accountId(row));update.setObject(3,change.date(row));update.setLong(4,row.id());
                    if(update.executeUpdate()!=1)throw new SQLException("Unable to update transaction #"+row.id());
                    audit.setString(1,user);audit.setString(2,reason.strip());audit.setString(3,row.snapshot());audit.setLong(4,row.id());
                    if(audit.executeUpdate()!=1)throw new SQLException("Unable to record edit history for #"+row.id());
                    updated++;
                }
            }
            db.commit();return new Result(updated,current.size()-updated);
        }catch(SQLException|RuntimeException ex){db.rollback();throw ex;}
        finally{db.setAutoCommit(true);}
    }
}

package com.businessledger;

import java.sql.*;
import java.util.*;

/** Retains deleted ledger records and restores original IDs in atomic transactions. */
final class DeletedRecords {
    enum Kind {
        TRANSACTION("transactions","Transaction","entry_date","kind,category,amount,party,notes,account_id"),
        TRANSFER("transfers","Transfer","transfer_date","from_account_id,to_account_id,amount,notes"),
        MILEAGE("mileage","Mileage","trip_date","origin,destination,purpose,miles");
        final String table,label,date,columns;
        Kind(String table,String label,String date,String columns){this.table=table;this.label=label;this.date=date;this.columns=columns;}
        static Kind of(String table){for(Kind kind:values())if(kind.table.equals(table))return kind;throw new IllegalArgumentException("This record type cannot be recovered.");}
    }
    static final String SCHEMA="CREATE TABLE IF NOT EXISTS deleted_records (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,relation TEXT NOT NULL CHECK(relation IN ('transactions','transfers','mileage')),record_id BIGINT NOT NULL,payload JSONB NOT NULL,category TEXT REFERENCES categories(name),account_id BIGINT REFERENCES accounts(id),second_account_id BIGINT REFERENCES accounts(id),deleted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_by TEXT NOT NULL,restored_at TIMESTAMPTZ,restored_by TEXT,CHECK((restored_at IS NULL)=(restored_by IS NULL)))";
    static final String INDEX="CREATE UNIQUE INDEX IF NOT EXISTS deleted_records_pending_idx ON deleted_records(relation,record_id) WHERE restored_at IS NULL";
    record Original(Kind kind,long id,String payload,String category,Long account,Long second){}
    record Item(long id,Kind kind,long recordId,Object deletedAt,String deletedBy,String date,String type,String value,String payee,String category,String account,String second,String notes){}
    record Matches(DuplicateDetection.Key key,List<DuplicateDetection.Match> rows){}
    static Original original(Connection db,Kind kind,long id,boolean lock)throws SQLException{
        String refs=switch(kind){case TRANSACTION->"category,account_id,NULL::bigint";case TRANSFER->"NULL::text,from_account_id,to_account_id";case MILEAGE->"NULL::text,NULL::bigint,NULL::bigint";};
        try(PreparedStatement s=db.prepareStatement("SELECT to_jsonb(t)::text,"+refs+" FROM "+kind.table+" t WHERE id=?"+(lock?" FOR UPDATE":""))){
            s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(!r.next())throw new SQLException("That record no longer exists.");return new Original(kind,id,r.getString(1),r.getString(2),(Long)r.getObject(3),(Long)r.getObject(4));}
        }
    }
    static long delete(Connection db,Original reviewed,String user)throws SQLException{
        idle(db);db.setAutoCommit(false);
        try{
            Original current=original(db,reviewed.kind(),reviewed.id(),true);
            if(!current.payload().equals(reviewed.payload()))throw new SQLException("The record changed while you reviewed it. Nothing deleted; refresh and try again.");
            long recovery;
            try(PreparedStatement s=db.prepareStatement("INSERT INTO deleted_records(relation,record_id,payload,category,account_id,second_account_id,deleted_by) VALUES (?,?,CAST(? AS JSONB),?,?,?,?) RETURNING id")){
                s.setString(1,current.kind().table);s.setLong(2,current.id());s.setString(3,current.payload());s.setString(4,current.category());s.setObject(5,current.account());s.setObject(6,current.second());s.setString(7,user);
                try(ResultSet r=s.executeQuery()){r.next();recovery=r.getLong(1);}
            }
            try(PreparedStatement s=db.prepareStatement("DELETE FROM "+current.kind().table+" WHERE id=?")){s.setLong(1,current.id());if(s.executeUpdate()!=1)throw new SQLException("Nothing deleted.");}
            db.commit();return recovery;
        }catch(SQLException|RuntimeException ex){db.rollback();throw ex;}finally{db.setAutoCommit(true);}
    }
    static List<Item> list(Connection db)throws SQLException{
        List<Item> items=new ArrayList<>();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT d.id,d.relation,d.record_id,d.deleted_at,d.deleted_by,COALESCE(payload->>'entry_date',payload->>'transfer_date',payload->>'trip_date'),COALESCE(payload->>'kind',CASE WHEN relation='transfers' THEN 'Transfer' ELSE 'Mileage' END),COALESCE(payload->>'amount',payload->>'miles'),COALESCE(payload->>'party',payload->>'purpose',''),COALESCE(d.category,''),COALESCE(a.name,'Unassigned'),COALESCE(b.name,''),COALESCE(payload->>'notes',concat_ws(' -> ',payload->>'origin',payload->>'destination')) FROM deleted_records d LEFT JOIN accounts a ON a.id=d.account_id LEFT JOIN accounts b ON b.id=d.second_account_id WHERE restored_at IS NULL ORDER BY deleted_at DESC,d.id DESC")){
            while(r.next())items.add(new Item(r.getLong(1),Kind.of(r.getString(2)),r.getLong(3),r.getObject(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),r.getString(13)));
        }return List.copyOf(items);
    }
    static Matches matches(Connection db,long recovery)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("SELECT relation,(payload->>'entry_date')::date,payload->>'kind',(payload->>'amount')::numeric,account_id,payload->>'party' FROM deleted_records WHERE id=? AND restored_at IS NULL")){
            s.setLong(1,recovery);try(ResultSet r=s.executeQuery()){
                if(!r.next())throw new SQLException("This entry was already restored or no longer exists.");
                if(!r.getString(1).equals("transactions"))return new Matches(null,List.of());
                var key=new DuplicateDetection.Key(r.getDate(2).toLocalDate(),r.getString(3),r.getBigDecimal(4),(Long)r.getObject(5),r.getString(6));
                return new Matches(key,DuplicateDetection.find(db,key));
            }
        }
    }
    static void restore(Connection db,long recovery,String user,Set<Long> approvedMatches)throws SQLException{
        idle(db);approvedMatches=Set.copyOf(approvedMatches);db.setAutoCommit(false);
        try{
            Kind kind;long id;
            try(PreparedStatement s=db.prepareStatement("SELECT relation,record_id,restored_at FROM deleted_records WHERE id=? FOR UPDATE")){
                s.setLong(1,recovery);try(ResultSet r=s.executeQuery()){if(!r.next()||r.getObject(3)!=null)throw new SQLException("This entry was already restored or no longer exists.");kind=Kind.of(r.getString(1));id=r.getLong(2);}
            }
            if(kind==Kind.TRANSACTION){
                try(Statement s=db.createStatement()){s.execute("LOCK TABLE transactions IN SHARE ROW EXCLUSIVE MODE");}
                for(var match:matches(db,recovery).rows())if(!approvedMatches.contains(match.id()))throw new SQLException("A matching transaction exists. Nothing restored; review the duplicate warning and try again.");
            }
            try(PreparedStatement s=db.prepareStatement("SELECT 1 FROM "+kind.table+" WHERE id=?")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(r.next())throw new SQLException("Record ID "+id+" is already in use. Nothing overwritten or restored.");}}
            String columns=kind.date+","+kind.columns;List<String> values=new ArrayList<>();
            for(String column:columns.split(","))values.add(switch(column){
                case "category"->"d.category";case "account_id","from_account_id"->"d.account_id";case "to_account_id"->"d.second_account_id";
                default->"(jsonb_populate_record(NULL::"+kind.table+",d.payload))."+column;
            });
            try(PreparedStatement s=db.prepareStatement("INSERT INTO "+kind.table+"(id,"+columns+") OVERRIDING SYSTEM VALUE SELECT d.record_id,"+String.join(",",values)+" FROM deleted_records d WHERE d.id=? AND restored_at IS NULL")){
                s.setLong(1,recovery);if(s.executeUpdate()!=1)throw new SQLException("Unable to restore this record.");
            }
            try(PreparedStatement s=db.prepareStatement("UPDATE deleted_records SET restored_at=CURRENT_TIMESTAMP,restored_by=? WHERE id=? AND restored_at IS NULL")){
                s.setString(1,user);s.setLong(2,recovery);if(s.executeUpdate()!=1)throw new SQLException("Unable to record restoration.");
            }
            db.commit();
        }catch(SQLException|RuntimeException ex){db.rollback();throw ex;}finally{db.setAutoCommit(true);}
    }
    private static void idle(Connection db)throws SQLException{if(!db.getAutoCommit())throw new SQLException("Finish the current operation first.");}
}

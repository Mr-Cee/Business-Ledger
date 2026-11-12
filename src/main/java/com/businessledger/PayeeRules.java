package com.businessledger;

import java.sql.*;
import java.util.*;

/** Profile-local preferences and history; reading a suggestion never edits a transaction. */
final class PayeeRules {
    static final String SCHEMA = "CREATE TABLE IF NOT EXISTS payee_rules (payee_key TEXT NOT NULL CHECK(length(payee_key)>0), kind TEXT NOT NULL CHECK(kind IN ('Income','Expense')), payee TEXT NOT NULL, category TEXT NOT NULL REFERENCES categories(name), account_id BIGINT NOT NULL REFERENCES accounts(id), PRIMARY KEY(payee_key,kind))";
    record Key(String payee,String kind){}
    record Suggestion(String payee,String kind,String category,long accountId,String accountName,boolean saved,boolean available){
        @Override public String toString(){return payee+" ("+kind+"): "+category+" / "+accountName+(available?"":" (unavailable)");}
    }
    record Snapshot(Map<Key,Suggestion> rules,Map<Key,Suggestion> history){
        Suggestion find(String payee,String kind){String name=normalize(payee);if(name.isEmpty())return null;Key key=new Key(name,kind);return rules.containsKey(key)?rules.get(key):history.get(key);}
    }
    static String normalize(String value){return value.strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    static Snapshot load(Connection db)throws SQLException{
        Map<Key,Suggestion> rules=new LinkedHashMap<>(),history=new LinkedHashMap<>();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT p.payee,p.kind,p.category,p.account_id,a.name,(a.active AND c.active) FROM payee_rules p JOIN accounts a ON a.id=p.account_id JOIN categories c ON c.name=p.category ORDER BY p.payee,p.kind")){
            while(r.next()){Suggestion v=row(r,true);rules.put(new Key(normalize(v.payee()),v.kind()),v);}
        }
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT t.party,t.kind,t.category,t.account_id,a.name,TRUE FROM transactions t JOIN accounts a ON a.id=t.account_id JOIN categories c ON c.name=t.category WHERE a.active AND c.active AND length(trim(t.party))>0 ORDER BY t.entry_date DESC,t.id DESC")){
            while(r.next()){Suggestion v=row(r,false);history.putIfAbsent(new Key(normalize(v.payee()),v.kind()),v);}
        }
        return new Snapshot(Collections.unmodifiableMap(rules),Collections.unmodifiableMap(history));
    }
    private static Suggestion row(ResultSet r,boolean saved)throws SQLException{return new Suggestion(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),saved,r.getBoolean(6));}
    static void save(Connection db,String payee,String kind,String category,long account)throws SQLException{
        if(normalize(payee).isEmpty())throw new IllegalArgumentException("Enter a payee / customer first.");
        if(!Set.of("Income","Expense").contains(kind))throw new IllegalArgumentException("Choose Income or Expense.");
        try(PreparedStatement s=db.prepareStatement("INSERT INTO payee_rules(payee_key,kind,payee,category,account_id) SELECT ?,?,?,c.name,a.id FROM categories c CROSS JOIN accounts a WHERE c.name=? AND a.id=? AND c.active AND a.active ON CONFLICT(payee_key,kind) DO UPDATE SET payee=EXCLUDED.payee,category=EXCLUDED.category,account_id=EXCLUDED.account_id")){
            s.setString(1,normalize(payee));s.setString(2,kind);s.setString(3,payee.strip());s.setString(4,category);s.setLong(5,account);
            if(s.executeUpdate()!=1)throw new SQLException("Choose an active category and account before saving this rule.");
        }
    }
    static void remove(Connection db,Suggestion rule)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("DELETE FROM payee_rules WHERE payee_key=? AND kind=?")){s.setString(1,normalize(rule.payee()));s.setString(2,rule.kind());s.executeUpdate();}
    }
}

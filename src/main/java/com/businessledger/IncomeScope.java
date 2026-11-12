package com.businessledger;

import java.sql.*;

/** Per-ledger income analysis filter; never used for account balances. */
record IncomeScope(String category) {
    static final String KEY="income_analysis_category";
    static IncomeScope load(Connection db)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("SELECT value FROM ledger_settings WHERE name=?")){
            s.setString(1,KEY);try(ResultSet r=s.executeQuery()){return new IncomeScope(r.next()?r.getString(1):null);}
        }
    }
    void save(Connection db)throws SQLException{
        if(category==null){
            try(PreparedStatement s=db.prepareStatement("DELETE FROM ledger_settings WHERE name=?")){s.setString(1,KEY);s.executeUpdate();}
        }else{
            try(PreparedStatement s=db.prepareStatement("INSERT INTO ledger_settings(name,value) SELECT ?,name FROM categories WHERE name=? ON CONFLICT(name) DO UPDATE SET value=EXCLUDED.value")){
                s.setString(1,KEY);s.setString(2,category);if(s.executeUpdate()!=1)throw new IllegalArgumentException("Choose an existing category.");
            }
        }
    }
    String sql(){return category==null?"":" AND (kind<>'Income' OR category=?)";}
    String description(){return category==null?"Income: all categories":"Income category: "+category;}
}

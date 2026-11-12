package com.businessledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;

/** A user-defined percentage of calendar-year income, not a tax calculation engine. */
final class TaxEstimate {
    static final String KEY="estimated_tax_rate_percent";
    record Estimate(int year,BigDecimal income,BigDecimal rate,BigDecimal amount){}
    static BigDecimal parseRate(String text){
        try{
            BigDecimal rate=new BigDecimal(text.trim()).setScale(2,RoundingMode.UNNECESSARY);
            if(rate.signum()<0||rate.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException();
            return rate;
        }catch(IllegalArgumentException|ArithmeticException ex){throw new IllegalArgumentException("Enter a tax percentage from 0 to 100 with at most two decimal places.");}
    }
    static BigDecimal rate(Connection db)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("SELECT value FROM ledger_settings WHERE name=?")){
            s.setString(1,KEY);try(ResultSet r=s.executeQuery()){return parseRate(r.next()?r.getString(1):"24");}
        }
    }
    static void saveRate(Connection db,BigDecimal rate)throws SQLException{
        BigDecimal checked=parseRate(rate.toPlainString());
        try(PreparedStatement s=db.prepareStatement("INSERT INTO ledger_settings(name,value) VALUES (?,?) ON CONFLICT(name) DO UPDATE SET value=EXCLUDED.value")){
            s.setString(1,KEY);s.setString(2,checked.toPlainString());s.executeUpdate();
        }
    }
    static Estimate load(Connection db,LocalDate today)throws SQLException{
        IncomeScope scope=IncomeScope.load(db);BigDecimal income;
        try(PreparedStatement s=db.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE kind='Income' AND entry_date BETWEEN ? AND ?"+scope.sql())){
            s.setObject(1,LocalDate.of(today.getYear(),1,1));s.setObject(2,LocalDate.of(today.getYear(),12,31));
            if(scope.category()!=null)s.setString(3,scope.category());
            try(ResultSet r=s.executeQuery()){r.next();income=r.getBigDecimal(1);}
        }
        BigDecimal rate=rate(db),amount=income.multiply(rate).movePointLeft(2).setScale(2,RoundingMode.HALF_UP);
        return new Estimate(today.getYear(),income,rate,amount);
    }
}

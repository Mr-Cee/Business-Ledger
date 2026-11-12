package com.businessledger;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public final class CsvImportCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static CsvImport.Options options(CsvImport.Mapping mapping,CsvImport.AmountMode mode,long account,boolean skip){return new CsvImport.Options(mapping,"M/d/uuuu",mode,"Other",account,true,skip);}
    public static void main(String[] args)throws Exception {
        List<List<String>> parsed=CsvImport.parse("Date,Payee,Notes,Amount\r\n11/1/2026,\"Shop, Inc.\",\"A \"\"quoted\"\" note\nsecond line\",-12.34\r\n",',');
        check(parsed.size()==2 && parsed.get(1).get(1).equals("Shop, Inc.") && parsed.get(1).get(2).equals("A \"quoted\" note\nsecond line"),"Quoted commas, escaped quotes, multiline values, CRLF");
        boolean rejected=false;try{CsvImport.parse("a,\"unclosed",',');}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"Unclosed quotes rejected");
        check(CsvImport.money("($1,234.56)").compareTo(new BigDecimal("-1234.56"))==0,"Currency grouping and parentheses");
        rejected=false;try{CsvImport.money("1,2.34");}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"Malformed grouping rejected");
        if(args.length!=1 || !args[0].contains(":55439/"))throw new IllegalArgumentException("Use fresh test database on port 55439.");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Statement s=db.createStatement()){
            s.executeUpdate("CREATE TABLE categories(name TEXT PRIMARY KEY,active BOOLEAN DEFAULT TRUE)");
            s.executeUpdate("CREATE TABLE accounts(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,name TEXT,active BOOLEAN DEFAULT TRUE)");
            s.executeUpdate("CREATE TABLE transactions(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,entry_date DATE NOT NULL,kind TEXT NOT NULL CHECK(kind IN ('Income','Expense')),category TEXT NOT NULL REFERENCES categories(name),amount NUMERIC(14,2) NOT NULL CHECK(amount>0),party TEXT NOT NULL,notes TEXT NOT NULL,account_id BIGINT NOT NULL REFERENCES accounts(id))");
            s.executeUpdate("INSERT INTO categories(name) VALUES ('Other'),('Food')");s.executeUpdate("INSERT INTO accounts(name) VALUES ('Checking'),('Savings')");
            CsvImport.Mapping mapping=new CsvImport.Mapping(0,-1,3,2,-1,-1,1,-1,-1);
            CsvImport.Table table=new CsvImport.Table(List.of("Date","Description","Amount","Category","Discard"),List.of(List.of("11/1/2026","Shop, Inc.","-12.34","Food","ignored secret"),List.of("11/2/2026","Client","125.00","New category","extra"),List.of("11/1/2026","Shop, Inc.","-12.34","food","duplicate")),2);
            CsvImport.Preview preview=CsvImport.preview(db,table,options(mapping,CsvImport.AmountMode.SIGNED,1,true));
            check(preview.errorCount()==0 && preview.entries().size()==3 && preview.duplicates()==1 && preview.newCategories().contains("New category"),"Mappings, defaults, signed amount, category creation, duplicate preview");
            try(ResultSet r=s.executeQuery("SELECT count(*) FROM transactions")){r.next();check(r.getInt(1)==0,"Preview writes nothing");}
            CsvImport.Result result=CsvImport.commit(db,preview);check(result.imported()==2 && result.skipped()==1,"Atomic import skips in-file duplicates");
            result=CsvImport.commit(db,preview);check(result.imported()==0 && result.skipped()==3,"Re-import skips database duplicates");
            CsvImport.Options keep=options(mapping,CsvImport.AmountMode.SIGNED,1,false);result=CsvImport.commit(db,CsvImport.preview(db,table,keep));check(result.imported()==3,"User may intentionally retain identical transactions");
            try(ResultSet r=s.executeQuery("SELECT kind,amount,party,notes FROM transactions ORDER BY id LIMIT 1")){r.next();check(r.getString(1).equals("Expense") && r.getBigDecimal(2).compareTo(new BigDecimal("12.34"))==0 && r.getString(3).equals("Shop, Inc.") && r.getString(4).isEmpty(),"Only mapped values saved with positive amount");}
            CsvImport.Mapping split=new CsvImport.Mapping(0,-1,-1,-1,1,2,-1,3,-1);
            CsvImport.Table debitCredit=new CsvImport.Table(List.of("date","debit","credit","memo"),List.of(List.of("11/3/2026","20.10","","Debit note"),List.of("11/4/2026","","30.20","Credit note")),2);
            CsvImport.Preview splitPreview=CsvImport.preview(db,debitCredit,options(split,CsvImport.AmountMode.DEBIT_CREDIT,2,true));
            check(splitPreview.errorCount()==0 && splitPreview.entries().get(0).type().equals("Expense") && splitPreview.entries().get(1).type().equals("Income"),"Separate debit and credit mapping");
            CsvImport.Table bad=new CsvImport.Table(table.headers(),List.of(List.of("2/30/2026","Shop","-1","Food","extra"),List.of("11/1/2026","Shop","0","Food","extra"),List.of("11/1/2026","Shop")),2);
            check(CsvImport.preview(db,bad,options(mapping,CsvImport.AmountMode.SIGNED,1,true)).errorCount()==3,"Invalid dates, zero amounts, row widths reported before import");
            // Simulate an account being archived after preview. The whole import must fail.
            s.executeUpdate("UPDATE accounts SET active=FALSE WHERE id=2");
            rejected=false;try{CsvImport.commit(db,splitPreview);}catch(SQLException ex){rejected=true;}check(rejected,"Stale account prevents import");
            try(ResultSet r=s.executeQuery("SELECT count(*) FROM transactions")){r.next();check(r.getInt(1)==5,"Failed import leaves no partial transactions");}
            // Force an error after the first queued insert and a new category; both must roll back.
            s.executeUpdate("ALTER TABLE transactions ADD CONSTRAINT block_client CHECK(party<>'Client') NOT VALID");
            CsvImport.Table fail=new CsvImport.Table(table.headers(),List.of(List.of("11/5/2026","Allowed","-1","Rollback category",""),List.of("11/6/2026","Client","2","Rollback category","")),2);
            rejected=false;try{CsvImport.commit(db,CsvImport.preview(db,fail,options(mapping,CsvImport.AmountMode.SIGNED,1,false)));}catch(SQLException ex){rejected=true;}check(rejected,"Database failure rejects complete batch");
            try(ResultSet r=s.executeQuery("SELECT (SELECT count(*) FROM transactions),(SELECT count(*) FROM categories WHERE name='Rollback category')")){r.next();check(r.getInt(1)==5 && r.getInt(2)==0,"Rows and new categories roll back together");}
            System.out.println("CSV parsing, mapping, validation, duplicate, debit/credit, and rollback checks passed.");
        }
    }
}

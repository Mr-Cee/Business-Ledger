package com.businessledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.*;
import java.util.*;

/** Optional integration URL must name a fresh disposable database on port 55439. */
public final class DuplicateDetectionCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static DuplicateDetection.Key key(String date,String kind,String amount,Long account,String party){return new DuplicateDetection.Key(LocalDate.parse(date),kind,new BigDecimal(amount),account,party);}
    static CsvImport.Options options(boolean skip){return new CsvImport.Options(new CsvImport.Mapping(0,1,2,3,-1,-1,4,5,-1),"uuuu-MM-dd",CsvImport.AmountMode.SIGNED,"Other",1,true,skip);}
    static CsvImport.Table table(List<List<String>> rows){return new CsvImport.Table(List.of("Date","Type","Category","Amount","Payee","Notes"),rows,2);}
    static long count(Connection db)throws Exception{try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM transactions")){r.next();return r.getLong(1);}}
    public static void main(String[] args)throws Exception{
        var k=key("2026-11-01","Expense","10.00",1L,"  ACME\t Shop ");
        check(k.equals(key("2026-11-01","Expense","10.0",1L,"acme shop")),"Case, whitespace and money scale normalize");
        check(!k.equals(key("2026-11-02","Expense","10",1L,"acme shop")),"Date distinguishes matches");
        check(!k.equals(key("2026-11-01","Income","10",1L,"acme shop")),"Type distinguishes matches");
        check(!k.equals(key("2026-11-01","Expense","10.01",1L,"acme shop")),"Amount distinguishes matches");
        check(!k.equals(key("2026-11-01","Expense","10",2L,"acme shop")),"Account distinguishes matches");
        check(!k.equals(key("2026-11-01","Expense","10",1L,"acme")),"Payees are full-name matches");
        check(!k.equals(key("2026-11-01","Expense","10",null,"acme shop")),"Unassigned account is distinct");
        List<CsvImport.Entry> entries=new ArrayList<>();List<String> reasons=new ArrayList<>();
        for(int i=0;i<205;i++){entries.add(new CsvImport.Entry(LocalDate.of(2026,11,1),"Expense","Other",new BigDecimal("10"),"Acme Shop","note "+i,1));reasons.add(i==0?"":"Earlier CSV record #2");}
        var preview=new CsvImport.Preview(List.copyOf(entries),List.of(),0,204,Set.of(),options(true),List.copyOf(reasons));
        var model=CsvImportWizard.duplicateReviewRows(preview,Set.of(204),Map.of(1L,"Checking"));
        check(model.getRowCount()==204,"Review includes duplicates beyond the first 200 preview rows");
        check(model.getValueAt(203,0).equals(205)&&Boolean.TRUE.equals(model.getValueAt(203,7)),"Keep retains original preview-row identity");
        check(model.getColumnClass(7)==Boolean.class&&model.isCellEditable(0,7)&&!model.isCellEditable(0,3),"Only Keep is editable");
        if(args.length==0){System.out.println("Duplicate keys, full-file review, Keep row identities and editable-column checks passed; database integration requires disposable 55439 URL.");return;}
        if(args.length!=1||!args[0].startsWith("jdbc:postgresql://127.0.0.1:55439/"))throw new IllegalArgumentException("Fresh database on 55439 required");
        try(Connection db=DriverManager.getConnection(args[0],"ledger_test","");Statement s=db.createStatement()){
            s.executeUpdate("CREATE TABLE categories(name TEXT PRIMARY KEY,active BOOLEAN DEFAULT TRUE)");
            s.executeUpdate("CREATE TABLE accounts(id BIGINT PRIMARY KEY,name TEXT,active BOOLEAN DEFAULT TRUE)");
            s.executeUpdate("CREATE TABLE transactions(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,entry_date DATE,kind TEXT,category TEXT REFERENCES categories(name),amount NUMERIC(14,2),party TEXT,notes TEXT,account_id BIGINT REFERENCES accounts(id))");
            s.executeUpdate("INSERT INTO categories(name) VALUES ('Other'),('Supplies')");s.executeUpdate("INSERT INTO accounts VALUES(1,'Checking',TRUE),(2,'Savings',TRUE)");
            s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount,party,notes,account_id) VALUES ('2026-11-01','Expense','Other',10,'Acme Shop','original',1),('2026-11-01','Income','Other',10,'Acme Shop','income',1),('2026-11-01','Expense','Other',10,'Acme Shop','other account',2)");
            var matches=DuplicateDetection.find(db,k);check(matches.size()==1&&matches.getFirst().notes().equals("original"),"Manual matching ignores category/notes and distinguishes type/account");
            var p=CsvImport.preview(db,table(List.of(List.of("2026-11-01","Expense","Supplies","10.00"," acme  SHOP ","different"),List.of("2026-11-02","Expense","Other","20","New Shop","one"),List.of("2026-11-02","Expense","Supplies","20.0","NEW SHOP","two"))),options(true));
            check(p.duplicates()==2&&p.duplicateReasons().get(0).startsWith("Ledger transaction #")&&p.duplicateReasons().get(2).equals("Earlier CSV record #3"),"Existing and within-file match reasons");
            check(count(db)==3,"Preview does not write");
            var result=CsvImport.commit(db,p,Set.of(0));check(result.imported()==2&&result.skipped()==1&&count(db)==5,"Keep one legitimate repeat and skip another");
            var next=CsvImport.preview(db,table(List.of(List.of("2026-11-03","Expense","Other","30","Race shop","new"))),options(true));
            s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount,party,notes,account_id) VALUES ('2026-11-03','Expense','Supplies',30,'RACE SHOP','concurrent',1)");
            check(CsvImport.commit(db,next).skipped()==1&&count(db)==6,"Commit rechecks matches that appeared after preview");
            check(CsvImport.commit(db,p,Set.of(0)).imported()==1,"Explicit Keep can retain a reviewed repeated entry");
            check(CsvImport.commit(db,CsvImport.preview(db,table(List.of(List.of("2026-11-01","Expense","Other","10","Acme Shop","intentional repeat"))),options(false))).imported()==1,"Skip disabled allows deliberate repeat");
            long before=count(db);s.executeUpdate("ALTER TABLE transactions ADD CONSTRAINT fail_test CHECK(notes<>'FAIL') NOT VALID");
            var failing=CsvImport.preview(db,table(List.of(List.of("2026-11-04","Expense","New category","1","First","ok"),List.of("2026-11-05","Expense","New category","2","Second","FAIL"))),options(true));
            boolean rejected=false;try{CsvImport.commit(db,failing);}catch(SQLException expected){rejected=true;}check(rejected&&count(db)==before,"Failed imports roll back all rows");
            try(ResultSet r=s.executeQuery("SELECT count(*) FROM categories WHERE name='New category'")){r.next();check(r.getInt(1)==0,"Category creation rolls back with failed import");}
            System.out.println("Manual matching, CSV matching/Keep, post-preview recheck, explicit repeat and atomic rollback checks passed.");
        }
    }
}

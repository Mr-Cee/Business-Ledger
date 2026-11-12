package com.businessledger;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.*;
import java.util.*;

/** CSV parsing, validation, and atomic import; no UI and no changes during preview. */
final class CsvImport {
    record Table(List<String> headers,List<List<String>> rows,int firstRecord) {}
    record Mapping(int date,int type,int category,int amount,int debit,int credit,int party,int notes,int account) {}
    enum AmountMode { SIGNED, REVERSED, EXPENSE, INCOME, DEBIT_CREDIT }
    record Options(Mapping mapping,String dateFormat,AmountMode mode,String category,long account,boolean createCategories,boolean skipDuplicates) {}
    record Entry(LocalDate date,String type,String category,BigDecimal amount,String party,String notes,long account) {}
    record Preview(List<Entry> entries,List<String> errors,int errorCount,int duplicates,Set<String> newCategories,Options options) {}
    record Result(int imported,int skipped) {}

    static Table read(Path path,Charset charset,char delimiter,boolean header)throws IOException {
        if(Files.size(path)>20L*1024*1024)throw new IllegalArgumentException("Choose a CSV file of 20 MB or less.");
        String text=Files.readString(path,charset);if(text.startsWith("\uFEFF"))text=text.substring(1);
        List<List<String>> records=parse(text,delimiter);
        if(records.isEmpty())throw new IllegalArgumentException("The CSV file is empty.");
        List<String> headings=new ArrayList<>();int width=records.get(0).size();
        for(int col=0;col<width;col++)headings.add(header?records.get(0).get(col).trim():"Column "+(col+1));
        List<List<String>> rows=header?records.subList(1,records.size()):records;
        if(rows.size()>50000)throw new IllegalArgumentException("Import up to 50,000 CSV rows at a time.");
        if(rows.isEmpty())throw new IllegalArgumentException("The CSV contains no transaction rows.");
        return new Table(List.copyOf(headings),List.copyOf(rows),header?2:1);
    }
    static List<List<String>> parse(String text,char delimiter) {
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();
        boolean quoted=false,closed=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(quoted) {
                if(c=='"') {if(i+1<text.length() && text.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}
                else cell.append(c);
                continue;
            }
            if(c==delimiter){row.add(cell.toString());cell.setLength(0);closed=false;}
            else if(c=='\r' || c=='\n') {
                if(c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n')i++;
                row.add(cell.toString());rows.add(List.copyOf(row));row.clear();cell.setLength(0);closed=false;
                if(rows.size()>50001)throw new IllegalArgumentException("Import up to 50,000 CSV rows at a time.");
            } else if(c=='"') {
                if(cell.length()!=0 || closed)throw new IllegalArgumentException("Unexpected quote in CSV record "+(rows.size()+1)+".");
                quoted=true;
            } else {
                if(closed)throw new IllegalArgumentException("Unexpected text after a closing quote in CSV record "+(rows.size()+1)+".");
                cell.append(c);
            }
        }
        if(quoted)throw new IllegalArgumentException("An enclosed CSV value is missing its closing quote.");
        if(cell.length()>0 || !row.isEmpty() || closed){row.add(cell.toString());rows.add(List.copyOf(row));}
        return rows;
    }
    private static String value(List<String> row,int column){return column<0?"":row.get(column).trim();}
    private static String canonical(String input,Collection<String> candidates,String label) {
        if(candidates.contains(input))return input;
        List<String> matches=candidates.stream().filter(name->name.equalsIgnoreCase(input)).toList();
        if(matches.size()>1)throw new IllegalArgumentException("Ambiguous "+label+": "+input+". Use its exact spelling.");
        return matches.isEmpty()?null:matches.get(0);
    }
    static BigDecimal money(String text) {
        String cleaned=text.trim();
        if(cleaned.startsWith("(") && cleaned.endsWith(")"))cleaned="-"+cleaned.substring(1,cleaned.length()-1);
        if(cleaned.startsWith("$"))cleaned=cleaned.substring(1);
        else if(cleaned.startsWith("-$") || cleaned.startsWith("+$"))cleaned=cleaned.substring(0,1)+cleaned.substring(2);
        if(!cleaned.matches("[+-]?(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d{1,2})?"))throw new IllegalArgumentException("Invalid dollar amount: "+text);
        BigDecimal amount=new BigDecimal(cleaned.replace(",","")).setScale(2);
        if(amount.abs().compareTo(new BigDecimal("999999999999.99"))>0)throw new IllegalArgumentException("Amount is too large.");
        return amount;
    }
    private static String type(String raw) {
        return switch(raw.toLowerCase(Locale.ROOT)) {
            case "income","credit","deposit","in" -> "Income";
            case "expense","debit","withdrawal","out" -> "Expense";
            default -> throw new IllegalArgumentException("Type must be Income/Expense (or Credit/Debit), not '"+raw+"'. Record account transfers using Accounts & transfers.");
        };
    }
    static Set<Entry> existing(Connection db)throws SQLException {
        Set<Entry> entries=new HashSet<>();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT entry_date,kind,category,amount,party,notes,account_id FROM transactions WHERE account_id IS NOT NULL")) {
            while(r.next())entries.add(new Entry(r.getDate(1).toLocalDate(),r.getString(2),r.getString(3),r.getBigDecimal(4).setScale(2),r.getString(5),r.getString(6),r.getLong(7)));
        }return entries;
    }
    static Preview preview(Connection db,Table table,Options options)throws SQLException {
        Mapping m=options.mapping();
        if(m.date()<0)throw new IllegalArgumentException("Map the transaction Date column.");
        if(options.mode()==AmountMode.DEBIT_CREDIT && (m.debit()<0 || m.credit()<0))throw new IllegalArgumentException("Map both Debit and Credit columns for this amount mode.");
        if(options.mode()!=AmountMode.DEBIT_CREDIT && m.amount()<0)throw new IllegalArgumentException("Map the Amount column.");
        Set<Integer> columns=new HashSet<>();
        for(int column:new int[]{m.date(),m.type(),m.category(),m.party(),m.notes(),m.account(),options.mode()==AmountMode.DEBIT_CREDIT?m.debit():m.amount(),options.mode()==AmountMode.DEBIT_CREDIT?m.credit():-1})
            if(column>=0 && !columns.add(column))throw new IllegalArgumentException("Map each CSV column to only one ledger field.");
        DateTimeFormatter dates=DateTimeFormatter.ofPattern(options.dateFormat(),Locale.US).withResolverStyle(ResolverStyle.STRICT);
        Set<String> categories=new HashSet<>();Map<String,Long> accounts=new HashMap<>();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT name FROM categories")){while(r.next())categories.add(r.getString(1));}
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT name,id FROM accounts WHERE active")){while(r.next())accounts.put(r.getString(1),r.getLong(2));}
        Set<Entry> seen=existing(db);List<Entry> entries=new ArrayList<>();List<String> errors=new ArrayList<>();Set<String> newCategories=new TreeSet<>();int duplicates=0,errorCount=0;
        for(int index=0;index<table.rows().size();index++) {
            List<String> row=table.rows().get(index);if(row.stream().allMatch(String::isBlank))continue;
            try {
                if(row.size()!=table.headers().size())throw new IllegalArgumentException("Expected "+table.headers().size()+" columns, found "+row.size()+". Check the delimiter or quotes.");
                LocalDate date=LocalDate.parse(value(row,m.date()),dates);
                String kind;BigDecimal amount;
                if(options.mode()==AmountMode.DEBIT_CREDIT) {
                    BigDecimal debit=value(row,m.debit()).isEmpty()?BigDecimal.ZERO:money(value(row,m.debit()));
                    BigDecimal credit=value(row,m.credit()).isEmpty()?BigDecimal.ZERO:money(value(row,m.credit()));
                    if(debit.signum()<0 || credit.signum()<0 || (debit.signum()>0)==(credit.signum()>0))throw new IllegalArgumentException("Exactly one of Debit and Credit must be positive; the other must be blank or zero.");
                    kind=debit.signum()>0?"Expense":"Income";amount=debit.add(credit).setScale(2);
                    if(m.type()>=0 && !value(row,m.type()).isEmpty() && !type(value(row,m.type())).equals(kind))throw new IllegalArgumentException("Type conflicts with Debit/Credit columns.");
                } else {
                    BigDecimal signed=money(value(row,m.amount()));amount=signed.abs();
                    if(m.type()>=0 && !value(row,m.type()).isEmpty())kind=type(value(row,m.type()));
                    else kind=switch(options.mode()) {case EXPENSE->"Expense";case INCOME->"Income";case REVERSED->signed.signum()>0?"Expense":"Income";default->signed.signum()>0?"Income":"Expense";};
                }
                if(amount.signum()==0)throw new IllegalArgumentException("Zero-value transactions are not supported.");
                String rawCategory=value(row,m.category());if(rawCategory.isEmpty())rawCategory=options.category();
                String category=canonical(rawCategory,categories,"category");
                if(category==null) {
                    if(!options.createCategories())throw new IllegalArgumentException("Unknown category: "+rawCategory+". Enable creation of missing categories, or use a default category.");
                    if(rawCategory.isBlank())throw new IllegalArgumentException("Choose a default category.");
                    category=rawCategory;categories.add(category);newCategories.add(category);
                }
                long account=options.account();String rawAccount=value(row,m.account());
                if(!rawAccount.isEmpty()) {
                    String accountName=canonical(rawAccount,accounts.keySet(),"account");
                    if(accountName==null)throw new IllegalArgumentException("Unknown or archived account: "+rawAccount+". Create/restore it first, or use a default account.");
                    account=accounts.get(accountName);
                }
                if(!accounts.containsValue(account))throw new IllegalArgumentException("Choose an active default account.");
                Entry entry=new Entry(date,kind,category,amount,value(row,m.party()),value(row,m.notes()),account);
                if(!seen.add(entry))duplicates++;
                entries.add(entry);
            }catch(IllegalArgumentException | java.time.DateTimeException ex) {
                errorCount++;if(errors.size()<50)errors.add("CSV record "+(index+table.firstRecord())+": "+ex.getMessage());
            }
        }
        return new Preview(List.copyOf(entries),List.copyOf(errors),errorCount,duplicates,Set.copyOf(newCategories),options);
    }
    static Result commit(Connection db,Preview preview)throws SQLException {
        if(preview.errorCount()>0 || preview.entries().isEmpty())throw new IllegalArgumentException("Validate a nonempty file without errors before importing.");
        db.setAutoCommit(false);
        try {
            // Serialize imports with transaction writes, so duplicate checks cannot race.
            try(Statement s=db.createStatement()){s.execute("LOCK TABLE transactions IN SHARE ROW EXCLUSIVE MODE");}
            Set<Long> active=new HashSet<>();
            try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id FROM accounts WHERE active FOR SHARE")){while(r.next())active.add(r.getLong(1));}
            Set<Entry> seen=existing(db);int imported=0,skipped=0;
            try(PreparedStatement category=db.prepareStatement("INSERT INTO categories(name) VALUES (?) ON CONFLICT DO NOTHING");
                PreparedStatement insert=db.prepareStatement("INSERT INTO transactions(entry_date,kind,category,amount,party,notes,account_id) VALUES (?,?,?,?,?,?,?)")) {
                Set<String> created=new HashSet<>();
                for(Entry entry:preview.entries()) {
                    boolean duplicate=!seen.add(entry);
                    if(duplicate && preview.options().skipDuplicates()){skipped++;continue;}
                    if(!active.contains(entry.account()))throw new SQLException("An import account is no longer active. Nothing was imported.");
                    if(preview.newCategories().contains(entry.category()) && created.add(entry.category())){category.setString(1,entry.category());category.executeUpdate();}
                    insert.setObject(1,entry.date());insert.setString(2,entry.type());insert.setString(3,entry.category());insert.setBigDecimal(4,entry.amount());insert.setString(5,entry.party());insert.setString(6,entry.notes());insert.setLong(7,entry.account());insert.addBatch();imported++;
                    if(imported%500==0)insert.executeBatch();
                }
                if(imported%500!=0)insert.executeBatch();
            }
            db.commit();return new Result(imported,skipped);
        }catch(SQLException | RuntimeException ex){db.rollback();throw ex;}
        finally{db.setAutoCommit(true);}
    }
}

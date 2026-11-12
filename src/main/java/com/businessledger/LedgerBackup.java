package com.businessledger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/** Portable, data-only backups. Restore binds values into a fixed set of application tables. */
final class LedgerBackup {
    static final String AUTO="backup_auto_on_close",FOLDER="backup_folder";
    private record Table(String name,String columns,String types){
        String[] names(){return columns.split(",");}
    }
    private static final List<Table> TABLES=List.of(
        new Table("categories","name,active","SB"),
        new Table("ledger_settings","name,value","SS"),
        new Table("accounts","id,name,account_type,opening_balance,active","LSSNB"),
        new Table("transactions","id,entry_date,kind,category,amount,party,notes,account_id","LDSSNSSL"),
        new Table("mileage","id,trip_date,origin,destination,purpose,miles","LDSSSN"),
        new Table("transfers","id,transfer_date,from_account_id,to_account_id,amount,notes","LDLLNS"),
        new Table("report_templates","id,name,kind,category,detail,account_id,account_totals","LSSSBLB"),
        new Table("report_template_categories","template_id,category","LS"),
        new Table("transaction_edits","id,transaction_id,edited_at,edited_by,reason,before_record,after_record","LLTSSJJ"),
        new Table("transfer_edits","id,transfer_id,edited_at,edited_by,reason,before_record,after_record","LLTSSJJ")
    );
    record Info(String ledger,String created){}
    record Options(boolean automatic,Path folder){}
    record Restored(Path safetyBackup){}
    private final Connection db;
    LedgerBackup(Connection db){this.db=db;}
    Options options()throws SQLException{
        boolean enabled=false;Path folder=Path.of(System.getProperty("ledger.home","."),"backups",db.getCatalog()).toAbsolutePath().normalize();
        try(PreparedStatement s=db.prepareStatement("SELECT name,value FROM ledger_settings WHERE name IN (?,?)")){
            s.setString(1,AUTO);s.setString(2,FOLDER);try(ResultSet r=s.executeQuery()){while(r.next()){
                if(AUTO.equals(r.getString(1)))enabled=Boolean.parseBoolean(r.getString(2));else folder=Path.of(r.getString(2)).toAbsolutePath().normalize();
            }}
        }
        return new Options(enabled,folder);
    }
    void saveOptions(boolean enabled,Path folder)throws SQLException{
        setting(AUTO,Boolean.toString(enabled));setting(FOLDER,folder.toAbsolutePath().normalize().toString());
    }
    private void setting(String name,String value)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("INSERT INTO ledger_settings(name,value) VALUES (?,?) ON CONFLICT(name) DO UPDATE SET value=EXCLUDED.value")){
            s.setString(1,name);s.setString(2,value);s.executeUpdate();
        }
    }
    static Path datedFile(Path folder,String ledger,String reason){
        String name=ledger.replaceAll("[^\\p{L}\\p{N}._-]+","_");if(name.isBlank())name="ledger";if(name.length()>60)name=name.substring(0,60);
        String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss"));
        return folder.resolve(name+"_"+reason+"_"+stamp+"_"+UUID.randomUUID().toString().substring(0,8)+".ledgerbackup");
    }
    void create(Path target,String ledger)throws Exception{
        requireIdle();target=target.toAbsolutePath().normalize();Files.createDirectories(target.getParent());
        Path temp=Files.createTempFile(target.getParent(),"ledger-backup-",".tmp");
        int isolation=db.getTransactionIsolation();boolean readOnly=db.isReadOnly();
        try{
            db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);db.setReadOnly(true);db.setAutoCommit(false);
            try(OutputStream file=Files.newOutputStream(temp);ZipOutputStream zip=new ZipOutputStream(file)){
                Properties manifest=new Properties();manifest.setProperty("format","BusinessLedger");manifest.setProperty("version","1");manifest.setProperty("created",Instant.now().toString());manifest.setProperty("ledger",ledger);
                for(Table table:TABLES){
                    zip.putNextEntry(new ZipEntry(table.name()+".data"));MessageDigest hash=MessageDigest.getInstance("SHA-256");
                    DataOutputStream out=new DataOutputStream(new DigestOutputStream(zip,hash));writeText(out,table.columns());writeText(out,table.types());
                    String where=table.name().equals("ledger_settings")?" WHERE name NOT IN ('"+AUTO+"','"+FOLDER+"')":"";
                    long count;try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table.name()+where)){r.next();count=r.getLong(1);}
                    out.writeLong(count);
                    try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT "+table.columns()+" FROM "+table.name()+where)){
                        long written=0;while(r.next()){
                            for(int col=0;col<table.types().length();col++){
                                char type=table.types().charAt(col);Object value=switch(type){case 'D'->r.getObject(col+1,LocalDate.class);case 'T'->r.getObject(col+1,OffsetDateTime.class);default->r.getString(col+1);};
                                writeText(out,value==null?null:value.toString());
                            }written++;
                        }if(written!=count)throw new IOException("Backup snapshot row count changed.");
                    }
                    out.flush();manifest.setProperty(table.name()+".sha256",HexFormat.of().formatHex(hash.digest()));zip.closeEntry();
                    if(hasIdentity(table)){
                        long next;try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT last_value,is_called FROM "+sequence(table))){r.next();next=Math.addExact(r.getLong(1),r.getBoolean(2)?1:0);}
                        manifest.setProperty(table.name()+".nextId",Long.toString(Math.max(next,nextAfterRows(table))));
                    }
                }
                zip.putNextEntry(new ZipEntry("manifest.properties"));manifest.store(zip,"Business Ledger data backup");zip.closeEntry();
                db.commit();
            }catch(Exception ex){db.rollback();throw ex;}
            finally{db.setAutoCommit(true);db.setReadOnly(readOnly);db.setTransactionIsolation(isolation);}
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    static Info inspect(Path path)throws Exception{
        try(ZipFile zip=new ZipFile(path.toFile())){Properties p=manifest(zip);return new Info(p.getProperty("ledger","Ledger"),p.getProperty("created","Unknown date"));}
    }
    private static Properties manifest(ZipFile zip)throws Exception{
        Set<String> expected=new HashSet<>();expected.add("manifest.properties");for(Table table:TABLES)expected.add(table.name()+".data");
        Set<String> found=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){
            String name=entries.nextElement().getName();if(!expected.contains(name)||!found.add(name))throw new IOException("This is not a supported ledger backup.");
        }
        if(!found.equals(expected))throw new IOException("Backup is missing required data files.");
        Properties p=new Properties();try(InputStream in=zip.getInputStream(zip.getEntry("manifest.properties"))){byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw new IOException("Invalid backup manifest.");p.load(new ByteArrayInputStream(bytes));}
        if(!"BusinessLedger".equals(p.getProperty("format"))||!"1".equals(p.getProperty("version")))throw new IOException("Unsupported ledger backup version.");
        for(Table table:TABLES)if(!p.getProperty(table.name()+".sha256","").matches("[0-9a-f]{64}"))throw new IOException("Backup checksum is missing.");
        for(Table table:TABLES)if(hasIdentity(table)){
            try{if(Long.parseLong(p.getProperty(table.name()+".nextId","0"))<1)throw new NumberFormatException();}
            catch(NumberFormatException ex){throw new IOException("Backup record numbering metadata is missing or invalid.");}
        }
        return p;
    }
    Restored restoreWithSafety(Path source,String ledger)throws Exception{
        inspect(source);Path safety=datedFile(options().folder(),ledger,"before-restore");create(safety,ledger);
        try{restore(source);}catch(Exception ex){throw new IOException("Restore failed. Safety backup: "+safety+". "+ex.getMessage(),ex);}
        return new Restored(safety);
    }
    void restore(Path source)throws Exception{
        requireIdle();Options local=options();
        try(ZipFile zip=new ZipFile(source.toFile())){
            Properties manifest=manifest(zip);db.setAutoCommit(false);
            try{
                try(Statement s=db.createStatement()){s.executeUpdate("TRUNCATE "+String.join(",",TABLES.stream().map(Table::name).toList())+" RESTART IDENTITY");}
                for(Table table:TABLES){
                    MessageDigest hash=MessageDigest.getInstance("SHA-256");
                    try(DataInputStream in=new DataInputStream(new DigestInputStream(zip.getInputStream(zip.getEntry(table.name()+".data")),hash))){
                        if(!table.columns().equals(readText(in))||!table.types().equals(readText(in)))throw new IOException("Unsupported columns in "+table.name()+".");
                        long count=in.readLong();if(count<0||count>10_000_000)throw new IOException("Invalid backup record count.");
                        String sql="INSERT INTO "+table.name()+" ("+table.columns()+") OVERRIDING SYSTEM VALUE VALUES ("+String.join(",",Collections.nCopies(table.types().length(),"?"))+")";
                        try(PreparedStatement s=db.prepareStatement(sql)){
                            for(long row=0;row<count;row++){
                                for(int col=0;col<table.types().length();col++)bind(s,col+1,table.types().charAt(col),readText(in));
                                s.addBatch();if((row+1)%500==0)s.executeBatch();
                            }s.executeBatch();
                        }
                        if(in.read()!=-1)throw new IOException("Unexpected extra backup data.");
                    }
                    if(!MessageDigest.isEqual(HexFormat.of().parseHex(manifest.getProperty(table.name()+".sha256")),hash.digest()))throw new IOException("Backup checksum failed for "+table.name()+".");
                }
                // Local backup destination and automatic-backup preference belong to this PC.
                saveOptions(local.automatic(),local.folder());
                for(Table table:TABLES)if(hasIdentity(table)){
                    long next=Math.max(nextAfterRows(table),Long.parseLong(manifest.getProperty(table.name()+".nextId")));
                    try(Statement s=db.createStatement()){s.executeUpdate("ALTER SEQUENCE "+sequence(table)+" RESTART WITH "+next);}
                }
                db.commit();
            }catch(Exception ex){db.rollback();throw ex;}
            finally{db.setAutoCommit(true);}
        }
    }
    private void requireIdle()throws SQLException{if(!db.getAutoCommit())throw new SQLException("Finish the current ledger operation before backing up or restoring.");}
    private static boolean hasIdentity(Table table){return table.types().startsWith("L")&&table.columns().startsWith("id,");}
    private String sequence(Table table)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("SELECT pg_get_serial_sequence(?,'id')")){
            s.setString(1,table.name());try(ResultSet r=s.executeQuery()){r.next();String name=r.getString(1);if(name==null||!name.matches("[a-z_]+\\.[a-z_]+"))throw new SQLException("Unsupported ledger sequence.");return name;}
        }
    }
    private long nextAfterRows(Table table)throws SQLException{
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM "+table.name())){r.next();return r.getLong(1);}
    }
    private static void bind(PreparedStatement s,int col,char type,String value)throws Exception{
        if(value==null){s.setObject(col,null);return;}
        switch(type){
            case 'L'->s.setLong(col,Long.parseLong(value));case 'N'->s.setBigDecimal(col,new java.math.BigDecimal(value));
            case 'D'->s.setObject(col,LocalDate.parse(value));case 'T'->s.setObject(col,OffsetDateTime.parse(value));
            case 'B'->{if(!Set.of("t","f","true","false").contains(value))throw new IOException("Invalid boolean value.");s.setBoolean(col,value.equals("t")||value.equals("true"));}
            case 'J'->s.setObject(col,value,Types.OTHER);default->s.setString(col,value);
        }
    }
    private static void writeText(DataOutputStream out,String value)throws IOException{
        if(value==null){out.writeInt(-1);return;}byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>16_777_216)throw new IOException("A ledger field is too large to back up.");out.writeInt(bytes.length);out.write(bytes);
    }
    private static String readText(DataInputStream in)throws IOException{
        int length=in.readInt();if(length==-1)return null;if(length<0||length>16_777_216)throw new IOException("Invalid backup field size.");byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException("Backup is incomplete.");return new String(bytes,StandardCharsets.UTF_8);
    }
}

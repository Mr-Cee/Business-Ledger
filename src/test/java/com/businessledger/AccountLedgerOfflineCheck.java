package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import static com.businessledger.AccountLedgerCheck.*;

/** Uses rows produced by the real query in a fresh single-user PostgreSQL fixture.
 * The JDBC adapter only supplies those rows, letting us check mapping/UI without TCP access. */
public final class AccountLedgerOfflineCheck {
    static final String[] COLUMNS={"account_id","account_name","account_type","active","opening_balance","id","day","kind","category","amount","party","notes","balance","edited"};
    static Object json(String text)throws Exception{
        Class<?> parser=Class.forName("com.businessledger.UpdateChecker$Json");Constructor<?> constructor=parser.getDeclaredConstructor(String.class);constructor.setAccessible(true);
        Method read=parser.getDeclaredMethod("read");read.setAccessible(true);return read.invoke(constructor.newInstance(text));
    }
    @SuppressWarnings("unchecked") static List<Map<String,Object>> records(Path path)throws Exception{return (List<Map<String,Object>>)json(Files.readString(path));}
    static Object proxy(Class<?> type,InvocationHandler handler){return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
    static ResultSet result(List<Map<String,Object>> rows){
        int[] current={-1};
        return (ResultSet)proxy(ResultSet.class,(object,method,args)->{
            String name=method.getName();if(name.equals("close"))return null;if(name.equals("next"))return ++current[0]<rows.size();
            Object value=rows.get(current[0]).get(COLUMNS[((Integer)args[0])-1]);
            return switch(name){case "getObject"->value;case "getString"->value==null?null:value.toString();case "getLong"->value==null?0L:((Number)value).longValue();case "getBoolean"->Boolean.TRUE.equals(value);case "getBigDecimal"->value==null?null:new BigDecimal(value.toString());case "getDate"->value==null?null:java.sql.Date.valueOf(value.toString());default->throw new UnsupportedOperationException(name);};
        });
    }
    static Connection connection(Path oracle,boolean[] changed)throws Exception{
        String sql=Files.readString(oracle.resolve("query.txt")).replaceAll("\\s+"," ").strip();
        return (Connection)proxy(Connection.class,(object,method,args)->{
            if(method.getName().equals("close"))return null;
            if(method.getName().equals("createStatement"))return proxy(Statement.class,(s,m,a)->{if(m.getName().equals("close"))return null;if(m.getName().equals("executeQuery"))return result(List.of());throw new UnsupportedOperationException(m.getName());});
            if(!method.getName().equals("prepareStatement"))throw new UnsupportedOperationException(method.getName());
            check(((String)args[0]).replaceAll("\\s+"," ").strip().equals(sql),"Compiled query differs from PostgreSQL-validated query");
            Map<Integer,Long> parameters=new HashMap<>();
            return proxy(PreparedStatement.class,(statement,action,values)->{
                if(action.getName().equals("close"))return null;
                if(action.getName().equals("setLong")){parameters.put((Integer)values[0],(Long)values[1]);return null;}
                if(!action.getName().equals("executeQuery"))throw new UnsupportedOperationException(action.getName());
                check(parameters.size()==4&&parameters.values().stream().allMatch(id->id.equals(parameters.get(1))),"Account must bind to all query branches");
                long id=parameters.get(1);return result(records(oracle.resolve(changed[0]&&id==1?"changed.json":"account-"+id+".json")));
            });
        });
    }
    static JTable findTable(Container parent,Object model){for(Component component:parent.getComponents()){if(component instanceof JTable table&&table.getModel()==model)return table;if(component instanceof Container child){JTable found=findTable(child,model);if(found!=null)return found;}}return null;}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Disposable PostgreSQL oracle folder required");
        Path oracle=Path.of(args[0]).toAbsolutePath().normalize();if(!oracle.startsWith(Path.of("target").toAbsolutePath().normalize()))throw new IllegalArgumentException("Only target fixture results allowed");
        boolean[] changed={false};Connection db=connection(oracle,changed);LocalDate start=LocalDate.of(2026,1,1),end=LocalDate.of(2026,1,31);
        var account=AccountLedgerPanel.load(db,1);check(account.entries().size()==7,"Account isolation");
        equal(account.entries().get(1).balance(),"130","Transfer first on same day");equal(account.entries().get(2).balance(),"105","Expense balance");equal(account.entries().get(3).balance(),"205","Income balance");
        check(account.entries().get(0).edited().equals("Edited")&&account.entries().get(1).edited().equals("Edited"),"Transfer/transaction ID collision audit routing");
        var summary=account.summary(start,end);equal(summary.opening(),"150","Prior carry-forward");equal(summary.in(),"105","Income plus transfer in");equal(summary.out(),"95","Expenses plus transfers out");equal(summary.closing(),"160","Period balance");equal(account.summary(null,null).closing(),"150","Future records included with all dates");
        equal(AccountLedgerPanel.load(db,3).summary(null,null).closing(),"-290","Credit debt/payment");check(!AccountLedgerPanel.load(db,4).active(),"Archived account");equal(AccountLedgerPanel.load(db,5).summary(start,end).closing(),"10","Empty account");
        Files.createDirectories(Path.of("target/account-ledger"));
        SwingUtilities.invokeAndWait(()->{try{
            AccountLedgerPanel.Entry[] selected={null};AccountLedgerPanel page=new AccountLedgerPanel(db,1,start,end,entry->selected[0]=entry,entry->selected[0]=entry);JTable table=(JTable)field(page,"table");LedgerFilters filters=(LedgerFilters)field(page,"filters");
            check(table.getRowCount()==5,"Initial date range");((JTextField)field(filters,"search")).setText("To: Savings");filters.applyNow();check(table.getRowCount()==1,"Transfer search");equal((BigDecimal)table.getModel().getValueAt(table.convertRowIndexToModel(0),8),"130","Search leaves balance intact");
            table.setRowSelectionInterval(0,0);button(page,"View edit history").doClick();check(selected[0].id()==1&&selected[0].transfer(),"Transfer audit target");filters.clear();check(table.getRowCount()==7,"Clearing never adds other accounts");filters.setDates(start,end);
            table.getRowSorter().toggleSortOrder(4);table.getRowSorter().toggleSortOrder(4);for(int view=0;view<table.getRowCount();view++){int model=table.convertRowIndexToModel(view);if(table.getModel().getValueAt(model,2).equals("Transfer Out")&&((Number)table.getModel().getValueAt(model,0)).longValue()==1){table.setRowSelectionInterval(view,view);break;}}button(page,"Edit selected").doClick();check(selected[0].id()==1&&selected[0].transfer(),"Sorted edit target");page.stopPending();
            for(AppTheme.Style style:new AppTheme.Style[]{AppTheme.Style.OCEAN,AppTheme.Style.MIDNIGHT}){AppTheme.apply(style);AccountLedgerPanel themed=new AccountLedgerPanel(db,1,start,end,entry->{},entry->{});themed.setSize(650,650);for(int pass=0;pass<4;pass++)layout(themed);BufferedImage image=new BufferedImage(650,650,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();themed.printAll(graphics);graphics.dispose();ImageIO.write(image,"png",Path.of("target/account-ledger/"+style.name().toLowerCase()+".png").toFile());themed.stopPending();}
            long[] opened={0};LocalDate[] selectedEnd={null};DashboardPanel dashboard=new DashboardPanel(db,(id,from,through)->{opened[0]=id;selectedEnd[0]=through;});
            dashboard.render(new DashboardPanel.Data(new ChartsPanel.Data(BigDecimal.ZERO,BigDecimal.ZERO,Map.of(),Map.of()),BigDecimal.ZERO,List.of(new Object[]{"Checking","Checking",new BigDecimal("160"),1L},new Object[]{"Savings","Savings",new BigDecimal("1214"),2L}),new TaxEstimate.Estimate(2026,BigDecimal.ZERO,new BigDecimal("24"),BigDecimal.ZERO)),end);
            JTable balances=findTable(dashboard,field(dashboard,"accounts"));balances.getRowSorter().toggleSortOrder(2);balances.getRowSorter().toggleSortOrder(2);balances.setRowSelectionInterval(0,0);balances.getActionMap().get("open-account-ledger").actionPerformed(new java.awt.event.ActionEvent(balances,0,"test"));check(opened[0]==2&&end.equals(selectedEnd[0]),"Sorted dashboard account ID and snapshot date");dashboard.stopPendingRefresh();
        }catch(Exception ex){throw new RuntimeException(ex);}});
        changed[0]=true;var edited=AccountLedgerPanel.load(db,1);equal(edited.summary(start,end).closing(),"180","Edited transfer period");equal(edited.summary(null,null).closing(),"140","Edited transfer balance");
        System.out.println("Offline PostgreSQL row mapping, bound account IDs, running/period balances, transfers/audit, archived/empty/credit accounts, search/edit routing, dashboard sorting/date linkage, and narrow light/dark UI checks passed.");
    }
}

package com.businessledger;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Run only against a fresh disposable database with user ledger_test on port 55439. */
public final class ReportIntegrationCheck {
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static Object call(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(target);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static BusinessLedger open(Connection db) throws Exception {
        Constructor<BusinessLedger> ctor = BusinessLedger.class.getDeclaredConstructor(Connection.class); ctor.setAccessible(true); return ctor.newInstance(db);
    }
    private static void select(BusinessLedger app, String name) throws Exception {
        JComboBox<?> box = (JComboBox<?>) field(app, "templates");
        for (int i = 0; i < box.getItemCount(); i++) if (box.getItemAt(i).toString().equals(name)) { box.setSelectedIndex(i); return; }
        throw new AssertionError("Missing template: " + name);
    }
    private static BigDecimal balanceAt(BusinessLedger app, String date, String account) throws Exception {
        DefaultTableModel rows = (DefaultTableModel) field(app, "transactions");
        for (int i = 0; i < rows.getRowCount(); i++) {
            if (rows.getValueAt(i, 1).toString().equals(date) && account.equals(rows.getValueAt(i, 7)))
                return (BigDecimal) rows.getValueAt(i, 8);
        }
        throw new AssertionError("Missing transaction " + date + " / " + account);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].contains(":55439/")) throw new IllegalArgumentException("Use disposable test database on port 55439.");
        try (Connection db = DriverManager.getConnection(args[0], "ledger_test", "")) {
            SwingUtilities.invokeAndWait(() -> {
                BusinessLedger app = null;
                try {
                    app = open(db);
                    check(((JComboBox<?>) field(app, "templates")).getItemCount() == 5, "Five defaults");
                    try (Statement s = db.createStatement()) {
                        s.executeUpdate("INSERT INTO transactions(entry_date,kind,category,amount) VALUES ('2026-01-01','Expense','Food',10),('2026-12-31','Income','Paycheck',100),('2026-06-15','Expense','Supplies',20),('2025-12-31','Expense','Food',999),('2026-07-01','Income','Food',5)");
                    }
                    ((JTextField) field(app, "from")).setText("2026-01-01");
                    ((JTextField) field(app, "through")).setText("2026-12-31");
                    String[] names = {"All Expenses", "All Income", "All Transactions", "All Food"};
                    int[] counts = {2, 2, 4, 2};
                    for (int i = 0; i < names.length; i++) {
                        select(app, names[i]); call(app, "runReport");
                        check(((DefaultTableModel) field(app, "report")).getRowCount() == counts[i], names[i] + " filtering / inclusive dates");
                    }
                    select(app, "All Transactions");
                    JComboBox<?> comparison = (JComboBox<?>) field(app, "amountComparison");
                    JTextField amountFilter = (JTextField) field(app, "reportAmount");
                    amountFilter.setText("10.00");
                    int[] amountCounts = {0, 2, 1, 1};
                    for (int mode = 1; mode <= 3; mode++) {
                        comparison.setSelectedIndex(mode); call(app, "runReport");
                        check(((DefaultTableModel) field(app, "report")).getRowCount() == amountCounts[mode], "Strict amount comparison " + mode);
                    }
                    comparison.setSelectedIndex(2); amountFilter.setText("0"); call(app, "runReport");
                    check(((DefaultTableModel) field(app, "report")).getRowCount() == 0, "Zero threshold accepted");
                    amountFilter.setText("-1"); boolean invalidAmount = false;
                    try { call(app, "runReport"); } catch (InvocationTargetException ex) { invalidAmount = ex.getCause() instanceof IllegalArgumentException; }
                    check(invalidAmount, "Negative report amounts rejected");
                    comparison.setSelectedIndex(0); call(app, "runReport");
                    check(((DefaultTableModel) field(app, "report")).getRowCount() == 4, "Any amount ignores unused value");
                    try (Statement s = db.createStatement()) {
                        s.executeUpdate("INSERT INTO report_templates(name,kind,category,detail) VALUES ('Food expense totals','Expense','Food',FALSE)");
                    }
                    Method reload = BusinessLedger.class.getDeclaredMethod("reloadTemplates", Long.class); reload.setAccessible(true); reload.invoke(app, new Object[]{null});
                    select(app, "Food expense totals"); call(app, "runReport");
                    DefaultTableModel result = (DefaultTableModel) field(app, "report");
                    check(result.getColumnCount() == 3 && result.getRowCount() == 1, "Summary layout");
                    check(new BigDecimal("10.00").compareTo((BigDecimal) result.getValueAt(0, 2)) == 0, "Filtered summary total");
                    comparison.setSelectedIndex(1); amountFilter.setText("10"); call(app, "runReport");
                    check(result.getRowCount() == 0, "Amount filter applies before category aggregation");
                    comparison.setSelectedIndex(3); call(app, "runReport");
                    check(result.getRowCount() == 1 && ((BigDecimal) result.getValueAt(0, 2)).compareTo(new BigDecimal("10")) == 0, "Equal amount works in summary reports");
                    comparison.setSelectedIndex(0);
                    try (Statement s = db.createStatement()) {
                        s.executeUpdate("UPDATE report_templates SET name='Saved food',kind='Income',detail=TRUE WHERE name='Food expense totals'");
                        s.executeUpdate("DELETE FROM report_templates WHERE name='All Expenses'");
                    }
                    app.dispose(); app = open(db);
                    DefaultTableModel transactionRows = (DefaultTableModel) field(app, "transactions");
                    check(transactionRows.getColumnName(8).equals("Running balance ($)"), "Running balance follows account");
                    check(transactionRows.getColumnClass(8) == BigDecimal.class, "Balance column sorts numerically even when unassigned");
                    check(transactionRows.getValueAt(0, 8) == null, "Unassigned entries have no invented account balance");
                    check(((JComboBox<?>) field(app, "templates")).getItemCount() == 5, "Deleted default stays deleted after restart");
                    select(app, "Saved food");
                    ((JTextField) field(app, "from")).setText("2026-01-01"); ((JTextField) field(app, "through")).setText("2026-12-31");
                    call(app, "runReport");
                    check(((DefaultTableModel) field(app, "report")).getRowCount() == 1, "Edited template persists");
                    try (Statement s = db.createStatement()) {
                        s.executeUpdate("UPDATE accounts SET opening_balance=1000 WHERE name='Checking'");
                        s.executeUpdate("UPDATE accounts SET opening_balance=-200 WHERE name='Credit'");
                        s.executeUpdate("UPDATE transactions SET account_id=(SELECT id FROM accounts WHERE name='Checking') WHERE kind='Income'");
                        s.executeUpdate("UPDATE transactions SET account_id=(SELECT id FROM accounts WHERE name='Credit') WHERE kind='Expense'");
                        s.executeUpdate("INSERT INTO transfers(transfer_date,from_account_id,to_account_id,amount) SELECT DATE '2026-07-01',a.id,b.id,50 FROM accounts a,accounts b WHERE a.name='Checking' AND b.name='Credit'");
                        try (ResultSet r = s.executeQuery("SELECT balance FROM account_balances WHERE name='Checking'")) { r.next(); check(r.getBigDecimal(1).compareTo(new BigDecimal("1055")) == 0, "Income and transfer debit balance"); }
                        try (ResultSet r = s.executeQuery("SELECT balance FROM account_balances WHERE name='Credit'")) { r.next(); check(r.getBigDecimal(1).compareTo(new BigDecimal("-1179")) == 0, "Credit charges and payment balance"); }
                        try (ResultSet r = s.executeQuery("SELECT SUM(balance) FROM account_balances")) { r.next(); check(r.getBigDecimal(1).compareTo(new BigDecimal("-124")) == 0, "Transfers preserve combined balances"); }
                        boolean rejected = false;
                        try { s.executeUpdate("INSERT INTO transfers(transfer_date,from_account_id,to_account_id,amount) SELECT CURRENT_DATE,id,id,1 FROM accounts LIMIT 1"); }
                        catch (SQLException ex) { rejected = "23514".equals(ex.getSQLState()); }
                        check(rejected, "Same-account transfers rejected");
                        s.executeUpdate("UPDATE report_templates SET account_id=(SELECT id FROM accounts WHERE name='Checking') WHERE name='All Transactions'");
                    }
                    call(app, "refresh");
                    select(app,"All In/Out");call(app,"runReport");
                    DefaultTableModel accountTotals=(DefaultTableModel)field(app,"report");
                    check(accountTotals.getRowCount()==3 && accountTotals.getColumnCount()==3,"One totals row for every account including inactive-in-period accounts");
                    for(int row=0;row<accountTotals.getRowCount();row++) {
                        String accountName=accountTotals.getValueAt(row,0).toString();
                        BigDecimal incoming=(BigDecimal)accountTotals.getValueAt(row,1),outgoing=(BigDecimal)accountTotals.getValueAt(row,2);
                        if(accountName.equals("Checking")) check(incoming.compareTo(new BigDecimal("105"))==0 && outgoing.signum()==0,"Checking income excludes outgoing transfers");
                        if(accountName.equals("Credit")) check(incoming.signum()==0 && outgoing.compareTo(new BigDecimal("30"))==0,"Credit expenses exclude incoming transfers and respect dates");
                        if(accountName.equals("Savings")) check(incoming.signum()==0 && outgoing.signum()==0,"Empty account shows zero totals");
                    }
                    DefaultTableModel ledger = (DefaultTableModel) field(app, "transactions");
                    check(ledger.getRowCount() == 7, "Each transfer adds exactly two main-ledger rows");
                    int transferRows = 0;
                    JTable sortedLedger = new JTable(ledger); sortedLedger.setAutoCreateRowSorter(true);
                    sortedLedger.getRowSorter().toggleSortOrder(7);
                    Method transactionId = BusinessLedger.class.getDeclaredMethod("selectedTransactionId", JTable.class); transactionId.setAccessible(true);
                    for (int row = 0; row < ledger.getRowCount(); row++) {
                        String type = ledger.getValueAt(row, 2).toString();
                        if (!type.startsWith("Transfer ")) continue;
                        transferRows++;
                        check(ledger.getValueAt(row, 3).equals("Transfer"), "Transfer category is clearly labeled");
                        check(((BigDecimal) ledger.getValueAt(row, 4)).compareTo(new BigDecimal("50")) == 0, "Transfer amount displayed once on each side");
                        if (type.equals("Transfer Out")) {
                            check(ledger.getValueAt(row, 7).equals("Checking") && ledger.getValueAt(row, 5).equals("To: Credit"), "Outgoing account and destination");
                            check(((BigDecimal) ledger.getValueAt(row, 8)).compareTo(new BigDecimal("950")) == 0, "Transfer-out running balance");
                        } else {
                            check(ledger.getValueAt(row, 7).equals("Credit") && ledger.getValueAt(row, 5).equals("From: Checking"), "Incoming account and source");
                            check(((BigDecimal) ledger.getValueAt(row, 8)).compareTo(new BigDecimal("-1179")) == 0, "Transfer-in running balance");
                        }
                        sortedLedger.setRowSelectionInterval(sortedLedger.convertRowIndexToView(row), sortedLedger.convertRowIndexToView(row));
                        boolean guarded = false;
                        try { transactionId.invoke(null, sortedLedger); }
                        catch (InvocationTargetException ex) { guarded = ex.getCause() instanceof IllegalArgumentException; }
                        check(guarded, "Transfer rows cannot delete or reassign a transaction with the same numeric ID");
                    }
                    check(transferRows == 2, "Both transfer directions visible");
                    check(balanceAt(app, "2026-07-01", "Checking").compareTo(new BigDecimal("955")) == 0, "Same-day outgoing transfer precedes transaction");
                    check(balanceAt(app, "2026-12-31", "Checking").compareTo(new BigDecimal("1055")) == 0, "Running balance includes opening and prior transactions only");
                    check(balanceAt(app, "2025-12-31", "Credit").compareTo(new BigDecimal("-1199")) == 0, "Backdated entry precedes newer entries regardless of ID");
                    check(balanceAt(app, "2026-01-01", "Credit").compareTo(new BigDecimal("-1209")) == 0, "Credit running balance excludes later transfers");
                    reload.invoke(app, new Object[]{null}); select(app, "All Transactions"); call(app, "runReport");
                    check(((DefaultTableModel) field(app, "report")).getRowCount() == 2, "Account report filter excludes transfers");
                    try (Statement s = db.createStatement()) {
                        s.executeUpdate("DELETE FROM transfers");
                        try (ResultSet r = s.executeQuery("SELECT balance FROM account_balances WHERE name='Checking'")) { r.next(); check(r.getBigDecimal(1).compareTo(new BigDecimal("1105")) == 0, "Deleting transfer reverses debit"); }
                        s.executeUpdate("UPDATE accounts SET active=FALSE WHERE name='Credit'");
                    }
                    call(app, "refresh");
                    check(((DefaultTableModel) field(app, "transactions")).getRowCount() == 5, "Deleting transfer removes both display rows");
                    check(balanceAt(app, "2026-07-01", "Checking").compareTo(new BigDecimal("1005")) == 0, "Deleting transfer recalculates running balance");
                    JComboBox<?> choice = (JComboBox<?>) field(app, "transactionAccount");
                    check(choice.getItemCount() == 2, "Archived accounts excluded from new transactions");
                    DateRangeControls reportRanges=(DateRangeControls)field(app,"reportRanges");
                    select(app,"All Transactions");reportRanges.year.getEditor().setItem("2026");reportRanges.range.setSelectedIndex(14);call(app,"runReport");
                    check(((JTextField)field(app,"from")).getText().equals("2026-10-01") && ((JTextField)field(app,"through")).getText().equals("2026-12-31"),"Reports direct Q4 fills selected year dates");
                    check(((DefaultTableModel)field(app,"report")).getRowCount()==1,"Report quarter filter respects template account");
                    reportRanges.range.setSelectedIndex(13);call(app,"runReport");check(((DefaultTableModel)field(app,"report")).getRowCount()==1,"Q3 report includes July transaction");
                    reportRanges.range.setSelectedIndex(10);call(app,"runReport");check(((JTextField)field(app,"from")).getText().equals("2025-12-31") && ((DefaultTableModel)field(app,"report")).getRowCount()==2,"All recorded dates works on reports");
                    ((JTextField)field(app,"from")).setText("2026-01-01");((JTextField)field(app,"through")).setText("2026-12-31");check(reportRanges.range.getSelectedIndex()==0,"Editing report dates returns to Custom");
                    Method saveTemplate=BusinessLedger.class.getDeclaredMethod("saveReportTemplate",Long.class,String.class,String.class,java.util.List.class,boolean.class,Long.class); saveTemplate.setAccessible(true);
                    long multiId=(Long)saveTemplate.invoke(app,null,"Food and Supplies","Expense",java.util.List.of("Food","Supplies"),false,null);
                    app.dispose(); app=open(db);
                    ((JTextField)field(app,"from")).setText("2026-01-01"); ((JTextField)field(app,"through")).setText("2026-12-31");
                    select(app,"Food and Supplies");call(app,"runReport");
                    DefaultTableModel multi=(DefaultTableModel)field(app,"report");
                    check(multi.getRowCount()==2,"Multiple categories persist and match either category");
                    BigDecimal combined=BigDecimal.ZERO;for(int row=0;row<multi.getRowCount();row++) combined=combined.add((BigDecimal)multi.getValueAt(row,2));
                    check(combined.compareTo(new BigDecimal("30"))==0,"Multiple-category totals respect type and dates");
                    boolean rollback=false;
                    try{saveTemplate.invoke(app,multiId,"Food and Supplies","Expense",java.util.List.of("Missing category"),false,null);}catch(InvocationTargetException ex){rollback=ex.getCause() instanceof SQLException;}
                    check(rollback,"Invalid category rolls back template change");
                    reload.invoke(app,new Object[]{multiId});call(app,"runReport");check(multi.getRowCount()==2,"Failed save keeps prior category selections");
                    saveTemplate.invoke(app,multiId,"Food and Supplies",null,java.util.List.of(),true,null);
                    reload.invoke(app,new Object[]{multiId});call(app,"runReport");check(multi.getRowCount()==4,"All categories clears previous selections");
                    saveTemplate.invoke(app,multiId,"Food and Supplies","Expense",java.util.List.of("Food","Supplies"),true,null);
                    try(Statement s=db.createStatement()) {s.executeUpdate("DELETE FROM report_templates WHERE id="+multiId);try(ResultSet r=s.executeQuery("SELECT count(*) FROM report_template_categories WHERE template_id="+multiId)){r.next();check(r.getInt(1)==0,"Deleting template removes category links");}}
                    System.out.println("Report integration checks passed.");
                } catch (Exception ex) { throw new RuntimeException(ex); }
                finally { if (app != null) app.dispose(); }
            });
        }
    }
}

package com.businessledger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Single-user business ledger. All SQL values are bound parameters. */
public final class BusinessLedger extends JFrame {
    private final Connection db;
    private final DefaultTableModel transactions = model("ID", "Date", "Type", "Category", "Amount", "Payee / customer", "Notes", "Account", "Running balance ($)", "Edited");
    private String loginName = "Local user";
    private final java.util.List<String> payeeHistory = new java.util.ArrayList<>();
    private final java.util.List<String> notesHistory = new java.util.ArrayList<>();
    private record Account(long id, String name, String type, boolean active) {
        @Override public String toString() { return name + (active ? "" : " (archived)"); }
    }
    private final JComboBox<Account> transactionAccount = new JComboBox<>();
    private final DefaultTableModel accounts = model("ID", "Name", "Type", "Opening balance ($)", "Recorded balance ($)", "Status");
    private final DefaultTableModel transfers = model("ID", "Date", "From account", "To account", "Amount ($)", "Notes", "Edited");
    private final DefaultTableModel trips = model("ID", "Date", "From", "To", "Business purpose", "Miles");
    private final DefaultTableModel report = model("Type", "Category", "Total");
    private final JComboBox<String> categories = new JComboBox<>();
    private final JTextArea totals = new JTextArea(" ", 3, 0);
    private final JTextField from = new JTextField(LocalDate.now().withDayOfYear(1).toString(), 10);
    private final JTextField through = new JTextField(LocalDate.now().toString(), 10);
    private DateRangeControls reportRanges;
    private final JComboBox<String> amountComparison = new JComboBox<>(new String[]{"Any amount", "Above", "Below", "Equal to"});
    private final JTextField reportAmount = new JTextField(10);
    private record ReportTemplate(long id, String name, String kind, java.util.List<String> selectedCategories, boolean detail, Long accountId, boolean accountTotals) {
        @Override public String toString() { return name; }
    }
    private final JComboBox<ReportTemplate> templates = new JComboBox<>();
    private String reportSummary = "";

    private BusinessLedger(Connection connection) throws SQLException {
        super("Business Ledger"); db = connection;
        initialize();
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JMenuBar menuBar = new JMenuBar();
        setSize(1100, 700); setLocationRelativeTo(null);
        totals.setEditable(false); totals.setLineWrap(true); totals.setWrapStyleWord(true);
        totals.setBackground(UIManager.getColor("Panel.background"));
        JTabbedPane tabs = new JTabbedPane();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        DashboardPanel dashboard = new DashboardPanel(db);
        addWindowListener(new java.awt.event.WindowAdapter(){
            private boolean closing;
            @Override public void windowClosing(java.awt.event.WindowEvent event){
                if(closing)return;closing=true;dashboard.stopPendingRefresh();
                try{BackupControls.closeWithBackup(BusinessLedger.this,db,()->{dispose();System.exit(0);});}finally{closing=false;}
            }
        });
        tabs.addTab("Dashboard", dashboard);
        tabs.addTab("Income & expenses", transactionPanel());
        tabs.addTab("Accounts & transfers", accountsPanel());
        tabs.addTab("Business mileage", mileagePanel());
        tabs.addTab("Reports", reportPanel());
        ChartsPanel charts = new ChartsPanel(db);
        tabs.addTab("Charts", charts);
        JMenu settingsMenu = new JMenu("Settings");
        JMenuItem openSettings = new JMenuItem("Open settings...");
        openSettings.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_COMMA,java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openSettings.addActionListener(e -> {
            dashboard.stopPendingRefresh();
            try {
                SettingsPanel.createDialog(this,db,() -> {
                    try{refresh();}catch(SQLException ex){throw new RuntimeException(ex);}
                    clearReport(); charts.settingsChanged(); dashboard.refreshSafely();
                }).setVisible(true);
            } catch(SQLException ex) { JOptionPane.showMessageDialog(this,ex.getMessage(),"Unable to load settings",JOptionPane.ERROR_MESSAGE); }
        });
        settingsMenu.add(openSettings); menuBar.add(settingsMenu);
        JMenu help=new JMenu("Help");JMenuItem about=new JMenuItem("About Business Ledger");
        JMenuItem guide = new JMenuItem("Help guide...");
        guide.addActionListener(e -> HelpGuide.show(this, "Getting started"));
        JMenuItem screenHelp = new JMenuItem("Help for this screen");
        screenHelp.setAccelerator(KeyStroke.getKeyStroke("F1"));
        screenHelp.addActionListener(e -> HelpGuide.show(this, tabs.getTitleAt(tabs.getSelectedIndex())));
        help.add(guide); help.add(screenHelp); help.addSeparator();
        JMenuItem updates = new JMenuItem("Check for updates...");
        updates.addActionListener(e -> UpdateChecker.check(this, true));
        help.add(updates); help.addSeparator();
        HelpGuide.bind(getRootPane(), () -> tabs.getTitleAt(tabs.getSelectedIndex()));
        about.addActionListener(e->JOptionPane.showMessageDialog(this,"Business Ledger\nVersion "+AppVersion.version()+"\nBuilt: "+AppVersion.built()+"\n\nData folder: "+System.getProperty("ledger.home","."),"About Business Ledger",JOptionPane.INFORMATION_MESSAGE));
        help.add(about);menuBar.add(help);setJMenuBar(menuBar);
        tabs.addChangeListener(e -> {
            if(tabs.getSelectedComponent()==charts) charts.refreshSafely();
            if(tabs.getSelectedComponent()==dashboard) dashboard.refreshSafely();
        });
        add(tabs); refresh(); dashboard.refreshSafely();
        SwingUtilities.invokeLater(() -> UpdateChecker.onStartup(this));
    }

    private void initialize() throws SQLException {
        try (Statement s = db.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS categories (name TEXT PRIMARY KEY CHECK(length(trim(name)) > 0))");
            s.executeUpdate("ALTER TABLE categories ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ledger_settings (name TEXT PRIMARY KEY, value TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS transactions (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, entry_date DATE NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('Income','Expense')), category TEXT NOT NULL REFERENCES categories(name), amount NUMERIC(14,2) NOT NULL CHECK(amount > 0), party TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS mileage (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, trip_date DATE NOT NULL, origin TEXT NOT NULL, destination TEXT NOT NULL, purpose TEXT NOT NULL, miles NUMERIC(12,2) NOT NULL CHECK(miles > 0))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS transactions_date_idx ON transactions(entry_date)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS mileage_date_idx ON mileage(trip_date)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS report_templates (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name TEXT NOT NULL UNIQUE CHECK(length(trim(name))>0), kind TEXT CHECK(kind IN ('Income','Expense')), category TEXT REFERENCES categories(name), detail BOOLEAN NOT NULL DEFAULT TRUE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS accounts (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name TEXT UNIQUE NOT NULL CHECK(length(trim(name))>0), account_type TEXT NOT NULL CHECK(account_type IN ('Checking','Savings','Credit','Cash','Other')), opening_balance NUMERIC(14,2) NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE)");
            s.executeUpdate("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS account_id BIGINT REFERENCES accounts(id)");
            s.executeUpdate("ALTER TABLE report_templates ADD COLUMN IF NOT EXISTS account_id BIGINT REFERENCES accounts(id)");
            s.executeUpdate("ALTER TABLE report_templates ADD COLUMN IF NOT EXISTS account_totals BOOLEAN NOT NULL DEFAULT FALSE");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS report_template_categories (template_id BIGINT NOT NULL REFERENCES report_templates(id) ON DELETE CASCADE, category TEXT NOT NULL REFERENCES categories(name), PRIMARY KEY(template_id,category))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS transfers (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, transfer_date DATE NOT NULL, from_account_id BIGINT NOT NULL REFERENCES accounts(id), to_account_id BIGINT NOT NULL REFERENCES accounts(id), amount NUMERIC(14,2) NOT NULL CHECK(amount>0), notes TEXT NOT NULL DEFAULT '', CHECK(from_account_id<>to_account_id))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS transactions_account_idx ON transactions(account_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS transfers_from_idx ON transfers(from_account_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS transfers_to_idx ON transfers(to_account_id)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS transaction_edits (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, transaction_id BIGINT NOT NULL, edited_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, edited_by TEXT NOT NULL, reason TEXT NOT NULL CHECK(length(trim(reason))>0), before_record JSONB NOT NULL, after_record JSONB NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS transaction_edits_transaction_idx ON transaction_edits(transaction_id)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS transfer_edits (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, transfer_id BIGINT NOT NULL, edited_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, edited_by TEXT NOT NULL, reason TEXT NOT NULL CHECK(length(trim(reason))>0), before_record JSONB NOT NULL, after_record JSONB NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS transfer_edits_transfer_idx ON transfer_edits(transfer_id)");
            s.executeUpdate("CREATE OR REPLACE VIEW account_balances AS SELECT a.*, a.opening_balance + COALESCE((SELECT SUM(CASE WHEN t.kind='Income' THEN t.amount ELSE -t.amount END) FROM transactions t WHERE t.account_id=a.id),0) + COALESCE((SELECT SUM(x.amount) FROM transfers x WHERE x.to_account_id=a.id),0) - COALESCE((SELECT SUM(x.amount) FROM transfers x WHERE x.from_account_id=a.id),0) AS balance FROM accounts a");
        }
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT 1 FROM ledger_settings WHERE name='categories_initialized'")) {
            if (!r.next()) {
                for (String category : new String[]{"Sales", "Services", "Food", "Supplies", "Travel", "Software", "Rent", "Utilities", "Insurance", "Other"})
                    execute("INSERT INTO categories(name) VALUES (?) ON CONFLICT DO NOTHING", category);
                execute("INSERT INTO ledger_settings(name,value) VALUES ('categories_initialized','true')");
            }
        }
        execute("INSERT INTO categories(name) VALUES ('Paycheck') ON CONFLICT DO NOTHING");
        db.setAutoCommit(false);
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT 1 FROM ledger_settings WHERE name='report_templates_initialized'")) {
            if (!r.next()) {
                execute("INSERT INTO categories(name,active) VALUES ('Food',FALSE) ON CONFLICT DO NOTHING");
                execute("INSERT INTO report_templates(name,kind) VALUES ('All Expenses','Expense'),('All Income','Income'),('All Transactions',NULL) ON CONFLICT DO NOTHING");
                execute("INSERT INTO report_templates(name,category) VALUES ('All Food','Food') ON CONFLICT DO NOTHING");
                execute("INSERT INTO ledger_settings(name,value) VALUES ('report_templates_initialized','true')");
            }
            db.commit();
        } catch (SQLException ex) { db.rollback(); throw ex; }
        finally { db.setAutoCommit(true); }
        db.setAutoCommit(false);
        try (Statement s=db.createStatement(); ResultSet r=s.executeQuery("SELECT 1 FROM ledger_settings WHERE name='in_out_report_initialized'")) {
            if(!r.next()) {
                execute("INSERT INTO report_templates(name,detail,account_totals) VALUES ('All In/Out',FALSE,TRUE) ON CONFLICT DO NOTHING");
                execute("INSERT INTO ledger_settings(name,value) VALUES ('in_out_report_initialized','true')");
            }
            db.commit();
        } catch(SQLException ex) { db.rollback(); throw ex; }
        finally { db.setAutoCommit(true); }
        db.setAutoCommit(false);
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT 1 FROM ledger_settings WHERE name='accounts_initialized'")) {
            if (!r.next()) {
                execute("INSERT INTO accounts(name,account_type) VALUES ('Checking','Checking'),('Savings','Savings'),('Credit','Credit') ON CONFLICT DO NOTHING");
                execute("INSERT INTO ledger_settings(name,value) VALUES ('accounts_initialized','true')");
            }
            db.commit();
        } catch (SQLException ex) { db.rollback(); throw ex; }
        finally { db.setAutoCommit(true); }
    }

    private JPanel transactionPanel() throws SQLException {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        JTextField date = new JTextField(LocalDate.now().toString(), 10);
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT value FROM ledger_settings WHERE name='last_transaction_date'")) {
            if (r.next()) {
                try { date.setText(LocalDate.parse(r.getString(1)).toString()); }
                catch (java.time.format.DateTimeParseException ignored) { /* Keep today if an old setting is invalid. */ }
            }
        }
        JComboBox<String> kind = new JComboBox<>(new String[]{"Expense", "Income"});
        kind.addActionListener(e -> { if ("Income".equals(kind.getSelectedItem())) categories.setSelectedItem("Paycheck"); });
        JTextField amount = new JTextField(8), party = new GhostField(12, () -> payeeHistory), notes = new GhostField(15, () -> notesHistory);
        JPanel fields = new WrapPanel();
        field(fields, "Date (YYYY-MM-DD)", new DateChooser(date)); field(fields, "Type", kind); field(fields, "Category", categories);
        field(fields, "Amount ($)", amount); field(fields, "Payee / customer", party); field(fields, "Notes", notes);
        field(fields, "Account", transactionAccount);
        JButton save = button("Add transaction", () -> {
            Account account = requiredAccount(transactionAccount);
            LocalDate day = LocalDate.parse(date.getText().trim()); BigDecimal value = positive(amount.getText());
            db.setAutoCommit(false);
            try {
                try (PreparedStatement s = db.prepareStatement("INSERT INTO transactions(entry_date,kind,category,amount,party,notes,account_id) SELECT ?,?,?,?,?,?,id FROM accounts WHERE id=? AND active")) {
                    bind(s, new Object[]{day,kind.getSelectedItem(),categories.getSelectedItem(),value,party.getText().trim(),notes.getText().trim(),account.id()});
                    if (s.executeUpdate() != 1) throw new SQLException("The selected account is no longer active.");
                }
                execute("INSERT INTO ledger_settings(name,value) VALUES ('last_transaction_date',?) ON CONFLICT(name) DO UPDATE SET value=EXCLUDED.value", day.toString());
                db.commit();
            } catch (SQLException ex) { db.rollback(); throw ex; }
            finally { db.setAutoCommit(true); }
            amount.setText(""); party.setText(""); notes.setText(""); refresh();
            amount.requestFocusInWindow();
        });
        for (JTextField input : new JTextField[]{date, amount, party, notes})
            input.addActionListener(e -> save.doClick());
        notes.getActionMap().put("accept-history", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                ((GhostField) notes).acceptCompletion();
                save.requestFocusInWindow();
            }
        });
        notes.setToolTipText("Tab accepts the suggestion and focuses Add transaction; Enter then saves.");
        save.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "submit-transaction");
        save.getActionMap().put("submit-transaction", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { save.doClick(); }
        });
        save.setToolTipText("Add transaction (Enter from a transaction text field)");
        JButton category = button("Manage categories", this::manageCategories);
        LedgerTable table = new LedgerTable(transactions); table.setAutoCreateRowSorter(true);
        JCheckBox colorRows = new JCheckBox("Color rows (red = money out, green = money in)");
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT value FROM ledger_settings WHERE name='color_ledger_rows'")) {
            if (r.next()) colorRows.setSelected(Boolean.parseBoolean(r.getString(1)));
        }
        table.colorRows = colorRows.isSelected();
        colorRows.addActionListener(e -> {
            try {
                execute("INSERT INTO ledger_settings(name,value) VALUES ('color_ledger_rows',?) ON CONFLICT(name) DO UPDATE SET value=EXCLUDED.value", Boolean.toString(colorRows.isSelected()));
                table.colorRows = colorRows.isSelected(); table.repaint();
            } catch (SQLException ex) {
                colorRows.setSelected(table.colorRows);
                JOptionPane.showMessageDialog(this,ex.getMessage(),"Unable to save color preference",JOptionPane.ERROR_MESSAGE);
            }
        });
        table.getColumnModel().getColumn(8).setPreferredWidth(145);
        table.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            private final java.text.NumberFormat currency = java.text.NumberFormat.getCurrencyInstance(Locale.US);
            @Override protected void setValue(Object value) {
                setHorizontalAlignment(SwingConstants.RIGHT);
                setText(value == null ? "—" : currency.format(value));
            }
        });
        table.getTableHeader().setToolTipText("Running balance is per account, oldest date first. Same-day transfers precede transactions; transactions follow ID order. Unassigned entries have no account balance.");
        JPanel actions = new WrapPanel(); actions.add(save); actions.add(category);
        actions.add(button("Import CSV", () -> { if(CsvImportWizard.showImport(this,db)) refresh(); }));
        actions.add(button("Edit selected", () -> editTransaction(selectedTransactionId(table))));
        actions.add(button("View edit history", () -> viewEditHistory(selectedTransactionId(table))));
        actions.add(button("Delete selected", () -> { selectedTransactionId(table); delete(table, "transactions"); }));
        actions.add(button("Assign / change account", () -> editTransaction(selectedTransactionId(table))));
        JPanel header = new JPanel(new BorderLayout(4,4)); header.add(fields);
        JPanel options = new WrapPanel(); options.add(colorRows); header.add(options,BorderLayout.SOUTH);
        LedgerFilters filters=new LedgerFilters(table);filters.installShortcut(p);
        JPanel records=new JPanel(new BorderLayout(4,4));records.add(filters,BorderLayout.NORTH);records.add(new JScrollPane(table));
        p.add(header, BorderLayout.NORTH); p.add(records); p.add(actions, BorderLayout.SOUTH); return p;
    }

    static final class LedgerTable extends JTable {
        boolean colorRows;
        private static final Color MONEY_OUT = new Color(255, 233, 233);
        private static final Color MONEY_IN = new Color(231, 246, 233);
        LedgerTable(DefaultTableModel model) { super(model); }
        @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer,int row,int column) {
            Component cell = super.prepareRenderer(renderer,row,column);
            if (isCellSelected(row,column)) {
                cell.setBackground(getSelectionBackground()); cell.setForeground(getSelectionForeground());
            } else {
                Color background = getBackground();
                if (colorRows) {
                    Object type = getModel().getValueAt(convertRowIndexToModel(row),2);
                    if ("Expense".equals(type) || "Transfer Out".equals(type)) background = AppTheme.money(false);
                    else if ("Income".equals(type) || "Transfer In".equals(type)) background = AppTheme.money(true);
                }
                cell.setBackground(background); cell.setForeground(getForeground());
            }
            return cell;
        }
    }

    private void editTransaction(long id) throws SQLException {
        JTextField date = new JTextField(10), amount = new JTextField(12), party = new GhostField(24, () -> payeeHistory), notes = new GhostField(24, () -> notesHistory);
        JComboBox<String> kind = new JComboBox<>(new String[]{"Expense", "Income"}), category = new JComboBox<>();
        JComboBox<Account> account = new JComboBox<>();
        String originalCategory; Long originalAccount;
        try (PreparedStatement s = db.prepareStatement("SELECT entry_date,kind,category,amount,party,notes,account_id FROM transactions WHERE id=?")) {
            s.setLong(1,id); try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Transaction no longer exists.");
                date.setText(r.getObject(1).toString()); kind.setSelectedItem(r.getString(2)); originalCategory=r.getString(3);
                amount.setText(r.getBigDecimal(4).toPlainString()); party.setText(r.getString(5)); notes.setText(r.getString(6)); originalAccount=(Long)r.getObject(7);
            }
        }
        try (PreparedStatement s = db.prepareStatement("SELECT name FROM categories WHERE active OR name=? ORDER BY name")) {
            s.setString(1,originalCategory); try (ResultSet r=s.executeQuery()) { while(r.next()) category.addItem(r.getString(1)); }
        }
        category.setSelectedItem(originalCategory); fillAccounts(account,false,originalAccount);
        if (originalAccount == null) {
            account.insertItemAt(null,0); account.setSelectedItem(null);
        } else {
            try (PreparedStatement s=db.prepareStatement("SELECT name,account_type,active FROM accounts WHERE id=? AND NOT active")) {
                s.setLong(1,originalAccount); try(ResultSet r=s.executeQuery()) { if(r.next()) { Account a=new Account(originalAccount,r.getString(1),r.getString(2),false); account.addItem(a); account.setSelectedItem(a); } }
            }
        }
        account.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) {
                return super.getListCellRendererComponent(list,value==null?"Unassigned":value,index,selected,focus);
            }
        });
        JTextArea reason=new JTextArea(3,24); reason.setLineWrap(true); reason.setWrapStyleWord(true);
        JPanel fields=new JPanel(new GridLayout(0,2,8,8));
        field(fields,"Date",new DateChooser(date)); field(fields,"Transaction type",kind); field(fields,"Category",category);
        field(fields,"Amount ($)",amount); field(fields,"Payee / customer",party); field(fields,"Notes",notes); field(fields,"Account",account); field(fields,"Reason for edit (required)",new JScrollPane(reason));
        while(JOptionPane.showConfirmDialog(this,fields,"Edit transaction " + id,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION) {
            try {
                Account selected=(Account)account.getSelectedItem();
                saveTransactionEdit(id,LocalDate.parse(date.getText().trim()),(String)kind.getSelectedItem(),(String)category.getSelectedItem(),positive(amount.getText()),party.getText().trim(),notes.getText().trim(),selected==null?null:selected.id(),reason.getText());
                refresh(); return;
            } catch(IllegalArgumentException | ArithmeticException | java.time.format.DateTimeParseException ex) { JOptionPane.showMessageDialog(this,ex.getMessage(),"Unable to save edit",JOptionPane.ERROR_MESSAGE); }
        }
    }

    /** Save the changed record and its audit snapshot in one transaction. */
    private void saveTransactionEdit(long id,LocalDate date,String kind,String category,BigDecimal amount,String party,String notes,Long accountId,String reason) throws SQLException {
        if(reason==null || reason.isBlank()) throw new IllegalArgumentException("Enter a reason for this edit.");
        amount=positive(amount.toPlainString());
        db.setAutoCommit(false);
        try {
            String before;
            try(PreparedStatement s=db.prepareStatement("SELECT (to_jsonb(t) || jsonb_build_object('account_name',COALESCE(a.name,'Unassigned')))::text FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.id=? FOR UPDATE OF t")) {
                s.setLong(1,id); try(ResultSet r=s.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Transaction no longer exists."); before=r.getString(1); }
            }
            try(PreparedStatement s=db.prepareStatement("UPDATE transactions SET entry_date=?,kind=?,category=?,amount=?,party=?,notes=?,account_id=? WHERE id=? AND (account_id IS NOT DISTINCT FROM CAST(? AS BIGINT) OR EXISTS(SELECT 1 FROM accounts WHERE id=? AND active)) AND (category=? OR EXISTS(SELECT 1 FROM categories WHERE name=? AND active))")) {
                bind(s,new Object[]{date,kind,category,amount,party,notes,accountId,id,accountId,accountId,category,category});
                if(s.executeUpdate()!=1) throw new IllegalArgumentException("Choose an active account and category, or keep the original choices.");
            }
            try(PreparedStatement s=db.prepareStatement("INSERT INTO transaction_edits(transaction_id,edited_by,reason,before_record,after_record) SELECT t.id,?,?,CAST(? AS JSONB),to_jsonb(t) || jsonb_build_object('account_name',COALESCE(a.name,'Unassigned')) FROM transactions t LEFT JOIN accounts a ON a.id=t.account_id WHERE t.id=? AND CAST(? AS JSONB) IS DISTINCT FROM (to_jsonb(t) || jsonb_build_object('account_name',COALESCE(a.name,'Unassigned')))")) {
                bind(s,new Object[]{loginName,reason.trim(),before,id,before});
                if(s.executeUpdate()!=1) throw new IllegalArgumentException("No values changed. Change a field or cancel.");
            }
            db.commit();
        } catch(SQLException | RuntimeException ex) { db.rollback(); throw ex; }
        finally { db.setAutoCommit(true); }
    }

    private void viewEditHistory(long id) throws SQLException {
        viewEditHistory(id,false);
    }

    private void viewEditHistory(long id,boolean transfer) throws SQLException {
        DefaultTableModel history=model("Edited at","Edited by","Reason");
        java.util.List<String> snapshots=new java.util.ArrayList<>();
        String[] keys=transfer?new String[]{"transfer_date","from_account_name","to_account_name","amount","notes"}:new String[]{"entry_date","kind","category","amount","party","notes","account_name"};
        String[] labels=transfer?new String[]{"Date","From account","To account","Amount ($)","Notes"}:new String[]{"Date","Type","Category","Amount ($)","Payee / customer","Notes","Account"};
        StringBuilder sql=new StringBuilder("SELECT edited_at,edited_by,reason");
        for(String snapshot:new String[]{"before_record","after_record"}) for(String key:keys) sql.append(",").append(snapshot).append("->>'").append(key).append("'");
        sql.append(transfer?" FROM transfer_edits WHERE transfer_id=? ORDER BY edited_at DESC,id DESC":" FROM transaction_edits WHERE transaction_id=? ORDER BY edited_at DESC,id DESC");
        try(PreparedStatement s=db.prepareStatement(sql.toString())) {
            s.setLong(1,id); try(ResultSet r=s.executeQuery()) {
                while(r.next()) {
                    history.addRow(new Object[]{r.getObject(1),r.getString(2),r.getString(3)});
                    StringBuilder text=new StringBuilder("Reason: ").append(r.getString(3)).append("\n\n");
                    for(int side=0;side<2;side++) {
                        text.append(side==0?"BEFORE\n":"\nAFTER\n");
                        for(int i=0;i<keys.length;i++) text.append(labels[i]).append(": ").append(r.getString(4+side*keys.length+i)).append("\n");
                    }
                    snapshots.add(text.toString());
                }
            }
        }
        if(history.getRowCount()==0) { JOptionPane.showMessageDialog(this,"This record has not been edited."); return; }
        JDialog dialog=new JDialog(this,"Edit history — " + (transfer?"transfer ":"transaction ") + id,true);
        JTable table=new JTable(history); table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTextArea detail=new JTextArea(); detail.setEditable(false); detail.setLineWrap(true); detail.setWrapStyleWord(true);
        table.getSelectionModel().addListSelectionListener(e -> { int row=table.getSelectedRow(); if(row>=0) { detail.setText(snapshots.get(table.convertRowIndexToModel(row))); detail.setCaretPosition(0); } });
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(table),new JScrollPane(detail)); split.setResizeWeight(0.35);
        dialog.add(split); dialog.setSize(750,600); dialog.setLocationRelativeTo(this); table.setRowSelectionInterval(0,0); dialog.setVisible(true);
    }

    private JPanel accountsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JTable accountTable = new JTable(accounts); accountTable.setAutoCreateRowSorter(true);
        JTable transferTable = new JTable(transfers); transferTable.setAutoCreateRowSorter(true);
        JPanel accountArea = new JPanel(new BorderLayout()), transferArea = new JPanel(new BorderLayout());
        JPanel accountActions = new WrapPanel();
        accountActions.add(button("Add account", () -> editAccount(null)));
        accountActions.add(button("Edit account", () -> editAccount(selectedId(accountTable))));
        accountActions.add(button("Delete / archive account", () -> removeAccount(selectedId(accountTable))));
        accountActions.add(button("Restore account", () -> { execute("UPDATE accounts SET active=TRUE WHERE id=?", selectedId(accountTable)); refresh(); }));
        accountArea.add(new JScrollPane(accountTable)); accountArea.add(accountActions, BorderLayout.SOUTH);
        JPanel transferActions = new WrapPanel();
        transferActions.add(button("Transfer between accounts", this::newTransfer));
        transferActions.add(button("Edit selected transfer", () -> editTransfer(selectedId(transferTable))));
        transferActions.add(button("View transfer edit history", () -> viewEditHistory(selectedId(transferTable),true)));
        transferActions.add(button("Delete selected transfer", () -> delete(transferTable, "transfers")));
        transferArea.add(new JLabel(" Transfer history — transfers are excluded from income and expense reports."), BorderLayout.NORTH);
        transferArea.add(new JScrollPane(transferTable)); transferArea.add(transferActions, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, accountArea, transferArea); split.setResizeWeight(0.5);
        panel.add(split);
        JTextArea help = new JTextArea("Balances include opening balances and all recorded entries, including future dates. Credit debt appears negative; a payment from Checking to Credit reduces that debt.\nExisting transactions remain Unassigned until you assign them. Opening balances should represent balances before your recorded transactions.");
        help.setEditable(false); help.setLineWrap(true); help.setWrapStyleWord(true); help.setRows(3); help.setBackground(UIManager.getColor("Panel.background"));
        panel.add(help, BorderLayout.SOUTH); return panel;
    }

    private static long selectedId(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("Select a record first.");
        return ((Number) table.getModel().getValueAt(table.convertRowIndexToModel(row), 0)).longValue();
    }

    private static long selectedTransactionId(JTable table) {
        long id = selectedId(table);
        Object type = table.getModel().getValueAt(table.convertRowIndexToModel(table.getSelectedRow()), 2);
        if ("Transfer In".equals(type) || "Transfer Out".equals(type))
            throw new IllegalArgumentException("Manage this transfer on Accounts & transfers. Both ledger rows belong to the same transfer.");
        return id;
    }

    private static Account requiredAccount(JComboBox<Account> choice) {
        Account account = (Account) choice.getSelectedItem();
        if (account == null) throw new IllegalArgumentException("Add or select an active account first.");
        return account;
    }

    private void fillAccounts(JComboBox<Account> choice, boolean includeArchived, Long preferred) throws SQLException {
        choice.removeAllItems();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT id,name,account_type,active FROM accounts" + (includeArchived ? "" : " WHERE active") + " ORDER BY name")) {
            Account selection = null;
            while (r.next()) {
                Account a = new Account(r.getLong(1), r.getString(2), r.getString(3), r.getBoolean(4)); choice.addItem(a);
                if (preferred != null && a.id() == preferred) selection = a;
            }
            if (selection != null) choice.setSelectedItem(selection);
        }
    }

    private void editAccount(Long id) throws SQLException {
        JTextField name = new JTextField(24), opening = new JTextField("0.00");
        JComboBox<String> type = new JComboBox<>(new String[]{"Checking", "Savings", "Credit", "Cash", "Other"});
        if (id != null) {
            try (PreparedStatement s = db.prepareStatement("SELECT name,account_type,opening_balance FROM accounts WHERE id=?")) {
                s.setLong(1, id); try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("Account no longer exists.");
                    name.setText(r.getString(1)); type.setSelectedItem(r.getString(2)); opening.setText(r.getBigDecimal(3).toPlainString());
                }
            }
        }
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        field(fields, "Account name", name); field(fields, "Type", type); field(fields, "Opening balance ($; negative if owed)", opening);
        while (JOptionPane.showConfirmDialog(this, fields, id == null ? "Add account" : "Edit account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                if (name.getText().isBlank()) throw new IllegalArgumentException("Enter an account name.");
                BigDecimal value = new BigDecimal(opening.getText().trim()).setScale(2, java.math.RoundingMode.UNNECESSARY);
                if (id == null) execute("INSERT INTO accounts(name,account_type,opening_balance) VALUES (?,?,?)", name.getText().trim(), type.getSelectedItem(), value);
                else execute("UPDATE accounts SET name=?,account_type=?,opening_balance=? WHERE id=?", name.getText().trim(), type.getSelectedItem(), value, id);
                refresh(); return;
            } catch (SQLException ex) {
                if (!"23505".equals(ex.getSQLState())) throw ex;
                JOptionPane.showMessageDialog(this, "An account with that name already exists. Choose another name or restore the archived account.");
            } catch (IllegalArgumentException | ArithmeticException ex) { JOptionPane.showMessageDialog(this, "Enter a name and a valid opening balance with at most two decimal places."); }
        }
    }

    private void removeAccount(long id) throws SQLException {
        // Lock the account while checking references so a concurrent transfer cannot be lost.
        if (JOptionPane.showConfirmDialog(this, "Delete this account if unused?\nAccounts with entries or a nonzero opening balance will be archived.\nArchived accounts retain history and balances but cannot receive new entries.", "Delete / archive account", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        db.setAutoCommit(false);
        try {
            boolean used;
            try (PreparedStatement s = db.prepareStatement("SELECT opening_balance FROM accounts WHERE id=? FOR UPDATE")) {
                s.setLong(1, id); try (ResultSet r = s.executeQuery()) { if (!r.next()) throw new SQLException("Account no longer exists."); used = r.getBigDecimal(1).signum() != 0; }
            }
            try (PreparedStatement s = db.prepareStatement("SELECT EXISTS(SELECT 1 FROM transactions WHERE account_id=?) OR EXISTS(SELECT 1 FROM transfers WHERE from_account_id=? OR to_account_id=?) OR EXISTS(SELECT 1 FROM report_templates WHERE account_id=?)")) {
                bind(s, new Object[]{id,id,id,id}); try (ResultSet r = s.executeQuery()) { r.next(); used |= r.getBoolean(1); }
            }
            execute(used ? "UPDATE accounts SET active=FALSE WHERE id=?" : "DELETE FROM accounts WHERE id=?", id);
            db.commit();
        } catch (SQLException ex) { db.rollback(); throw ex; }
        finally { db.setAutoCommit(true); }
        refresh();
    }

    private void newTransfer() throws SQLException {
        JComboBox<Account> source = new JComboBox<>(), destination = new JComboBox<>();
        fillAccounts(source, false, null); fillAccounts(destination, false, null);
        if (source.getItemCount() < 2) throw new IllegalArgumentException("Add at least two active accounts to transfer between them.");
        destination.setSelectedIndex(1);
        JTextField date = new JTextField(LocalDate.now().toString()), amount = new JTextField(), notes = new JTextField(24);
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT COALESCE((SELECT value FROM ledger_settings WHERE name='last_transfer_date'),(SELECT transfer_date::text FROM transfers ORDER BY id DESC LIMIT 1))")) {
            if (r.next() && r.getString(1) != null) {
                try { date.setText(LocalDate.parse(r.getString(1)).toString()); }
                catch (java.time.format.DateTimeParseException ignored) { /* Keep today if the saved date is invalid. */ }
            }
        }
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        field(fields, "Date (YYYY-MM-DD)", new DateChooser(date)); field(fields, "From account", source); field(fields, "To account", destination); field(fields, "Amount ($)", amount); field(fields, "Notes", notes);
        while (JOptionPane.showConfirmDialog(this, fields, "Transfer between accounts", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                Account a = requiredAccount(source), b = requiredAccount(destination);
                if (a.id() == b.id()) throw new IllegalArgumentException("Choose two different accounts.");
                LocalDate day = LocalDate.parse(date.getText().trim()); BigDecimal value = positive(amount.getText());
                // One record represents both sides. It cannot save a debit without its credit.
                db.setAutoCommit(false);
                try {
                    try (PreparedStatement s = db.prepareStatement("INSERT INTO transfers(transfer_date,from_account_id,to_account_id,amount,notes) SELECT ?,a.id,b.id,?,? FROM accounts a,accounts b WHERE a.id=? AND b.id=? AND a.active AND b.active")) {
                        bind(s, new Object[]{day,value,notes.getText().trim(),a.id(),b.id()});
                        if (s.executeUpdate() != 1) throw new IllegalArgumentException("One of these accounts is no longer active.");
                    }
                    execute("INSERT INTO ledger_settings(name,value) VALUES ('last_transfer_date',?) ON CONFLICT(name) DO UPDATE SET value=EXCLUDED.value", day.toString());
                    db.commit();
                } catch (SQLException | RuntimeException ex) { db.rollback(); throw ex; }
                finally { db.setAutoCommit(true); }
                refresh(); return;
            } catch (IllegalArgumentException | ArithmeticException | java.time.format.DateTimeParseException ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
        }
    }

    private void fillTransferEditAccounts(JComboBox<Account> choices,long original) throws SQLException {
        fillAccounts(choices,false,original);
        try(PreparedStatement s=db.prepareStatement("SELECT name,account_type FROM accounts WHERE id=? AND NOT active")) {
            s.setLong(1,original);try(ResultSet r=s.executeQuery()){if(r.next()){Account account=new Account(original,r.getString(1),r.getString(2),false);choices.addItem(account);choices.setSelectedItem(account);}}
        }
    }

    private void editTransfer(long id) throws SQLException {
        JTextField date=new JTextField(10),amount=new JTextField(12),notes=new JTextField(24);
        JComboBox<Account> source=new JComboBox<>(),destination=new JComboBox<>();long fromId,toId;
        try(PreparedStatement s=db.prepareStatement("SELECT transfer_date,from_account_id,to_account_id,amount,notes FROM transfers WHERE id=?")) {
            s.setLong(1,id);try(ResultSet r=s.executeQuery()) {
                if(!r.next())throw new IllegalArgumentException("Transfer no longer exists.");
                date.setText(r.getObject(1).toString());fromId=r.getLong(2);toId=r.getLong(3);amount.setText(r.getBigDecimal(4).toPlainString());notes.setText(r.getString(5));
            }
        }
        fillTransferEditAccounts(source,fromId);fillTransferEditAccounts(destination,toId);
        JTextArea reason=new JTextArea(3,24);reason.setLineWrap(true);reason.setWrapStyleWord(true);
        JPanel fields=new JPanel(new GridLayout(0,2,8,8));
        field(fields,"Date",new DateChooser(date));field(fields,"From account",source);field(fields,"To account",destination);field(fields,"Amount ($)",amount);field(fields,"Notes",notes);field(fields,"Reason for edit (required)",new JScrollPane(reason));
        while(JOptionPane.showConfirmDialog(this,fields,"Edit transfer "+id,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION) {
            try {
                saveTransferEdit(id,LocalDate.parse(date.getText().trim()),requiredAccount(source).id(),requiredAccount(destination).id(),positive(amount.getText()),notes.getText().trim(),reason.getText());refresh();return;
            }catch(IllegalArgumentException | ArithmeticException | java.time.format.DateTimeParseException ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Unable to save transfer edit",JOptionPane.ERROR_MESSAGE);}
        }
    }

    private void saveTransferEdit(long id,LocalDate date,long source,long destination,BigDecimal amount,String notes,String reason) throws SQLException {
        if(reason==null || reason.isBlank())throw new IllegalArgumentException("Enter a reason for this edit.");
        if(source==destination)throw new IllegalArgumentException("Choose two different accounts.");
        amount=positive(amount.toPlainString());
        String snapshot="(to_jsonb(x) || jsonb_build_object('from_account_name',a.name,'to_account_name',b.name))";
        String joined=" FROM transfers x JOIN accounts a ON a.id=x.from_account_id JOIN accounts b ON b.id=x.to_account_id";
        db.setAutoCommit(false);
        try {
            String before;
            try(PreparedStatement s=db.prepareStatement("SELECT "+snapshot+"::text"+joined+" WHERE x.id=? FOR UPDATE OF x")) {
                s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Transfer no longer exists.");before=r.getString(1);}
            }
            try(PreparedStatement s=db.prepareStatement("UPDATE transfers SET transfer_date=?,from_account_id=?,to_account_id=?,amount=?,notes=? WHERE id=? AND (from_account_id=? OR EXISTS(SELECT 1 FROM accounts WHERE id=? AND active)) AND (to_account_id=? OR EXISTS(SELECT 1 FROM accounts WHERE id=? AND active))")) {
                bind(s,new Object[]{date,source,destination,amount,notes,id,source,source,destination,destination});
                if(s.executeUpdate()!=1)throw new IllegalArgumentException("Choose active accounts, or keep the transfer's original accounts.");
            }
            try(PreparedStatement s=db.prepareStatement("INSERT INTO transfer_edits(transfer_id,edited_by,reason,before_record,after_record) SELECT x.id,?,?,CAST(? AS JSONB),"+snapshot+joined+" WHERE x.id=? AND CAST(? AS JSONB) IS DISTINCT FROM "+snapshot)) {
                bind(s,new Object[]{loginName,reason.trim(),before,id,before});
                if(s.executeUpdate()!=1)throw new IllegalArgumentException("No values changed. Change a field or cancel.");
            }
            db.commit();
        }catch(SQLException | RuntimeException ex){db.rollback();throw ex;}
        finally{db.setAutoCommit(true);}
    }

    private JPanel mileagePanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8)), fields = new WrapPanel();
        JTextField date = new JTextField(LocalDate.now().toString(), 10), origin = new JTextField(12), destination = new JTextField(12), purpose = new JTextField(18), miles = new JTextField(7);
        field(fields, "Date (YYYY-MM-DD)", new DateChooser(date)); field(fields, "From", origin); field(fields, "To", destination); field(fields, "Business purpose", purpose); field(fields, "Miles", miles);
        JTable table = new JTable(trips); table.setAutoCreateRowSorter(true);
        JPanel actions = new WrapPanel();
        actions.add(button("Add trip", () -> {
            execute("INSERT INTO mileage(trip_date,origin,destination,purpose,miles) VALUES (?,?,?,?,?)", LocalDate.parse(date.getText().trim()), required(origin), required(destination), required(purpose), positive(miles.getText()));
            origin.setText(""); destination.setText(""); purpose.setText(""); miles.setText(""); refresh();
        }));
        actions.add(button("Delete selected", () -> delete(table, "mileage")));
        p.add(fields, BorderLayout.NORTH); p.add(new JScrollPane(table)); p.add(actions, BorderLayout.SOUTH); return p;
    }

    private JPanel reportPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8)), filters = new WrapPanel();
        JPanel toolbar = new WrapPanel();
        field(toolbar, "Report", templates);
        templates.addActionListener(e -> clearReport());
        toolbar.add(button("New template", () -> editTemplate(null)));
        toolbar.add(button("Edit template", () -> editTemplate(selectedTemplate())));
        toolbar.add(button("Delete template", () -> {
            ReportTemplate selected = selectedTemplate();
            if (JOptionPane.showConfirmDialog(this, "Delete report template \"" + selected.name() + "\"?\nTransactions will be kept.", "Delete template", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                execute("DELETE FROM report_templates WHERE id=?", selected.id()); reloadTemplates(null);
            }
        }));
        field(filters, "From", new DateChooser(from)); field(filters, "Through (inclusive)", new DateChooser(through));
        JPanel amounts = new WrapPanel();
        field(amounts, "Transaction amount ($)", amountComparison); amounts.add(reportAmount);
        reportAmount.setEnabled(false); reportAmount.setToolTipText("Compare each transaction's amount, before category totals are calculated.");
        amountComparison.addActionListener(e -> { reportAmount.setEnabled(amountComparison.getSelectedIndex() != 0); clearReport(); });
        reportAmount.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { clearReport(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { clearReport(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { clearReport(); }
        });
        reportAmount.addActionListener(e -> { try { runReport(); } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Unable to run report", JOptionPane.ERROR_MESSAGE); } });
        amounts.add(button("Run report", this::runReport)); amounts.add(button("Export report CSV", this::export));
        JPanel controls = new JPanel(new BorderLayout()); controls.add(toolbar,BorderLayout.NORTH); controls.add(filters); controls.add(amounts,BorderLayout.SOUTH);
        reportRanges=new DateRangeControls(db,from,through,this::clearReport);
        JPanel header=new JPanel(new BorderLayout());header.add(reportRanges,BorderLayout.NORTH);header.add(controls);
        JTable table = new JTable(report); table.setAutoCreateRowSorter(true);
        p.add(header, BorderLayout.NORTH); p.add(new JScrollPane(table)); p.add(totals, BorderLayout.SOUTH); return p;
    }

    private void clearReport() { report.setRowCount(0); totals.setText("Run report to view results."); }

    private ReportTemplate selectedTemplate() {
        ReportTemplate selected = (ReportTemplate) templates.getSelectedItem();
        if (selected == null) throw new IllegalArgumentException("Create or select a report template first.");
        return selected;
    }

    private void reloadTemplates(Long preferred) throws SQLException {
        ReportTemplate old = (ReportTemplate) templates.getSelectedItem();
        long selectedId = preferred != null ? preferred : old == null ? -1 : old.id();
        templates.removeAllItems();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT id,name,kind,ARRAY(SELECT category FROM report_template_categories c WHERE c.template_id=rt.id UNION SELECT rt.category WHERE rt.category IS NOT NULL ORDER BY 1),detail,account_id,account_totals FROM report_templates rt ORDER BY name")) {
            ReportTemplate selection = null;
            while (r.next()) {
                java.sql.Array values = r.getArray(4);
                java.util.List<String> selectedCategories;
                try { selectedCategories = java.util.List.of((String[]) values.getArray()); } finally { values.free(); }
                ReportTemplate t = new ReportTemplate(r.getLong(1), r.getString(2), r.getString(3), selectedCategories, r.getBoolean(5), (Long) r.getObject(6), r.getBoolean(7));
                templates.addItem(t); if (t.id() == selectedId) selection = t;
            }
            if (selection != null) templates.setSelectedItem(selection);
        }
        clearReport();
    }

    private void editTemplate(ReportTemplate existing) throws SQLException {
        JTextField name = new JTextField(existing == null ? "" : existing.name(), 24);
        JComboBox<String> kind = new JComboBox<>(new String[]{"All types", "Expense", "Income"});
        kind.setSelectedItem(existing == null || existing.kind() == null ? "All types" : existing.kind());
        JCheckBox allCategories = new JCheckBox("All categories (including future categories)", existing == null || existing.selectedCategories().isEmpty());
        JPanel categoryChoices = new JPanel(new GridLayout(0, 1));
        java.util.List<JCheckBox> categoryChecks = new java.util.ArrayList<>();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT name FROM categories ORDER BY name")) {
            while (r.next()) {
                String value = r.getString(1);
                JCheckBox check = new JCheckBox(value, existing != null && existing.selectedCategories().contains(value));
                check.setEnabled(!allCategories.isSelected()); categoryChecks.add(check); categoryChoices.add(check);
            }
        }
        allCategories.addActionListener(e -> categoryChecks.forEach(check -> check.setEnabled(!allCategories.isSelected())));
        JComboBox<String> layout = new JComboBox<>(new String[]{"Transaction details", "Category totals", "Account In/Out totals (excludes transfers)"});
        layout.setSelectedIndex(existing != null && existing.accountTotals() ? 2 : existing == null || existing.detail() ? 0 : 1);
        JComboBox<Account> account = new JComboBox<>(); fillAccounts(account, true, existing == null ? null : existing.accountId());
        account.insertItemAt(null, 0);
        account.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "All accounts (including Unassigned)" : value, index, selected, focus);
            }
        });
        if (existing == null || existing.accountId() == null) account.setSelectedItem(null);
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        field(fields, "Template name", name); field(fields, "Transaction type", kind); field(fields, "Show", layout);
        field(fields, "Account", account);
        JPanel categoryPanel = new JPanel(new BorderLayout(4,4)); categoryPanel.setBorder(BorderFactory.createTitledBorder("Categories — match any checked category"));
        categoryPanel.add(allCategories, BorderLayout.NORTH);
        JScrollPane categoryScroll = new JScrollPane(categoryChoices); categoryScroll.setPreferredSize(new Dimension(360,180)); categoryPanel.add(categoryScroll);
        JPanel editor = new JPanel(new BorderLayout(8,12)); editor.add(fields, BorderLayout.NORTH); editor.add(categoryPanel);
        while (JOptionPane.showConfirmDialog(this, editor, existing == null ? "New report template" : "Edit report template", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (name.getText().isBlank()) { JOptionPane.showMessageDialog(this, "Enter a template name."); continue; }
            java.util.List<String> chosen = allCategories.isSelected() ? java.util.List.of() : categoryChecks.stream().filter(JCheckBox::isSelected).map(JCheckBox::getText).toList();
            if (!allCategories.isSelected() && chosen.isEmpty()) { JOptionPane.showMessageDialog(this,"Check at least one category, or choose All categories."); continue; }
            String type = kind.getSelectedIndex() == 0 ? null : (String) kind.getSelectedItem();
            Account selectedAccount = (Account) account.getSelectedItem();
            Long accountId = selectedAccount == null ? null : selectedAccount.id();
            try {
                long saved = saveReportTemplate(existing == null ? null : existing.id(),name.getText().trim(),type,chosen,layout.getSelectedIndex()==0,accountId,layout.getSelectedIndex()==2);
                reloadTemplates(saved);
                return;
            } catch (SQLException ex) {
                if (!"23505".equals(ex.getSQLState())) throw ex;
                JOptionPane.showMessageDialog(this, "A template with that name already exists. Choose another name.");
            }
        }
    }

    private long saveReportTemplate(Long id,String name,String kind,java.util.List<String> chosen,boolean detail,Long accountId) throws SQLException {
        return saveReportTemplate(id,name,kind,chosen,detail,accountId,false);
    }

    private long saveReportTemplate(Long id,String name,String kind,java.util.List<String> chosen,boolean detail,Long accountId,boolean accountTotals) throws SQLException {
        db.setAutoCommit(false);
        try {
            if(id==null) {
                try(PreparedStatement s=db.prepareStatement("INSERT INTO report_templates(name,kind,detail,account_id) VALUES (?,?,?,?) RETURNING id")) {
                    bind(s,new Object[]{name,kind,detail,accountId}); try(ResultSet r=s.executeQuery()) { r.next(); id=r.getLong(1); }
                }
            } else {
                try(PreparedStatement s=db.prepareStatement("UPDATE report_templates SET name=?,kind=?,category=NULL,detail=?,account_id=? WHERE id=?")) {
                    bind(s,new Object[]{name,kind,detail,accountId,id}); if(s.executeUpdate()!=1) throw new SQLException("Template no longer exists.");
                }
            }
            execute("DELETE FROM report_template_categories WHERE template_id=?",id);
            execute("UPDATE report_templates SET account_totals=? WHERE id=?",accountTotals,id);
            for(String category:new java.util.LinkedHashSet<>(chosen)) execute("INSERT INTO report_template_categories(template_id,category) VALUES (?,?)",id,category);
            db.commit(); return id;
        } catch(SQLException | RuntimeException ex) { db.rollback(); throw ex; }
        finally { db.setAutoCommit(true); }
    }

    private void refresh() throws SQLException {
        Object selected = categories.getSelectedItem(); categories.removeAllItems();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT name FROM categories WHERE active ORDER BY name")) { while (r.next()) categories.addItem(r.getString(1)); }
        if (selected != null) categories.setSelectedItem(selected);
        // Combine both transfer sides with transactions before calculating account balances.
        // Without timestamps, use transfers first on each day, then transactions in ID order.
        load(transactions, """
                WITH events AS (
                    SELECT id,account_id,entry_date AS day,1 AS event_kind,
                           CASE WHEN kind='Income' THEN amount ELSE -amount END AS delta,
                           kind,category,amount,party,notes FROM transactions
                    UNION ALL
                    SELECT x.id,x.from_account_id,x.transfer_date,0,-x.amount,
                           'Transfer Out','Transfer',x.amount,'To: ' || a.name,x.notes
                    FROM transfers x JOIN accounts a ON a.id=x.to_account_id
                    UNION ALL
                    SELECT x.id,x.to_account_id,x.transfer_date,0,x.amount,
                           'Transfer In','Transfer',x.amount,'From: ' || a.name,x.notes
                    FROM transfers x JOIN accounts a ON a.id=x.from_account_id
                ), running AS (
                    SELECT events.*,
                           SUM(delta) OVER (PARTITION BY account_id ORDER BY day,event_kind,id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS movement
                    FROM events
                )
                SELECT r.id,r.day,r.kind,r.category,r.amount,r.party,r.notes,
                       COALESCE(a.name,'Unassigned'),a.opening_balance+r.movement,
                       CASE WHEN (r.event_kind=1 AND EXISTS(SELECT 1 FROM transaction_edits e WHERE e.transaction_id=r.id))
                              OR (r.event_kind=0 AND EXISTS(SELECT 1 FROM transfer_edits e WHERE e.transfer_id=r.id)) THEN 'Edited' ELSE '' END
                FROM running r
                LEFT JOIN accounts a ON a.id=r.account_id
                ORDER BY r.day DESC,r.event_kind DESC,r.id DESC,r.kind
                """);
        Account selectedAccount = (Account) transactionAccount.getSelectedItem();
        fillAccounts(transactionAccount, false, selectedAccount == null ? null : selectedAccount.id());
        load(accounts, "SELECT id,name,account_type,opening_balance,balance,CASE WHEN active THEN 'Active' ELSE 'Archived' END FROM account_balances ORDER BY name");
        load(transfers, "SELECT x.id,x.transfer_date,a.name,b.name,x.amount,x.notes,CASE WHEN EXISTS(SELECT 1 FROM transfer_edits e WHERE e.transfer_id=x.id) THEN 'Edited' ELSE '' END FROM transfers x JOIN accounts a ON a.id=x.from_account_id JOIN accounts b ON b.id=x.to_account_id ORDER BY x.transfer_date DESC,x.id DESC");
        load(trips, "SELECT id,trip_date,origin,destination,purpose,miles FROM mileage ORDER BY trip_date DESC,id DESC");
        loadHistory(payeeHistory, "party"); loadHistory(notesHistory, "notes");
        reloadTemplates(null);
    }

    private void loadHistory(java.util.List<String> history, String column) throws SQLException {
        // Only internal column names are used. Suggestions come from this profile's saved entries.
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT " + column + " FROM transactions WHERE length(trim(" + column + "))>0 GROUP BY " + column + " ORDER BY MAX(id) DESC")) {
            history.clear(); while (r.next()) history.add(r.getString(1));
        }
    }

    private void manageCategories() throws SQLException {
        JDialog dialog = new JDialog(this, "Manage categories", true);
        DefaultListModel<String> names = new DefaultListModel<>();
        JList<String> list = new JList<>(names);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Action reload = () -> {
            names.clear();
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT name FROM categories WHERE active ORDER BY name")) {
                while (r.next()) names.addElement(r.getString(1));
            }
            refresh();
            report.setRowCount(0); totals.setText("Run report to refresh category totals.");
        };
        try { reload.run(); } catch (Exception ex) { throw new SQLException(ex); }
        JPanel actions = new JPanel();
        actions.add(button("Add", () -> {
            String name = JOptionPane.showInputDialog(dialog, "Category name:");
            if (name == null) return;
            name = categoryName(name);
            execute("INSERT INTO categories(name) VALUES (?) ON CONFLICT(name) DO UPDATE SET active=TRUE", name);
            reload.run(); list.setSelectedValue(name, true);
        }));
        actions.add(button("Rename", () -> {
            String old = selectedCategory(list);
            String name = JOptionPane.showInputDialog(dialog, "New name (existing transactions will use this name):", old);
            if (name == null) return;
            name = categoryName(name);
            if (name.equals(old)) return;
            // Insert, reassign, and delete together so the original foreign key remains valid.
            db.setAutoCommit(false);
            try {
                execute("INSERT INTO categories(name) VALUES (?)", name);
                execute("UPDATE transactions SET category=? WHERE category=?", name, old);
                execute("UPDATE report_templates SET category=? WHERE category=?", name, old);
                execute("UPDATE report_template_categories SET category=? WHERE category=?",name,old);
                execute("UPDATE ledger_settings SET value=? WHERE name=? AND value=?",name,IncomeScope.KEY,old);
                execute("DELETE FROM categories WHERE name=?", old);
                db.commit();
            } catch (SQLException ex) {
                db.rollback();
                if ("23505".equals(ex.getSQLState())) throw new IllegalArgumentException("That category already exists. Choose a different name.");
                throw ex;
            } finally { db.setAutoCommit(true); }
            reload.run(); list.setSelectedValue(name, true);
        }));
        actions.add(button("Remove from list", () -> {
            String name = selectedCategory(list);
            if (JOptionPane.showConfirmDialog(dialog, "Remove " + name + " from the category dropdown?\nExisting transactions and reports will keep this category.\nYou can restore it later by adding the same name.", "Remove category", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            execute("UPDATE categories SET active=FALSE WHERE name=?", name);
            reload.run();
        }));
        actions.add(button("Close", dialog::dispose));
        dialog.add(new JLabel(" Add, rename, or remove categories. Paycheck is the Income default."), BorderLayout.NORTH);
        dialog.add(new JScrollPane(list)); dialog.add(actions, BorderLayout.SOUTH);
        dialog.setSize(620, 400); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }

    private static String categoryName(String name) {
        if (name.isBlank()) throw new IllegalArgumentException("Enter a category name.");
        return name.trim();
    }

    private static String selectedCategory(JList<String> list) {
        String name = list.getSelectedValue();
        if (name == null) throw new IllegalArgumentException("Select a category first.");
        if ("Paycheck".equals(name)) throw new IllegalArgumentException("Paycheck is reserved as the default category for Income.");
        return name;
    }

    private void runReport() throws SQLException {
        ReportTemplate template = selectedTemplate();
        reportRanges.applyToDates();
        LocalDate start = LocalDate.parse(from.getText().trim()), end = LocalDate.parse(through.getText().trim());
        if (start.isAfter(end)) throw new IllegalArgumentException("From date must be on or before the through date.");
        String where = " WHERE entry_date BETWEEN ? AND ?";
        java.util.List<Object> parameters = new java.util.ArrayList<>(); parameters.add(start); parameters.add(end);
        IncomeScope scope=IncomeScope.load(db);
        where+=scope.sql();if(scope.category()!=null)parameters.add(scope.category());
        if (template.kind() != null) { where += " AND kind=?"; parameters.add(template.kind()); }
        if (!template.selectedCategories().isEmpty()) {
            where += " AND category IN (" + String.join(",", java.util.Collections.nCopies(template.selectedCategories().size(),"?")) + ")";
            parameters.addAll(template.selectedCategories());
        }
        if (template.accountId() != null) { where += " AND account_id=?"; parameters.add(template.accountId()); }
        String amountDescription = "Any amount";
        if (amountComparison.getSelectedIndex() != 0) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(reportAmount.getText().trim()).setScale(2, java.math.RoundingMode.UNNECESSARY);
                if (amount.signum() < 0 || amount.compareTo(new BigDecimal("999999999999.99")) > 0) throw new IllegalArgumentException();
            } catch (IllegalArgumentException | ArithmeticException ex) {
                throw new IllegalArgumentException("Enter an amount from 0 to 999999999999.99 with at most two decimal places.");
            }
            String operator = switch (amountComparison.getSelectedIndex()) { case 1 -> ">"; case 2 -> "<"; case 3 -> "="; default -> throw new IllegalStateException("Unknown amount comparison."); };
            where += " AND amount " + operator + " ?"; parameters.add(amount);
            amountDescription = amountComparison.getSelectedItem() + " $" + amount.toPlainString();
        }
        amountDescription+=" | "+scope.description();
        if(template.accountTotals()) {
            runAccountTotals(template,start,end,where,parameters,amountDescription); return;
        }
        report.setRowCount(0);
        if (template.detail()) {
            report.setColumnIdentifiers(new String[]{"ID", "Date", "Type", "Category", "Amount", "Payee / customer", "Notes", "Account"});
            load(report, "SELECT id,entry_date,kind,category,amount,party,notes,COALESCE((SELECT name FROM accounts a WHERE a.id=transactions.account_id),'Unassigned') FROM transactions" + where + " ORDER BY entry_date,id", parameters.toArray());
        } else {
            report.setColumnIdentifiers(new String[]{"Type", "Category", "Total"});
            load(report, "SELECT kind,category,SUM(amount) FROM transactions" + where + " GROUP BY kind,category ORDER BY kind,category", parameters.toArray());
        }
        BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO;
        for (int i = 0; i < report.getRowCount(); i++) {
            BigDecimal value = (BigDecimal) report.getValueAt(i, template.detail() ? 4 : 2);
            if ("Income".equals(report.getValueAt(i, template.detail() ? 2 : 0))) income = income.add(value); else expense = expense.add(value);
        }
        BigDecimal mileage;
        try (PreparedStatement s = db.prepareStatement("SELECT COALESCE(SUM(miles),0) FROM mileage WHERE trip_date BETWEEN ? AND ?")) {
            s.setObject(1, start); s.setObject(2, end);
            try (ResultSet r = s.executeQuery()) { r.next(); mileage = r.getBigDecimal(1); }
        }
        reportSummary = template.name() + " | Period: " + start + " through " + end + " | Amount: " + amountDescription + " | Matching income: $" + income.toPlainString() + " | Matching expenses: $" + expense.toPlainString() + " | Net: $" + income.subtract(expense).toPlainString() + " | All business miles in period: " + mileage.toPlainString();
        totals.setText(reportSummary);
    }

    private void runAccountTotals(ReportTemplate template,LocalDate start,LocalDate end,String where,java.util.List<Object> parameters,String amountDescription) throws SQLException {
        String accountFilter="";
        if(template.accountId()!=null) { accountFilter=" WHERE a.id=?"; parameters.add(template.accountId()); }
        String unassigned=template.accountId()==null ? " UNION ALL SELECT 'Unassigned',money_in,money_out FROM grouped WHERE account_id IS NULL" : "";
        report.setRowCount(0); report.setColumnIdentifiers(new String[]{"Account","Total In ($)","Total Out ($)"});
        load(report,"""
                WITH events AS (
                    SELECT entry_date,kind,category,amount,account_id FROM transactions
                ), filtered AS (SELECT * FROM events
                """ + where + """
                ), grouped AS (
                    SELECT account_id,
                           SUM(CASE WHEN kind='Income' THEN amount ELSE 0 END) AS money_in,
                           SUM(CASE WHEN kind='Expense' THEN amount ELSE 0 END) AS money_out
                    FROM filtered GROUP BY account_id
                )
                SELECT a.name,COALESCE(g.money_in,0),COALESCE(g.money_out,0)
                FROM accounts a LEFT JOIN grouped g ON g.account_id=a.id
                """ + accountFilter + unassigned + " ORDER BY 1",parameters.toArray());
        BigDecimal in=BigDecimal.ZERO,out=BigDecimal.ZERO;
        for(int row=0;row<report.getRowCount();row++) { in=in.add((BigDecimal)report.getValueAt(row,1)); out=out.add((BigDecimal)report.getValueAt(row,2)); }
        reportSummary=template.name()+" | Period: "+start+" through "+end+" | Amount: "+amountDescription+" | Total In: $"+in.toPlainString()+" | Total Out: $"+out.toPlainString()+" | Transfers and opening balances excluded.";
        totals.setText(reportSummary);
    }

    private void export() throws Exception {
        runReport();
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new File("business-report.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file.exists() && JOptionPane.showConfirmDialog(this, "Replace existing file?", "Export", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            w.write(csv(reportSummary)); w.newLine();
            for (int col = 0; col < report.getColumnCount(); col++) { if (col > 0) w.write(","); w.write(csv(report.getColumnName(col))); } w.newLine();
            for (int row = 0; row < report.getRowCount(); row++) {
                for (int col = 0; col < report.getColumnCount(); col++) { if (col > 0) w.write(","); w.write(csv(report.getValueAt(row, col))); } w.newLine();
            }
        }
        JOptionPane.showMessageDialog(this, "Report exported.");
    }

    static String csv(Object value) {
        String text = String.valueOf(value);
        // Prevent spreadsheet applications from interpreting user text as formulas.
        if (text.stripLeading().matches("^[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private void delete(JTable table, String relation) throws SQLException {
        int row = table.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("Select a record first.");
        Object id = table.getModel().getValueAt(table.convertRowIndexToModel(row), 0);
        if (JOptionPane.showConfirmDialog(this, "Permanently delete record " + id + "?", "Delete record", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            execute("DELETE FROM " + relation + " WHERE id=?", id); refresh();
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (PreparedStatement s = db.prepareStatement(sql)) { bind(s, values); s.executeUpdate(); }
    }
    private void load(DefaultTableModel model, String sql, Object... values) throws SQLException {
        try (PreparedStatement s = db.prepareStatement(sql)) {
            bind(s, values);
            try (ResultSet r = s.executeQuery()) {
                model.setRowCount(0);
                while (r.next()) { Object[] row = new Object[model.getColumnCount()]; for (int i = 0; i < row.length; i++) row[i] = r.getObject(i + 1); model.addRow(row); }
            }
        }
    }
    private static void bind(PreparedStatement s, Object[] values) throws SQLException { for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]); }
    static BigDecimal positive(String text) {
        BigDecimal value = new BigDecimal(text.trim()).setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (value.signum() <= 0) throw new IllegalArgumentException("Amount / miles must be greater than zero.");
        return value;
    }
    private static String required(JTextField field) { if (field.getText().isBlank()) throw new IllegalArgumentException("From, to, and business purpose are required."); return field.getText().trim(); }
    private static DefaultTableModel model(String... names) {
        return new DefaultTableModel(names, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                if ("Running balance ($)".equals(getColumnName(c))) return BigDecimal.class;
                for (int row = 0; row < getRowCount(); row++) {
                    Object value = getValueAt(row,c); if (value != null) return value.getClass();
                }
                return Object.class;
            }
        };
    }
    private static void field(JPanel p, String name, JComponent component) {
        if(p instanceof WrapPanel wrap)wrap.addField(name,component);
        else {p.add(new JLabel(name));p.add(component);}
    }

    /** Paints a suggestion without inserting it into the document until Tab is pressed. */
    static final class GhostField extends JTextField {
        private final java.util.function.Supplier<java.util.List<String>> history;
        private boolean dismissed;
        GhostField(int columns, java.util.function.Supplier<java.util.List<String>> history) {
            super(columns); this.history = history;
            setToolTipText("Previous entries appear in gray. Tab accepts and moves to the next field; Escape dismisses.");
            getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void changed() { dismissed = false; repaint(); }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            });
            addCaretListener(e -> { dismissed = false; repaint(); });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { dismissed = false; repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
            });
            java.util.Set<AWTKeyStroke> forward = new java.util.HashSet<>(getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
            forward.remove(AWTKeyStroke.getAWTKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0));
            setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forward);
            getInputMap().put(KeyStroke.getKeyStroke("TAB"), "accept-history");
            getActionMap().put("accept-history", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { acceptCompletion(); transferFocus(); }
            });
            getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "dismiss-history");
            getActionMap().put("dismiss-history", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { dismissed = true; repaint(); }
            });
        }
        String suggestion() {
            String prefix = getText();
            if (dismissed || prefix.isBlank() || getSelectionStart() != getSelectionEnd() || getCaretPosition() != prefix.length()) return null;
            for (String value : history.get()) {
                if (value.length() > prefix.length() && value.regionMatches(true, 0, prefix, 0, prefix.length()) && !value.contains("\n") && !value.contains("\r")) return value;
            }
            return null;
        }
        boolean acceptCompletion() {
            String value = suggestion();
            if (value == null) return false;
            setText(value); setCaretPosition(value.length()); return true;
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!isFocusOwner() || !isEnabled()) return;
            String value = suggestion(); if (value == null) return;
            try {
                java.awt.geom.Rectangle2D end = modelToView2D(getText().length()); if (end == null) return;
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    Insets insets = getInsets(); g.clipRect(insets.left, insets.top, getWidth()-insets.left-insets.right, getHeight()-insets.top-insets.bottom);
                    Color gray = UIManager.getColor("TextField.inactiveForeground"); g.setColor(gray == null ? Color.GRAY : gray); g.setFont(getFont());
                    FontMetrics fm = g.getFontMetrics();
                    float baseline = (float)(end.getY() + (end.getHeight()-fm.getHeight())/2 + fm.getAscent());
                    g.drawString(value.substring(getText().length()), (float)end.getX(), baseline);
                } finally { g.dispose(); }
            } catch (javax.swing.text.BadLocationException ignored) { /* The next repaint follows document updates. */ }
        }
    }

    /** Keeps manual date entry available while providing an adjacent calendar button. */
    static final class DateChooser extends JPanel {
        private final JTextField text;
        DateChooser(JTextField text) {
            super(new BorderLayout(4, 0)); this.text = text;
            JButton open = new JButton(new Icon() {
                public int getIconWidth() { return 16; }
                public int getIconHeight() { return 16; }
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    g.setColor(c.getForeground()); g.drawRect(x + 1, y + 3, 13, 11);
                    g.drawLine(x + 1, y + 6, x + 14, y + 6);
                    g.drawLine(x + 4, y + 1, x + 4, y + 4); g.drawLine(x + 11, y + 1, x + 11, y + 4);
                    for (int row = 0; row < 2; row++) for (int col = 0; col < 3; col++) g.fillRect(x + 4 + col * 3, y + 8 + row * 3, 1, 1);
                }
            });
            open.setMargin(new Insets(2, 5, 2, 5)); open.setToolTipText("Choose date");
            open.getAccessibleContext().setAccessibleName("Choose date");
            open.addActionListener(e -> showCalendar());
            add(text); add(open, BorderLayout.EAST);
        }
        LocalDate selectedDate() {
            try { return LocalDate.parse(text.getText().trim()); }
            catch (java.time.format.DateTimeParseException ex) { return LocalDate.now(); }
        }
        private void showCalendar() {
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Choose date", Dialog.ModalityType.APPLICATION_MODAL);
            CalendarPanel calendar = new CalendarPanel(selectedDate(), day -> { text.setText(day.toString()); dialog.dispose(); });
            JButton cancel = new JButton("Cancel"); cancel.addActionListener(e -> dialog.dispose());
            JPanel bottom = new JPanel(); bottom.add(cancel);
            dialog.add(calendar); dialog.add(bottom, BorderLayout.SOUTH);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.pack(); dialog.setResizable(false); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
        }
    }

    static final class CalendarPanel extends JPanel {
        private final LocalDate selected;
        private final java.util.function.Consumer<LocalDate> choose;
        private YearMonth month;
        private final JLabel title = new JLabel("", SwingConstants.CENTER);
        private final JPanel days = new JPanel(new GridLayout(7, 7, 3, 3));
        CalendarPanel(LocalDate selected, java.util.function.Consumer<LocalDate> choose) {
            super(new BorderLayout(6, 6)); this.selected = selected; this.choose = choose; month = YearMonth.from(selected);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JButton previous = new JButton("<"), next = new JButton(">");
            previous.setToolTipText("Previous month"); next.setToolTipText("Next month");
            previous.addActionListener(e -> changeMonth(-1)); next.addActionListener(e -> changeMonth(1));
            JPanel heading = new JPanel(new BorderLayout(6, 0)); heading.add(previous, BorderLayout.WEST); heading.add(title); heading.add(next, BorderLayout.EAST);
            JButton today = new JButton("Today"); today.addActionListener(e -> choose.accept(LocalDate.now()));
            add(heading, BorderLayout.NORTH); add(days); add(today, BorderLayout.SOUTH); render();
        }
        void changeMonth(int delta) { month = month.plusMonths(delta); render(); }
        YearMonth displayedMonth() { return month; }
        private void render() {
            title.setText(month.format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.getDefault())));
            days.removeAll();
            for (String weekday : new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}) days.add(new JLabel(weekday, SwingConstants.CENTER));
            int offset = month.atDay(1).getDayOfWeek().getValue() % 7;
            for (int cell = 0; cell < 42; cell++) {
                int number = cell - offset + 1;
                if (number < 1 || number > month.lengthOfMonth()) { days.add(new JLabel()); continue; }
                LocalDate day = month.atDay(number);
                JButton button = new JButton(Integer.toString(number));
                button.setMargin(new Insets(5, 7, 5, 7)); button.setToolTipText(day.toString());
                if (day.equals(selected)) { button.setBorder(BorderFactory.createLineBorder(UIManager.getColor("textHighlight"), 2)); button.setFont(button.getFont().deriveFont(Font.BOLD)); }
                button.addActionListener(e -> choose.accept(day)); days.add(button);
            }
            revalidate(); repaint();
        }
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private JButton button(String label, Action action) {
        JButton b = new JButton(label); b.addActionListener(e -> { try { action.run(); } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Unable to complete action", JOptionPane.ERROR_MESSAGE); } }); return b;
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                AppPaths.initialize();
                AppTheme.initialize();
                LocalDatabase.Server server = LocalDatabase.openServer();
                if (server == null) return;
                LoginStore.Session session;
                try (LoginStore logins = new LoginStore(server)) { session = LoginWindow.showLogin(logins); }
                if (session == null) return;
                Connection db = session.connection();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { db.close(); } catch (SQLException ignored) {} }));
                BusinessLedger ledger = new BusinessLedger(db);
                ledger.loginName = session.name();
                ledger.setTitle("Business Ledger " + AppVersion.version() + " - " + session.name()); ledger.setVisible(true);
            } catch (Exception | LinkageError ex) {
                JOptionPane.showMessageDialog(null, ex.toString(), "Business Ledger startup failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}

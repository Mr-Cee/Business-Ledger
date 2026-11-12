package com.businessledger;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;

/** Two-step column mapping and validated preview; Import is the only write action. */
final class CsvImportWizard extends JDialog {
    private record Column(int index,String name){@Override public String toString(){return index<0?"Not mapped — ignore":(index+1)+": "+(name.isBlank()?"(unnamed)":name);}}
    private record Account(long id,String name){@Override public String toString(){return name;}}
    private final Connection db;
    private final CsvImport.Table csv;
    private final Map<String,JComboBox<Column>> mappings=new LinkedHashMap<>();
    private final JComboBox<String> dates=new JComboBox<>(new String[]{"uuuu-MM-dd","M/d/uuuu","M/d/uu","d/M/uuuu","d/M/uu","uuuuMMdd"});
    private final JComboBox<String> amountMode=new JComboBox<>(new String[]{"Signed amount: + income, - expense","Signed amount: + expense, - income","All amounts are expenses","All amounts are income","Separate Debit and Credit columns"});
    private final JComboBox<String> defaultCategory=new JComboBox<>();
    private final JComboBox<Account> defaultAccount=new JComboBox<>();
    private final JCheckBox createCategories=new JCheckBox("Create missing CSV categories when importing",true),skipDuplicates=new JCheckBox("Skip exact duplicates (same date, type, category, amount, payee, notes, account)",true);
    private final CardLayout cards=new CardLayout();
    private final JPanel content=new JPanel(cards);
    private final JButton back=new JButton("Back to mapping"),previewButton=new JButton("Validate & preview"),importButton=new JButton("Import transactions"),cancel=new JButton("Cancel");
    private final JTextArea status=new JTextArea(3,0),issues=new JTextArea(5,0);
    private final DefaultTableModel previewRows=readonly("Preview row","Date","Type","Category","Amount ($)","Payee / customer","Notes","Account");
    private final Map<Long,String> accountNames=new HashMap<>();
    private CsvImport.Preview preview;
    private boolean imported,busy;

    static boolean showImport(JFrame owner,Connection db)throws Exception {
        JFileChooser chooser=new JFileChooser();chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)","csv"));
        if(chooser.showOpenDialog(owner)!=JFileChooser.APPROVE_OPTION)return false;
        Path file=chooser.getSelectedFile().toPath();
        JCheckBox header=new JCheckBox("First row contains column names",true);
        JComboBox<String> encoding=new JComboBox<>(new String[]{"UTF-8","windows-1252"}),delimiter=new JComboBox<>(new String[]{"Comma (,)","Semicolon (;)","Tab"});
        JPanel options=new JPanel(new GridLayout(0,2,8,8));options.add(new JLabel("Text encoding"));options.add(encoding);options.add(new JLabel("Delimiter"));options.add(delimiter);options.add(header);
        if(JOptionPane.showConfirmDialog(owner,options,"CSV format",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return false;
        Charset charset=Charset.forName((String)encoding.getSelectedItem());char separator=new char[]{',',';','\t'}[delimiter.getSelectedIndex()];boolean hasHeader=header.isSelected();
        CsvImport.Table table=readWithProgress(owner,()->CsvImport.read(file,charset,separator,hasHeader));
        CsvImportWizard wizard=new CsvImportWizard(owner,db,table,file.getFileName().toString());wizard.setVisible(true);return wizard.imported;
    }
    private static CsvImport.Table readWithProgress(JFrame owner,Callable<CsvImport.Table> read)throws Exception {
        JDialog progress=new JDialog(owner,"Reading CSV…",true);progress.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);JProgressBar bar=new JProgressBar();bar.setIndeterminate(true);progress.add(bar);progress.setSize(320,90);progress.setLocationRelativeTo(owner);
        SwingWorker<CsvImport.Table,Void> worker=new SwingWorker<>(){protected CsvImport.Table doInBackground()throws Exception{return read.call();}protected void done(){progress.dispose();}};
        worker.execute();progress.setVisible(true);
        try{return worker.get();}catch(java.util.concurrent.ExecutionException ex){throw new IllegalArgumentException("Unable to read CSV: "+ex.getCause().getMessage()+". Check encoding and delimiter.",ex.getCause());}
    }
    private CsvImportWizard(JFrame owner,Connection db,CsvImport.Table csv,String file)throws SQLException {
        super(owner,"Import CSV — "+file,true);this.db=db;this.csv=csv;
        HelpGuide.bind(getRootPane(), () -> "Import CSV");
        JPanel fields=new JPanel(new GridLayout(0,2,8,8));
        for(String label:new String[]{"Date","Type (optional)","Category (optional)","Amount","Debit (money out)","Credit (money in)","Payee / customer (optional)","Notes (optional)","Account name (optional)"}) {
            JComboBox<Column> combo=new JComboBox<>();combo.addItem(new Column(-1,""));for(int i=0;i<csv.headers().size();i++)combo.addItem(new Column(i,csv.headers().get(i)));
            combo.setPreferredSize(new Dimension(300,combo.getPreferredSize().height));mappings.put(label,combo);fields.add(new JLabel(label));fields.add(combo);
        }
        guess("Date","date","transactiondate","posteddate","postingdate");guess("Type (optional)","type","transactiontype");guess("Category (optional)","category");guess("Amount","amount","transactionamount");guess("Debit (money out)","debit","withdrawal","withdrawals");guess("Credit (money in)","credit","deposit","deposits");guess("Payee / customer (optional)","payee","customer","merchant","description","name");guess("Notes (optional)","notes","note","memo");guess("Account name (optional)","account","accountname");
        if(column("Amount")<0 && column("Debit (money out)")>=0 && column("Credit (money in)")>=0)amountMode.setSelectedIndex(4);
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT name FROM categories WHERE active ORDER BY name")){while(r.next())defaultCategory.addItem(r.getString(1));}defaultCategory.setSelectedItem("Other");
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id,name FROM accounts WHERE active ORDER BY name")){while(r.next()){Account a=new Account(r.getLong(1),r.getString(2));defaultAccount.addItem(a);accountNames.put(a.id(),a.name());}}
        fields.add(new JLabel("Date format (choose explicitly)"));fields.add(dates);fields.add(new JLabel("Amount handling"));fields.add(amountMode);fields.add(new JLabel("Default category (blank / not mapped)"));fields.add(defaultCategory);fields.add(new JLabel("Default account (blank / not mapped)"));fields.add(defaultAccount);
        JPanel mappingPanel=new JPanel(new BorderLayout(8,8));JTextArea explanation=text("Map the CSV columns you want to keep. Unmapped columns are ignored. A mapped Type overrides the signed-amount rule. Transfers must be entered using Accounts & transfers.",3);mappingPanel.add(explanation,BorderLayout.NORTH);mappingPanel.add(fields);
        JPanel checkboxes=new JPanel(new GridLayout(0,1));checkboxes.add(createCategories);checkboxes.add(skipDuplicates);mappingPanel.add(checkboxes,BorderLayout.SOUTH);
        DefaultTableModel raw=readonly(csv.headers().toArray(String[]::new));for(List<String> row:csv.rows().subList(0,Math.min(25,csv.rows().size())))raw.addRow(row.toArray());
        JTable rawTable=new JTable(raw);rawTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JTabbedPane mappingTabs=new JTabbedPane();mappingTabs.addTab("Column mapping",new JScrollPane(mappingPanel));mappingTabs.addTab("CSV sample (first 25 rows)",new JScrollPane(rawTable));content.add(mappingTabs,"mapping");
        JPanel previewPanel=new JPanel(new BorderLayout(8,8));JTable resultTable=new JTable(previewRows);resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);resultTable.setRowHeight(Math.max(resultTable.getRowHeight(),resultTable.getFontMetrics(resultTable.getFont()).getHeight()+4));
        issues.setEditable(false);issues.setLineWrap(true);issues.setWrapStyleWord(true);JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(resultTable),new JScrollPane(issues));split.setResizeWeight(.8);previewPanel.add(split);content.add(previewPanel,"preview");
        status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setBackground(getBackground());status.setText(csv.rows().size()+" CSV rows loaded. Select mappings, then validate. Nothing is saved until you click Import transactions.");
        back.setEnabled(false);importButton.setEnabled(false);
        back.addActionListener(e->{cards.show(content,"mapping");back.setEnabled(false);previewButton.setEnabled(true);importButton.setEnabled(false);status.setText("Adjust mappings, then validate again.");});
        previewButton.addActionListener(e->prepare());importButton.addActionListener(e->save());cancel.addActionListener(e->{if(!busy)dispose();});
        WrapPanel actions=new WrapPanel();actions.add(back);actions.add(previewButton);actions.add(importButton);actions.add(cancel);
        JPanel bottom=new JPanel(new BorderLayout());bottom.add(status);bottom.add(actions,BorderLayout.SOUTH);add(content);add(bottom,BorderLayout.SOUTH);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosing(java.awt.event.WindowEvent e){if(!busy)dispose();}});
        Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();setSize(Math.min(960,screen.width-60),Math.min(780,screen.height-60));setLocationRelativeTo(owner);
    }
    private static JTextArea text(String value,int rows){JTextArea area=new JTextArea(value,rows,0);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setBackground(UIManager.getColor("Panel.background"));return area;}
    private static DefaultTableModel readonly(String...columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int row,int col){return false;}};}
    private void guess(String target,String...aliases){JComboBox<Column> box=mappings.get(target);for(int i=1;i<box.getItemCount();i++){String name=box.getItemAt(i).name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");if(Arrays.asList(aliases).contains(name)){box.setSelectedIndex(i);return;}}}
    private int column(String label){return ((Column)mappings.get(label).getSelectedItem()).index();}
    private CsvImport.Options options(){Account account=(Account)defaultAccount.getSelectedItem();return new CsvImport.Options(new CsvImport.Mapping(column("Date"),column("Type (optional)"),column("Category (optional)"),column("Amount"),column("Debit (money out)"),column("Credit (money in)"),column("Payee / customer (optional)"),column("Notes (optional)"),column("Account name (optional)")),(String)dates.getSelectedItem(),CsvImport.AmountMode.values()[amountMode.getSelectedIndex()],(String)defaultCategory.getSelectedItem(),account==null?-1:account.id(),createCategories.isSelected(),skipDuplicates.isSelected());}
    private void setBusy(boolean value){busy=value;previewButton.setEnabled(!value);back.setEnabled(!value && preview!=null);importButton.setEnabled(!value && preview!=null && preview.errorCount()==0 && !preview.entries().isEmpty());cancel.setEnabled(!value);}
    private void prepare(){
        CsvImport.Options options=options();setBusy(true);status.setText("Validating CSV rows and checking duplicates…");
        new SwingWorker<CsvImport.Preview,Void>(){
            protected CsvImport.Preview doInBackground()throws Exception{return CsvImport.preview(db,csv,options);}
            protected void done(){
                try{
                    preview=get();previewRows.setRowCount(0);int number=0;
                    for(CsvImport.Entry entry:preview.entries().subList(0,Math.min(200,preview.entries().size())))previewRows.addRow(new Object[]{++number,entry.date(),entry.type(),entry.category(),entry.amount(),entry.party(),entry.notes(),accountNames.get(entry.account())});
                    // Record numbers for invalid rows are listed in the validation details below.
                    issues.setText(preview.errorCount()==0?"All rows validated. Extra CSV columns will be discarded.\nNew categories: "+(preview.newCategories().isEmpty()?"none":String.join(", ",new TreeSet<>(preview.newCategories())))+"\nExact-duplicate detection can skip legitimate identical purchases. Return to mapping and turn it off if you need those rows.":String.join("\n",preview.errors())+(preview.errorCount()>50?"\nOnly the first 50 errors are shown.":""));issues.setCaretPosition(0);
                    int planned=preview.entries().size()-(options.skipDuplicates()?preview.duplicates():0);
                    status.setText(preview.entries().size()+" valid rows; "+preview.errorCount()+" errors; "+preview.duplicates()+" exact duplicate rows. Planned import: "+planned+". Preview displays up to 200 valid rows. Nothing saved yet.");cards.show(content,"preview");
                }catch(Exception ex){preview=null;showError(ex);}
                finally{setBusy(false);previewButton.setEnabled(preview==null);}
            }
        }.execute();
    }
    private void save(){
        if(preview==null || busy)return;setBusy(true);status.setText("Importing transactions…");
        new SwingWorker<CsvImport.Result,Void>(){
            protected CsvImport.Result doInBackground()throws Exception{return CsvImport.commit(db,preview);}
            protected void done(){try{CsvImport.Result result=get();imported=true;JOptionPane.showMessageDialog(CsvImportWizard.this,result.imported()+" transactions imported; "+result.skipped()+" duplicates skipped.");dispose();}catch(Exception ex){showError(ex);setBusy(false);}}
        }.execute();
    }
    private void showError(Exception ex){Throwable cause=ex.getCause()==null?ex:ex.getCause();status.setText("Unable to continue. Nothing imported.");JOptionPane.showMessageDialog(this,cause.getMessage(),"CSV import",JOptionPane.ERROR_MESSAGE);}
}

package com.businessledger;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Filters the existing full ledger model; running balances are never recalculated on a subset. */
final class LedgerFilters extends JPanel {
    private record Choice(String value,String label){@Override public String toString(){return label;}}
    private final JTable ledger;
    private final TableRowSorter<TableModel> sorter;
    private final JTextField search=new JTextField(22),from=new JTextField(10),through=new JTextField(10),minimum=new JTextField(8),maximum=new JTextField(8);
    private final JComboBox<String> type=new JComboBox<>(new String[]{"All types","Income","Expense","Transfers","Transfer In","Transfer Out"});
    private final JComboBox<Choice> category=new JComboBox<>(),account=new JComboBox<>();
    private final JToggleButton more=new JToggleButton("More filters");
    private final WrapPanel advanced=new WrapPanel();
    private final JTextArea status=new JTextArea(" ",2,0);
    private final javax.swing.Timer delayed;
    private boolean updating,choicesDirty=true;
    private Runnable applied=()->{};
    @SuppressWarnings("unchecked")
    LedgerFilters(JTable ledger){
        this(ledger,true);
    }
    @SuppressWarnings("unchecked")
    LedgerFilters(JTable ledger,boolean showAccount){
        super(new BorderLayout(0,3));this.ledger=ledger;
        if(ledger.getRowSorter() instanceof TableRowSorter<?> existing)sorter=(TableRowSorter<TableModel>)existing;
        else{sorter=new TableRowSorter<>(ledger.getModel());ledger.setRowSorter(sorter);}
        sorter.setSortsOnUpdates(true);
        delayed=new javax.swing.Timer(180,e->applyNow());delayed.setRepeats(false);
        WrapPanel basic=new WrapPanel();basic.addField("Search ledger",search);basic.addField("Type",type);basic.add(more);
        JButton clear=new JButton("Clear filters");clear.addActionListener(e->clear());basic.add(clear);add(basic,BorderLayout.NORTH);
        search.setToolTipText("Find all words across ID, date, type, category, amount, payee/customer, notes, account, and edited marker. Case-insensitive; punctuation is literal. Ctrl+F focuses search.");
        advanced.addField("Category",category);if(showAccount)advanced.addField("Account",account);
        advanced.addField("From",new BusinessLedger.DateChooser(from));advanced.addField("Through",new BusinessLedger.DateChooser(through));
        advanced.addField("Min amount ($)",minimum);advanced.addField("Max amount ($)",maximum);advanced.setVisible(false);add(advanced);
        from.setToolTipText("Optional inclusive start date: YYYY-MM-DD");through.setToolTipText("Optional inclusive end date: YYYY-MM-DD");
        minimum.setToolTipText("Inclusive minimum transaction amount, not running balance.");maximum.setToolTipText("Inclusive maximum transaction amount, not running balance.");
        status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setBackground(getBackground());status.setBorder(BorderFactory.createEmptyBorder(0,8,0,8));add(status,BorderLayout.SOUTH);
        more.addActionListener(e->{advanced.setVisible(more.isSelected());revalidate();});
        for(JComboBox<?> box:new JComboBox<?>[]{type,category,account})box.addActionListener(e->changed());
        for(JTextField field:new JTextField[]{search,from,through,minimum,maximum}){
            field.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}});
            field.addActionListener(e->applyNow());
        }
        ledger.getModel().addTableModelListener(e->{choicesDirty=true;delayed.restart();});applyNow();
    }
    void installShortcut(JComponent page){
        page.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F,InputEvent.CTRL_DOWN_MASK),"find-ledger");
        page.getActionMap().put("find-ledger",new AbstractAction(){public void actionPerformed(ActionEvent e){search.requestFocusInWindow();search.selectAll();}});
    }
    private void changed(){if(!updating)delayed.restart();}
    private static String selected(JComboBox<Choice> box){return box.getSelectedItem() instanceof Choice choice?choice.value():null;}
    private void reloadChoices(JComboBox<Choice> box,int column,String all){
        String keep=selected(box);Set<String> values=new TreeSet<>(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        for(int row=0;row<ledger.getModel().getRowCount();row++){Object value=ledger.getModel().getValueAt(row,column);if(value!=null)values.add(value.toString());}
        if(keep!=null)values.add(keep);box.removeAllItems();box.addItem(new Choice(null,all));
        for(String value:values){Choice choice=new Choice(value,value);box.addItem(choice);if(value.equals(keep))box.setSelectedItem(choice);}
    }
    void applyNow(){
        delayed.stop();
        if(choicesDirty){updating=true;try{reloadChoices(category,3,"All categories");reloadChoices(account,7,"All accounts");choicesDirty=false;}finally{updating=false;}}
        ledger.clearSelection();
        boolean extra=selected(category)!=null||selected(account)!=null||!from.getText().isBlank()||!through.getText().isBlank()||!minimum.getText().isBlank()||!maximum.getText().isBlank();
        more.setText(extra?"More filters (active)":"More filters");
        try{
            LocalDate start=date(from.getText(),"From"),end=date(through.getText(),"Through");
            if(start!=null&&end!=null&&start.isAfter(end))throw new IllegalArgumentException("From date must be on or before Through date.");
            BigDecimal min=amount(minimum.getText(),"Minimum"),max=amount(maximum.getText(),"Maximum");
            if(min!=null&&max!=null&&min.compareTo(max)>0)throw new IllegalArgumentException("Minimum amount must be no greater than maximum amount.");
            String query=search.getText().strip().toLowerCase(Locale.ROOT),kind=type.getSelectedIndex()==0?null:(String)type.getSelectedItem(),cat=selected(category),acct=selected(account);
            String[] words=query.isEmpty()?new String[0]:query.split("\\s+");
            boolean active=words.length>0||kind!=null||extra;
            sorter.setRowFilter(!active?null:new RowFilter<>(){
                public boolean include(Entry<? extends TableModel,? extends Integer> entry){
                    int row=entry.getIdentifier();TableModel model=entry.getModel();String rowType=String.valueOf(model.getValueAt(row,2));
                    if(kind!=null&&!(kind.equals("Transfers")?rowType.startsWith("Transfer "):kind.equals(rowType)))return false;
                    if(cat!=null&&!cat.equals(model.getValueAt(row,3)))return false;if(acct!=null&&!acct.equals(model.getValueAt(row,7)))return false;
                    if(start!=null||end!=null){Object raw=model.getValueAt(row,1);if(raw==null)return false;LocalDate day=raw instanceof java.sql.Date sql?sql.toLocalDate():raw instanceof LocalDate local?local:LocalDate.parse(raw.toString());if(start!=null&&day.isBefore(start)||end!=null&&day.isAfter(end))return false;}
                    if(min!=null||max!=null){Object raw=model.getValueAt(row,4);if(raw==null)return false;BigDecimal value=raw instanceof BigDecimal decimal?decimal:new BigDecimal(raw.toString());if(min!=null&&value.compareTo(min)<0||max!=null&&value.compareTo(max)>0)return false;}
                    if(words.length>0){StringBuilder text=new StringBuilder();for(int col=0;col<model.getColumnCount();col++)if(col!=8){Object value=model.getValueAt(row,col);if(value!=null)text.append(value).append(' ');}String haystack=text.toString().toLowerCase(Locale.ROOT);for(String word:words)if(!haystack.contains(word))return false;}
                    return true;
                }
            });
            status.setText("Showing "+ledger.getRowCount()+" of "+ledger.getModel().getRowCount()+" records"+(active?" (filtered)":"")+". Running balances include all entries. Ctrl+F searches.");
        }catch(IllegalArgumentException ex){
            sorter.setRowFilter(new RowFilter<>(){public boolean include(Entry<? extends TableModel,? extends Integer> entry){return false;}});
            status.setText("Filters need attention: "+ex.getMessage()+" No results shown until corrected or cleared.");
        }
        applied.run();
    }
    void onApplied(Runnable action){applied=action;applied.run();}
    LocalDate startDate(){return date(from.getText(),"From");}
    LocalDate endDate(){return date(through.getText(),"Through");}
    void setDates(LocalDate start,LocalDate end){
        updating=true;try{from.setText(start==null?"":start.toString());through.setText(end==null?"":end.toString());more.setSelected(start!=null||end!=null);advanced.setVisible(more.isSelected());}finally{updating=false;}
        applyNow();revalidate();
    }
    void stopPending(){delayed.stop();}
    private static LocalDate date(String text,String label){if(text.isBlank())return null;try{return LocalDate.parse(text.trim());}catch(java.time.format.DateTimeParseException ex){throw new IllegalArgumentException(label+" date must use YYYY-MM-DD.");}}
    private static BigDecimal amount(String text,String label){
        if(text.isBlank())return null;
        try{BigDecimal value=new BigDecimal(text.trim()).setScale(2,RoundingMode.UNNECESSARY);if(value.signum()<0||value.compareTo(new BigDecimal("999999999999.99"))>0)throw new IllegalArgumentException();return value;}
        catch(IllegalArgumentException|ArithmeticException ex){throw new IllegalArgumentException(label+" amount must be between 0 and 999999999999.99 with up to two decimal places.");}
    }
    void clear(){
        updating=true;try{for(JTextField field:new JTextField[]{search,from,through,minimum,maximum})field.setText("");type.setSelectedIndex(0);category.setSelectedIndex(0);account.setSelectedIndex(0);more.setSelected(false);advanced.setVisible(false);}finally{updating=false;}
        applyNow();revalidate();search.requestFocusInWindow();
    }
}

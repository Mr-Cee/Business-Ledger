package com.businessledger;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.sql.*;
import java.util.Map;

/** Explicit Apply protects choices already made in the transaction form. */
final class PayeeSuggestions extends JPanel {
    private final Connection db;
    private final JTextField payee;
    private final JComboBox<String> kind,category;
    private final JComboBox<BusinessLedger.Account> account;
    private final JTextArea status=new JTextArea("Enter a familiar payee to see a suggestion.",2,0);
    private final JButton apply=new JButton("Apply suggestion");
    private PayeeRules.Snapshot snapshot=new PayeeRules.Snapshot(Map.of(),Map.of());
    PayeeSuggestions(Connection db,JTextField payee,JComboBox<String> kind,JComboBox<String> category,JComboBox<BusinessLedger.Account> account){
        super(new BorderLayout(4,4));this.db=db;this.payee=payee;this.kind=kind;this.category=category;this.account=account;
        status.setEditable(false);status.setFocusable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setOpaque(false);status.setFont(UIManager.getFont("Label.font"));
        add(status,BorderLayout.NORTH);WrapPanel actions=new WrapPanel();actions.add(apply);
        apply.setToolTipText("Apply the suggested category and account (Ctrl+Enter). Does not add a transaction.");
        JButton remember=new JButton("Save payee rule"),manage=new JButton("Manage payee rules");actions.add(remember);actions.add(manage);add(actions);
        status.setToolTipText("Exact payee match, ignoring capitalization and extra spaces. Income and Expense are separate. Nothing changes until you apply.");
        apply.addActionListener(e->run(this::apply));remember.addActionListener(e->run(this::remember));manage.addActionListener(e->run(this::manage));
        payee.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){update();}public void removeUpdate(DocumentEvent e){update();}public void changedUpdate(DocumentEvent e){update();}});
        kind.addActionListener(e->update());update();
    }
    void refresh()throws SQLException{snapshot=PayeeRules.load(db);update();}
    void applySuggestedRule(){run(this::apply);}
    private PayeeRules.Suggestion current(){return snapshot.find(payee.getText(),(String)kind.getSelectedItem());}
    private void update(){
        PayeeRules.Suggestion v=current();apply.setEnabled(v!=null&&v.available());
        status.setText(v==null?(payee.getText().isBlank()?"Enter a familiar payee to see a suggestion.":"No matching rule or active transaction history."):
            (!v.available()?"Saved rule unavailable: restore its category/account or replace the rule.":(v.saved()?"Saved rule: ":"From latest dated transaction: ")+v.category()+" / "+v.accountName()+" — click Apply suggestion."));
        status.setToolTipText(status.getText());
    }
    private void apply()throws SQLException{
        // Re-read before applying: another window may have archived or renamed a choice.
        refresh();PayeeRules.Suggestion v=current();if(v==null||!v.available())return;
        BusinessLedger.Account selected=null;boolean foundCategory=false;
        for(int i=0;i<account.getItemCount();i++)if(account.getItemAt(i).id()==v.accountId())selected=account.getItemAt(i);
        for(int i=0;i<category.getItemCount();i++)if(category.getItemAt(i).equals(v.category()))foundCategory=true;
        if(selected==null||!selected.active()||!foundCategory)throw new SQLException("Refresh the ledger before applying this suggestion.");
        category.setSelectedItem(v.category());account.setSelectedItem(selected);
    }
    private void remember()throws SQLException{
        BusinessLedger.Account a=(BusinessLedger.Account)account.getSelectedItem();if(a==null)throw new IllegalArgumentException("Select an account first.");
        String c=(String)category.getSelectedItem();if(c==null)throw new IllegalArgumentException("Select a category first.");
        if(payee.getText().isBlank())throw new IllegalArgumentException("Enter a payee / customer first.");
        if(JOptionPane.showConfirmDialog(this,"Save this rule for "+payee.getText().strip()+" ("+kind.getSelectedItem()+")?\n"+c+" / "+a.name()+"\nThis replaces any saved rule for the same payee and type. It does not save a transaction.","Save payee rule",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        PayeeRules.save(db,payee.getText(),(String)kind.getSelectedItem(),c,a.id());refresh();
    }
    private void manage()throws SQLException{
        refresh();JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Payee rules",Dialog.ModalityType.APPLICATION_MODAL);
        DefaultListModel<PayeeRules.Suggestion> model=new DefaultListModel<>();snapshot.rules().values().forEach(model::addElement);JList<PayeeRules.Suggestion> list=new JList<>(model);list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTextArea help=new JTextArea("Saved rules are suggestions only. To change one, close this window, enter its payee and type in the form, choose a category/account, and save the rule again. Removing a rule restores suggestions from transaction history.");help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);help.setRows(3);
        JButton remove=new JButton("Remove selected rule"),close=new JButton("Close");remove.setEnabled(false);list.addListSelectionListener(e->remove.setEnabled(list.getSelectedValue()!=null));
        remove.addActionListener(e->run(()->{PayeeRules.Suggestion rule=list.getSelectedValue();if(rule!=null&&JOptionPane.showConfirmDialog(dialog,"Remove rule for "+rule.payee()+" ("+rule.kind()+")?","Remove rule",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){PayeeRules.remove(db,rule);refresh();model.clear();snapshot.rules().values().forEach(model::addElement);}}));close.addActionListener(e->dialog.dispose());
        JPanel content=new JPanel(new BorderLayout(8,8));content.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));content.add(help,BorderLayout.NORTH);content.add(new JScrollPane(list));WrapPanel buttons=new WrapPanel();buttons.add(remove);buttons.add(close);content.add(buttons,BorderLayout.SOUTH);dialog.setContentPane(content);dialog.setSize(650,400);dialog.setLocationRelativeTo(this);dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);HelpGuide.bind(dialog.getRootPane(),()->"Payee rules");dialog.setVisible(true);
    }
    @FunctionalInterface private interface Action{void run()throws SQLException;}
    private void run(Action action){try{action.run();}catch(SQLException|IllegalArgumentException ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Payee rules",JOptionPane.ERROR_MESSAGE);}}
}

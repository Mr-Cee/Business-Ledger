package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.sql.*;

final class SettingsPanel extends JPanel {
    private final Connection db;
    private final JRadioButton all=new JRadioButton("All income categories"),specific=new JRadioButton("Only this income category");
    private final JComboBox<String> category=new JComboBox<>();
    private final JComboBox<AppTheme.Style> theme=new JComboBox<>(AppTheme.Style.values());
    private final JTextField taxRate=new JTextField(6);
    private final BackupControls backups;
    private final JTextArea status=new JTextArea(" ",2,0);
    SettingsPanel(Connection db,Runnable saved)throws SQLException{
        super(new BorderLayout(12,12));this.db=db;
        backups=new BackupControls(db,()->{saved.run();try{reload();}catch(SQLException ex){throw new RuntimeException(ex);}});
        JPanel form=new JPanel();form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("Settings");title.setFont(title.getFont().deriveFont(Font.BOLD,22f));form.add(title);form.add(Box.createVerticalStrut(18));
        JLabel appearance=new JLabel("Appearance");appearance.setFont(appearance.getFont().deriveFont(Font.BOLD,17f));form.add(appearance);
        theme.setSelectedItem(AppTheme.current());
        WrapPanel themes=new WrapPanel();themes.addField("Theme",theme);themes.setAlignmentX(LEFT_ALIGNMENT);form.add(themes);
        JLabel themeHelp=new JLabel("Theme changes apply and save immediately on this PC.");themeHelp.setAlignmentX(LEFT_ALIGNMENT);form.add(themeHelp);form.add(Box.createVerticalStrut(20));
        theme.addActionListener(e->{
            AppTheme.Style selected=(AppTheme.Style)theme.getSelectedItem();
            if(selected!=null && selected!=AppTheme.current()){
                try{AppTheme.save(selected);}
                catch(Exception ex){JOptionPane.showMessageDialog(this,"Unable to apply or save theme: "+ex.getMessage(),"Appearance",JOptionPane.ERROR_MESSAGE);}
            }
        });
        JLabel heading=new JLabel("What counts as income?");heading.setFont(heading.getFont().deriveFont(Font.BOLD,17f));form.add(heading);
        JTextArea help=new JTextArea("Choose which income categories count in dashboard totals, income/expense charts, and reports (including CSV exports and All In/Out). Expenses are always counted normally.\n\nYour ledger records, account balances, and balance charts include all transactions. This preference is saved separately for each ledger account.",5,30);
        help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);help.setBackground(getBackground());help.setAlignmentX(LEFT_ALIGNMENT);form.add(help);
        ButtonGroup group=new ButtonGroup();group.add(all);group.add(specific);form.add(all);
        WrapPanel choice=new WrapPanel();choice.add(specific);choice.add(category);choice.setAlignmentX(LEFT_ALIGNMENT);form.add(choice);
        specific.addActionListener(e->{category.setEnabled(true);status.setText("Unsaved changes. Click Save settings to apply.");});
        all.addActionListener(e->{category.setEnabled(false);status.setText("Unsaved changes. Click Save settings to apply.");});
        category.addActionListener(e->status.setText("Unsaved changes. Click Save settings to apply."));
        form.add(Box.createVerticalStrut(16));
        JLabel taxHeading=new JLabel("Estimated taxes");taxHeading.setFont(taxHeading.getFont().deriveFont(Font.BOLD,17f));form.add(taxHeading);
        WrapPanel taxControls=new WrapPanel();taxControls.addField("Percentage of income",taxRate);taxControls.add(new JLabel("%"));taxControls.setAlignmentX(LEFT_ALIGNMENT);form.add(taxControls);
        JTextArea taxHelp=new JTextArea("Current calendar-year income multiplied by this rate (default 24%). Uses your income category choice and is independent of dashboard dates. This simple estimate does not subtract expenses or tax payments.",3,30);
        taxHelp.setEditable(false);taxHelp.setLineWrap(true);taxHelp.setWrapStyleWord(true);taxHelp.setBackground(getBackground());taxHelp.setAlignmentX(LEFT_ALIGNMENT);form.add(taxHelp);
        taxRate.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            private void changed(){status.setText("Unsaved changes. Click Save settings to apply.");}
            public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}
        });
        JButton save=new JButton("Save settings");save.setAlignmentX(LEFT_ALIGNMENT);save.addActionListener(e->{
            try{
                if(specific.isSelected()&&category.getSelectedItem()==null)throw new IllegalArgumentException("Choose an income category.");
                java.math.BigDecimal rate=TaxEstimate.parseRate(taxRate.getText());
                IncomeScope scope=new IncomeScope(all.isSelected()?null:category.getSelectedItem().toString());
                db.setAutoCommit(false);
                try{scope.save(db);TaxEstimate.saveRate(db,rate);backups.save();db.commit();}
                catch(Exception ex){db.rollback();throw ex;}
                finally{db.setAutoCommit(true);}
                saved.run();status.setText("Saved. "+scope.description()+". Estimated tax rate: "+rate.stripTrailingZeros().toPlainString()+"%. Preferences are remembered for this ledger.");
            }catch(Exception ex){status.setText("Unable to save settings: "+ex.getMessage());}
        });form.add(Box.createVerticalStrut(20));backups.setAlignmentX(LEFT_ALIGNMENT);form.add(backups);
        status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setBackground(getBackground());status.setAlignmentX(LEFT_ALIGNMENT);
        JPanel content=new JPanel(new BorderLayout());content.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));content.add(form,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(content);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getVerticalScrollBar().setUnitIncrement(24);add(scroll);
        JPanel footer=new JPanel(new BorderLayout(6,6));footer.setBorder(BorderFactory.createEmptyBorder(6,12,6,12));
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));actions.add(save);footer.add(actions,BorderLayout.NORTH);footer.add(status);add(footer,BorderLayout.SOUTH);reload();
    }
    void reload()throws SQLException{
        backups.reload();
        theme.setSelectedItem(AppTheme.current());
        taxRate.setText(TaxEstimate.rate(db).stripTrailingZeros().toPlainString());
        IncomeScope scope=IncomeScope.load(db);category.removeAllItems();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT name FROM categories ORDER BY name")){while(r.next())category.addItem(r.getString(1));}
        if(scope.category()!=null && ((DefaultComboBoxModel<String>)category.getModel()).getIndexOf(scope.category())<0)category.addItem(scope.category());
        category.setSelectedItem(scope.category()==null?"Paycheck":scope.category());all.setSelected(scope.category()==null);specific.setSelected(scope.category()!=null);category.setEnabled(specific.isSelected());
        status.setText("Current setting: "+scope.description()+".");
    }
    static JDialog createDialog(JFrame owner,Connection db,Runnable saved)throws SQLException{
        JDialog dialog=new JDialog(owner,"Business Ledger - Settings",true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.add(new SettingsPanel(db,saved));
        JButton close=new JButton("Close");close.addActionListener(e->dialog.dispose());
        JButton help=new JButton("Help");help.addActionListener(e->HelpGuide.show(dialog,"Settings & appearance"));
        HelpGuide.bind(dialog.getRootPane(),()->"Settings & appearance");
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(help);actions.add(close);dialog.add(actions,BorderLayout.SOUTH);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setSize(720,680);dialog.setMinimumSize(new Dimension(480,420));dialog.setLocationRelativeTo(owner);return dialog;
    }
}

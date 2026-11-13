package com.businessledger;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;

/** Backup UI runs IO behind an application-modal progress window. */
final class BackupControls extends JPanel {
    private final LedgerBackup backups;
    private final JCheckBox automatic=new JCheckBox("Back up this ledger when closing the app");
    private final JCheckBox timed=new JCheckBox("10-minute backups when data changes (keep 5)");
    private final JTextField folder=new JTextField(24);
    private final Runnable restored;
    BackupControls(Connection db,Runnable restored){
        super(new BorderLayout(0,6));this.backups=new LedgerBackup(db);this.restored=restored;
        JPanel fields=new JPanel();fields.setLayout(new BoxLayout(fields,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("Backup & restore");title.setFont(title.getFont().deriveFont(Font.BOLD,17f));fields.add(title);
        JTextArea help=new JTextArea("Backups contain this ledger's records, edit history, report templates, and ledger preferences. Restore replaces this ledger after creating a safety backup. To move PCs, create a login there and restore into its ledger. Login passwords and the PC theme are separate. Backup files contain unencrypted financial data.",4,30);
        help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);help.setBackground(getBackground());help.setAlignmentX(LEFT_ALIGNMENT);fields.add(help);
        WrapPanel actions=new WrapPanel();actions.setAlignmentX(LEFT_ALIGNMENT);
        JButton create=new JButton("Back up ledger...");create.addActionListener(e->backup());actions.add(create);
        JButton restore=new JButton("Restore ledger...");restore.addActionListener(e->restore());actions.add(restore);fields.add(actions);
        automatic.setText("Back up when closing the app (keep 5)");automatic.setAlignmentX(LEFT_ALIGNMENT);fields.add(automatic);timed.setAlignmentX(LEFT_ALIGNMENT);fields.add(timed);
        WrapPanel destination=new WrapPanel();destination.setAlignmentX(LEFT_ALIGNMENT);destination.addField("Backup folder",folder);
        JButton browse=new JButton("Choose folder...");browse.addActionListener(e->{JFileChooser chooser=new JFileChooser();chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)folder.setText(chooser.getSelectedFile().toString());});destination.add(browse);fields.add(destination);
        JTextArea caption=new JTextArea("Close-time and timed backups keep 5 each in separate managed folders. Manual and safety backups are unlimited. Click Save settings below.",3,0);caption.setEditable(false);caption.setLineWrap(true);caption.setWrapStyleWord(true);caption.setBackground(getBackground());caption.setFont(caption.getFont().deriveFont(11f));caption.setAlignmentX(LEFT_ALIGNMENT);fields.add(caption);add(fields);
    }
    void reload()throws SQLException{LedgerBackup.Options options=backups.options();automatic.setSelected(options.automatic());timed.setSelected(options.timed());folder.setText(options.folder().toString());}
    void save()throws SQLException{
        if(folder.getText().isBlank())throw new IllegalArgumentException("Choose a backup folder.");
        backups.saveOptions(automatic.isSelected(),timed.isSelected(),Path.of(folder.getText().trim()));
    }
    private String ledgerName(){Window owner=SwingUtilities.getWindowAncestor(this);return owner instanceof Dialog dialog&&dialog.getOwner() instanceof JFrame frame?frame.getTitle():"Business Ledger";}
    private JFileChooser files()throws SQLException{
        JFileChooser chooser=new JFileChooser(backups.options().folder().toFile());chooser.setFileFilter(new FileNameExtensionFilter("Business Ledger backup (*.ledgerbackup)","ledgerbackup"));chooser.setAcceptAllFileFilterUsed(false);return chooser;
    }
    private void backup(){
        try{
            JFileChooser chooser=files();chooser.setSelectedFile(LedgerBackup.datedFile(backups.options().folder(),ledgerName(),"backup").toFile());
            if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            Path selected=chooser.getSelectedFile().toPath();if(!selected.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ledgerbackup"))selected=Path.of(selected+".ledgerbackup");
            if(Files.exists(selected)&&JOptionPane.showConfirmDialog(this,"Replace this backup file?\n"+selected,"Replace backup",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            Path target=selected;String name=ledgerName();work(this,"Creating ledger backup...",()->{backups.create(target,name);return target;});
            JOptionPane.showMessageDialog(this,"Backup saved:\n"+target,"Backup complete",JOptionPane.INFORMATION_MESSAGE);
        }catch(Exception ex){error(ex);}
    }
    private void restore(){
        try{
            JFileChooser chooser=files();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            Path source=chooser.getSelectedFile().toPath();LedgerBackup.Info info=LedgerBackup.inspect(source);
            String message="Restore backup for: "+info.ledger()+"\nCreated: "+info.created()+"\n\nThis replaces the signed-in ledger's records and preferences.\nA safety backup of the current ledger will be saved first.\nOther login accounts are unaffected. Continue?";
            if(JOptionPane.showConfirmDialog(this,message,"Restore ledger",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
            String name=ledgerName();LedgerBackup.Restored result=work(this,"Restoring ledger backup...",()->backups.restoreWithSafety(source,name));
            try{restored.run();reload();}catch(Exception ex){throw new Exception("Ledger restored, but the display could not refresh. Close and reopen the app. Safety backup: "+result.safetyBackup(),ex);}
            JOptionPane.showMessageDialog(this,"Ledger restored.\nSafety backup saved:\n"+result.safetyBackup(),"Restore complete",JOptionPane.INFORMATION_MESSAGE);
        }catch(Exception ex){error(ex);}
    }
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Backup & restore",JOptionPane.ERROR_MESSAGE);}
    static <T>T work(Component parent,String message,Callable<T> action)throws Exception{
        Window owner=parent instanceof Window window?window:SwingUtilities.getWindowAncestor(parent);
        JDialog progress=new JDialog(owner,"Business Ledger",Dialog.ModalityType.APPLICATION_MODAL);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JPanel body=new JPanel(new BorderLayout(10,12));body.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));body.add(new JLabel(message),BorderLayout.NORTH);
        JProgressBar bar=new JProgressBar();bar.setIndeterminate(true);body.add(bar);progress.add(body);progress.setSize(420,140);progress.setLocationRelativeTo(parent);
        SwingWorker<T,Void> worker=new SwingWorker<>(){
            protected T doInBackground()throws Exception{return action.call();}
            protected void done(){progress.dispose();}
        };
        worker.execute();progress.setVisible(true);
        try{return worker.get();}catch(ExecutionException ex){Throwable cause=ex.getCause();if(cause instanceof Exception error)throw error;throw new RuntimeException(cause);}
        finally{progress.dispose();}
    }
    static void closeWithBackup(JFrame owner,Connection db,Runnable close){
        try{
            LedgerBackup backups=new LedgerBackup(db);LedgerBackup.Options options=backups.options();
            if(!options.automatic()){close.run();return;}
            while(true){
                try{
                    String name=owner.getTitle();work(owner,"Backing up before closing...",()->AutomaticBackups.take(db,AutomaticBackups.Kind.CLOSING,name));close.run();return;
                }catch(Exception ex){
                    int choice=JOptionPane.showOptionDialog(owner,"Automatic backup failed: "+ex.getMessage(),"Backup before closing",JOptionPane.DEFAULT_OPTION,JOptionPane.ERROR_MESSAGE,null,new String[]{"Try again","Close without backup","Cancel"},"Cancel");
                    if(choice==1){close.run();return;}if(choice!=0)return;
                }
            }
        }catch(Exception ex){JOptionPane.showMessageDialog(owner,"Unable to read backup settings: "+ex.getMessage(),"Unable to close",JOptionPane.ERROR_MESSAGE);}
    }
}

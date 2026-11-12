package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

final class LoginWindow extends JDialog {
    private final LoginStore store;
    private final JComboBox<String> names = new JComboBox<>();
    private final JPasswordField password = new JPasswordField(24);
    private final JButton login = new JButton("Log in"), register = new JButton("Create account");
    private final JLabel status = new JLabel(" ");
    private LoginStore.Session result;
    private boolean busy;

    private LoginWindow(LoginStore store) throws Exception {
        super((Frame) null, "Business Ledger — Log in", true); this.store = store;
        JPanel panel = new JPanel(new BorderLayout(12, 12)); panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        names.setEditable(true);
        for (String name : store.names()) names.addItem(name);
        String remembered = store.rememberedName();
        if (!remembered.isBlank()) names.setSelectedItem(remembered);
        JPanel fields = new JPanel(new GridLayout(0, 2, 10, 12));
        fields.add(new JLabel("Account name")); fields.add(names); fields.add(new JLabel("Password")); fields.add(password);
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.add(new JLabel("Log in to your ledger, or create a separate ledger account."));
        JButton help = new JButton("Help"); help.addActionListener(e -> HelpGuide.show(this, "Login & separate ledgers"));
        heading.add(help, BorderLayout.EAST); panel.add(heading, BorderLayout.NORTH);
        HelpGuide.bind(getRootPane(), () -> "Login & separate ledgers");
        panel.add(fields);
        JPanel bottom = new JPanel(new BorderLayout()); JPanel buttons = new JPanel(); buttons.add(login); buttons.add(register);
        bottom.add(status, BorderLayout.NORTH); bottom.add(buttons, BorderLayout.SOUTH); panel.add(bottom, BorderLayout.SOUTH);
        login.addActionListener(e -> authenticate()); password.addActionListener(e -> authenticate()); register.addActionListener(e -> createAccount());
        getRootPane().setDefaultButton(login); setContentPane(panel); pack(); setResizable(false); setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { SwingUtilities.invokeLater(() -> { if (names.getItemCount() == 0) names.getEditor().getEditorComponent().requestFocusInWindow(); else password.requestFocusInWindow(); }); }
            @Override public void windowClosing(WindowEvent e) { if (!busy) dispose(); }
        });
    }
    static LoginStore.Session showLogin(LoginStore store) throws Exception {
        LoginWindow window = new LoginWindow(store); window.setVisible(true); return window.result;
    }
    private void authenticate() {
        if (busy) return;
        String name = String.valueOf(names.getEditor().getItem()); char[] secret = password.getPassword();
        run(false, name, secret);
    }
    private void createAccount() {
        if (busy) return;
        JTextField name = new JTextField(24); JPasswordField secret = new JPasswordField(24), confirm = new JPasswordField(24);
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 10));
        fields.add(new JLabel("Account name")); fields.add(name); fields.add(new JLabel("Password (12–256 characters)")); fields.add(secret); fields.add(new JLabel("Confirm password")); fields.add(confirm);
        JPanel form = new JPanel(new BorderLayout(8, 12)); form.add(fields);
        form.add(new JLabel(names.getItemCount() == 0 ? "The first account will inherit this PC's existing ledger." : "This account will start with its own empty ledger."), BorderLayout.NORTH);
        while (JOptionPane.showConfirmDialog(this, form, "Create ledger account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            char[] a = secret.getPassword(), b = confirm.getPassword();
            boolean matches = Arrays.equals(a, b); Arrays.fill(b, '\0');
            if (!matches || a.length < 12 || a.length > 256 || name.getText().isBlank()) {
                Arrays.fill(a, '\0'); JOptionPane.showMessageDialog(this, "Enter an account name and matching passwords of 12–256 characters."); continue;
            }
            secret.setText(""); confirm.setText(""); run(true, name.getText(), a); return;
        }
        secret.setText(""); confirm.setText("");
    }
    private void run(boolean creating, String name, char[] secret) {
        busy = true; login.setEnabled(false); register.setEnabled(false); names.setEnabled(false); password.setEnabled(false);
        status.setText(creating ? "Creating account…" : "Logging in…");
        new SwingWorker<LoginStore.Session, Void>() {
            @Override protected LoginStore.Session doInBackground() throws Exception {
                try { return creating ? store.create(name, secret) : store.login(name, secret); }
                finally { Arrays.fill(secret, '\0'); }
            }
            @Override protected void done() {
                password.setText("");
                try { result = get(); dispose(); }
                catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText(cause instanceof IllegalArgumentException ? cause.getMessage() : "Unable to open the ledger. Check the database connection.");
                    // Slow repeated failures without blocking Swing or changing the saved selection.
                    Timer retry = new Timer(1500, e -> {
                        busy = false; login.setEnabled(true); register.setEnabled(true); names.setEnabled(true); password.setEnabled(true); password.requestFocusInWindow();
                    }); retry.setRepeats(false); retry.start();
                }
            }
        }.execute();
    }
}

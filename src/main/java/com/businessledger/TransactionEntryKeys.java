package com.businessledger;

import javax.swing.*;
import java.awt.event.ActionEvent;

/** Keyboard entry without allowing the rule shortcut to submit the form. */
final class TransactionEntryKeys {
    static void install(JPanel page,BusinessLedger.GhostField payee,JTextField amount,
                        JTextField date,JTextField notes,Runnable applyRule,Runnable save) {
        payee.getActionMap().put("accept-history",new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){
                if(!payee.acceptCompletion())amount.requestFocusInWindow();
            }
        });
        Action apply=new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){applyRule.run();}
        };
        String applyKey="apply-payee-rule";
        KeyStroke shortcut=KeyStroke.getKeyStroke("control ENTER");
        page.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(shortcut,applyKey);
        page.getActionMap().put(applyKey,apply);
        for(JTextField input:new JTextField[]{payee,amount,date,notes}){
            // A focused text field must consume Ctrl+Enter before its notify action.
            input.getInputMap(JComponent.WHEN_FOCUSED).put(shortcut,applyKey);
            input.getActionMap().put(applyKey,apply);
            input.addActionListener(e->{
                int modified=ActionEvent.CTRL_MASK|ActionEvent.ALT_MASK|ActionEvent.META_MASK|ActionEvent.SHIFT_MASK;
                if((e.getModifiers()&modified)==0&&!payee.getText().isBlank()&&!amount.getText().isBlank())save.run();
            });
        }
        payee.setToolTipText("Tab accepts autocomplete; the next Tab moves to Amount. Ctrl+Enter applies the suggested rule. Enter adds only with a payee and amount.");
        amount.setToolTipText("Enter adds the transaction when payee and amount are filled in. Ctrl+Enter only applies the suggested rule.");
    }
}

package com.businessledger;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/** Exercises the installed key maps and text-field submit actions without a database. */
public final class TransactionEntryKeysCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void press(JComponent field,String stroke){
        Object key=field.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(stroke));
        Action action=field.getActionMap().get(key);check(action!=null,"Missing binding: "+stroke);
        action.actionPerformed(new ActionEvent(field,ActionEvent.ACTION_PERFORMED,stroke));
    }
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{
            int[] focused={0},applied={0},saved={0};
            BusinessLedger.GhostField payee=new BusinessLedger.GhostField(12,()->List.of("Panda Express"));
            JTextField amount=new JTextField(8){@Override public boolean requestFocusInWindow(){focused[0]++;return true;}};
            JTextField date=new JTextField(),notes=new JTextField();JPanel page=new JPanel();
            TransactionEntryKeys.install(page,payee,amount,date,notes,()->applied[0]++,()->saved[0]++);
            payee.setText("Pan");payee.setCaretPosition(3);press(payee,"TAB");
            check(payee.getText().equals("Panda Express")&&focused[0]==0,"First Tab accepts completion and keeps payee focus");
            press(payee,"TAB");check(focused[0]==1,"Next Tab moves to Amount");
            payee.setText("New payee");payee.setCaretPosition(payee.getText().length());press(payee,"TAB");
            check(focused[0]==2,"No completion moves directly to Amount");
            amount.setText("30.40");
            for(JTextField input:new JTextField[]{payee,amount,date,notes})press(input,"control ENTER");
            check(applied[0]==4&&saved[0]==0,"Ctrl+Enter only applies a rule, including with a complete transaction");
            for(var listener:amount.getActionListeners())listener.actionPerformed(new ActionEvent(amount,ActionEvent.ACTION_PERFORMED,"notify",ActionEvent.CTRL_MASK));
            check(saved[0]==0,"Modified notify events cannot accidentally submit");
            check(page.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("control ENTER")).equals("apply-payee-rule"),"Rule shortcut is available throughout the tab");
            amount.postActionEvent();check(saved[0]==1,"Plain Enter with both fields saves once");
            amount.setText("");for(JTextField input:new JTextField[]{payee,amount,date,notes})input.postActionEvent();
            check(saved[0]==1,"Missing amount prevents Enter submit from any text field");
            amount.setText("30.40");payee.setText("  ");amount.postActionEvent();check(saved[0]==1,"Blank payee prevents Enter submit");
            payee.setText("Panda Express");payee.postActionEvent();check(saved[0]==2,"Plain Enter is distinct from the modified shortcut");
            System.out.println("Payee completion, two-step Tab navigation, Ctrl+Enter isolation and required-field Enter guards passed.");
        });
    }
}

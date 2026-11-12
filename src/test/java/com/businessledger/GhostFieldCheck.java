package com.businessledger;

import java.awt.event.ActionEvent;
import java.util.*;
import javax.swing.*;

public final class GhostFieldCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> history = new ArrayList<>(List.of("Office supplies", "Office rent", "Coffee shop"));
            BusinessLedger.GhostField field = new BusinessLedger.GhostField(24, () -> history);
            field.setText("off"); field.setCaretPosition(3);
            check("Office supplies".equals(field.suggestion()), "Case-insensitive prefix match in history order");
            check(field.getText().equals("off"), "Ghost does not alter submitted text");
            field.getActionMap().get("accept-history").actionPerformed(new ActionEvent(field, 0, "TAB"));
            check(field.getText().equals("Office supplies"), "Tab inserts full historical value");
            field.setText("Office"); field.setCaretPosition(2);
            check(field.suggestion() == null, "No suggestion while editing within a value");
            field.select(0, 3); check(!field.acceptCompletion(), "Selected text is not overwritten");
            field.setText("Coffee"); field.setCaretPosition(6);
            field.getActionMap().get("dismiss-history").actionPerformed(new ActionEvent(field, 0, "ESCAPE"));
            check(field.suggestion() == null && field.getText().equals("Coffee"), "Escape dismisses without changing text");
            field.setText("Coff"); field.setCaretPosition(4); check(field.suggestion().equals("Coffee shop"), "Typing resumes suggestions");
            history.add(0, "Coffee supplier"); check(field.suggestion().equals("Coffee supplier"), "Saved history refresh is used immediately");
            field.setText("Unknown"); field.setCaretPosition(7);
            check(!field.acceptCompletion() && field.getText().equals("Unknown"), "No-match Tab preserves text");
            field.setText(""); check(field.suggestion() == null, "Blank input does not auto-fill");
            System.out.println("Inline completion and keyboard checks passed.");
        });
    }
}

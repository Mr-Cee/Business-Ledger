package com.businessledger;

import java.awt.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Exercises date selection without opening windows or accessing the ledger database. */
public final class CalendarCheck {
    private static JButton find(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton b && text.equals(b.getText())) return b;
            if (child instanceof Container c) { JButton b = find(c, text); if (b != null) return b; }
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<LocalDate> chosen = new AtomicReference<>();
            BusinessLedger.CalendarPanel calendar = new BusinessLedger.CalendarPanel(LocalDate.of(2024, 2, 29), chosen::set);
            check(calendar.displayedMonth().equals(YearMonth.of(2024, 2)), "Opens on selected month");
            find(calendar, "29").doClick(); check(chosen.get().equals(LocalDate.of(2024, 2, 29)), "Leap day selection");
            check(find(calendar, "30") == null, "February has no invalid day buttons");
            calendar.changeMonth(-2); check(calendar.displayedMonth().equals(YearMonth.of(2023, 12)), "Previous year navigation");
            calendar.changeMonth(1); check(calendar.displayedMonth().equals(YearMonth.of(2024, 1)), "Next year navigation");
            find(calendar, "31").doClick(); check(chosen.get().equals(LocalDate.of(2024, 1, 31)), "Day selection follows displayed month");
            JTextField text = new JTextField("2026-10-15");
            BusinessLedger.DateChooser chooser = new BusinessLedger.DateChooser(text);
            check(chooser.selectedDate().equals(LocalDate.of(2026, 10, 15)), "Uses previously entered date");
            text.setText("2025-12-03"); check(chooser.selectedDate().equals(LocalDate.of(2025, 12, 3)), "Manual date changes update calendar anchor");
            text.setText("invalid"); check(chooser.selectedDate().equals(LocalDate.now()), "Invalid entry opens at today");
            check(text.getText().equals("invalid"), "Opening does not overwrite typed input");
            System.out.println("Calendar checks passed.");
        });
    }
}

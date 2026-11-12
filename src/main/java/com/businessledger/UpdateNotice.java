package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

/** A visible reminder remains after the update dialog is dismissed. */
final class UpdateNotice extends JPanel {
    private final JLabel label = new JLabel();
    private final PropertyChangeListener changed = event -> refresh();

    UpdateNotice() {
        super(new BorderLayout());
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setIcon(new Icon() {
            public int getIconWidth() { return 24; }
            public int getIconHeight() { return 24; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(195, 35, 45)); g.fillOval(x + 1, y + 1, 22, 22);
                g.setColor(Color.WHITE); g.fillRoundRect(x + 10, y + 5, 4, 9, 2, 2); g.fillOval(x + 10, y + 17, 4, 4);
                g.dispose();
            }
        });
        JButton view = new JButton("View update");
        view.addActionListener(event -> UpdateChecker.showAvailable(SwingUtilities.getWindowAncestor(this)));
        view.setToolTipText("Download the newer version or read its release notes.");
        WrapPanel contents = new WrapPanel(); contents.add(label); contents.add(view); add(contents);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        getAccessibleContext().setAccessibleName("Software update reminder");
        refresh();
    }

    private void refresh() {
        UpdateChecker.Release release = UpdateChecker.available();
        label.setText(release == null ? "" : "Update available: " + release.version());
        setVisible(release != null); revalidate(); repaint();
    }

    @Override public void addNotify() {
        super.addNotify(); UpdateChecker.addAvailabilityListener(changed); refresh();
    }
    @Override public void removeNotify() {
        UpdateChecker.removeAvailabilityListener(changed); super.removeNotify();
    }
}

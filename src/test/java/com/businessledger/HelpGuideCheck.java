package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** No database, login, or user preference writes. */
public final class HelpGuideCheck {
    public static void main(String[] args) throws Exception {
        if (HelpGuide.find("BACKUP").isEmpty() || HelpGuide.find("paycheck").isEmpty()
                || HelpGuide.find("Accounts & transfers").isEmpty()) throw new AssertionError("Search misses instructions");
        if (!HelpGuide.find("zz-no-such-topic-zz").isEmpty()) throw new AssertionError("Unexpected search result");
        for (String title : new String[]{"Dashboard", "Income & expenses", "Accounts & transfers", "Business mileage", "Reports", "Charts"})
            if (HelpGuide.TOPICS.stream().noneMatch(t -> t.title().equals(title))) throw new AssertionError("Missing screen: " + title);
        SwingUtilities.invokeAndWait(() -> {
            try {
                HelpGuide guide = new HelpGuide("Getting started");
                JTextField search = find(guide, JTextField.class);
                JList<?> topics = find(guide, JList.class);
                JEditorPane article = find(guide, JEditorPane.class);
                for (int i = 0; i < topics.getModel().getSize(); i++) {
                    topics.setSelectedIndex(i);
                    if (article.getDocument().getLength() < 100) throw new AssertionError("Empty article");
                }
                search.setText("zz-no-such-topic-zz");
                if (topics.getModel().getSize() != 0 || !article.getText().contains("No matching topics")) throw new AssertionError("No-results display");
                search.setText("backup");
                if (topics.getSelectedIndex() < 0) throw new AssertionError("Search selection");
                search.setText(""); topics.setSelectedIndex(0);
                Files.createDirectories(Path.of("target/help-check"));
                for (AppTheme.Style theme : AppTheme.Style.values()) {
                    AppTheme.apply(theme);
                    HelpGuide themed = new HelpGuide("Getting started");
                    themed.setSize(600, 500); layout(themed);
                    BufferedImage image = new BufferedImage(600, 500, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics(); themed.printAll(graphics); graphics.dispose();
                    ImageIO.write(image, "png", Path.of("target/help-check/help-" + theme.name().toLowerCase() + ".png").toFile());
                }
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        System.out.println("Help guide: screen coverage, full-text search, no results, topic rendering, and narrow layout passed.");
    }
    private static void layout(Container parent) {
        parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
    }
    private static <T> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container c) { T result = find(c, type); if (result != null) return result; }
        }
        return null;
    }
}

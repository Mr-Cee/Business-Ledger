package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class UpdateNoticeCheck {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UpdateChecker.remember(null);
                UpdateNotice notice = new UpdateNotice(); notice.addNotify();
                if (notice.isVisible()) throw new AssertionError("Reminder shown before detection");
                URI page = URI.create("https://github.com/Mr-Cee/Business-Ledger/releases/latest");
                UpdateChecker.Release newer = new UpdateChecker.Release("65535.0.0", page, page);
                UpdateChecker.remember(newer);
                if (!notice.isVisible()) throw new AssertionError("Reminder did not appear after detection");
                // Redraw and tab lifecycle must not dismiss a pending update.
                notice.removeNotify(); notice.addNotify();
                if (!notice.isVisible() || UpdateChecker.available() != newer) throw new AssertionError("Reminder lost on navigation");
                notice.removeNotify();
                Files.createDirectories(Path.of("target/update-notice"));
                for (AppTheme.Style style : AppTheme.Style.values()) {
                    AppTheme.apply(style); UpdateNotice themed = new UpdateNotice();
                    themed.setSize(420, 100); layout(themed);
                    BufferedImage image = new BufferedImage(420, 100, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics(); themed.printAll(graphics); graphics.dispose();
                    ImageIO.write(image, "png", Path.of("target/update-notice/notice-" + style.name().toLowerCase() + ".png").toFile());
                }
                notice.addNotify();
                UpdateChecker.remember(new UpdateChecker.Release(AppVersion.version(), page, page));
                if (notice.isVisible()) throw new AssertionError("Same version still shows reminder");
                UpdateChecker.remember(newer); UpdateChecker.remember(null);
                if (notice.isVisible()) throw new AssertionError("Successful empty check did not hide reminder");
                notice.removeNotify();
                System.out.println("Update reminder detection, same-version suppression, navigation, clearing, and all four theme renders passed.");
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
    private static void layout(Container parent) {
        parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
    }
}

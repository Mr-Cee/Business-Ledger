package com.businessledger;

import java.awt.Image;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Shared icon sizes let Windows choose a sharp image for each display scale. */
final class AppIcon {
    private static final List<Image> IMAGES = load();
    private static List<Image> load() {
        List<Image> images = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            try (var input = AppIcon.class.getResourceAsStream("/icons/ledger-" + size + ".png")) {
                if (input != null) { Image image = ImageIO.read(input); if (image != null) images.add(image); }
            } catch (java.io.IOException ignored) { /* Optional branding must not block access to the ledger. */ }
        }
        return List.copyOf(images);
    }
    static List<Image> images() { return IMAGES; }
    static void apply(Window window) { window.setIconImages(IMAGES); }
}

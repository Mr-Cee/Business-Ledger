package com.businessledger;

import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.*;

/** Native identity belongs to the Java host process and its real Swing HWNDs. */
final class WindowsTaskbar {
    static final String APP_ID = "BusinessLedger.Desktop";
    private static boolean attempted, ready;
    private static Path root;
    static synchronized boolean initialize() {
        if (attempted) return ready;
        attempted = true;
        if (!System.getProperty("os.name", "").startsWith("Windows")) return false;
        try {
            root = Path.of(System.getProperty("ledger.windows.directory", AppPaths.applicationDirectory().toString())).toAbsolutePath().normalize();
            Path library = Path.of(System.getProperty("ledger.windows.library", root.resolve("LedgerWindows.dll").toString())).toAbsolutePath().normalize();
            if (Files.isRegularFile(library)) { System.load(library.toString()); ready = initializeNative(); }
        } catch (Exception | LinkageError ignored) { /* Branding must not prevent access to records. */ }
        return ready;
    }
    static String jawtPath() { return Path.of(System.getProperty("java.home"), "bin", "jawt.dll").toString(); }
    static Path launcher() {
        Path parent = root.getParent();
        if (parent != null && parent.getFileName().toString().equalsIgnoreCase("versions")) {
            Path installed = parent.getParent().resolve("BusinessLedgerLauncher.exe");
            if (Files.isRegularFile(installed)) return installed;
        }
        return root.resolve("BusinessLedger.exe");
    }
    static boolean configure(Window window) {
        if (!initialize() || !window.isDisplayable()) return false;
        try { return configureNative(window, System.getProperty("ledger.windows.icon", root.resolve("BusinessLedger.ico").toString()), launcher().toString(), jawtPath()); }
        catch (LinkageError ignored) { return false; }
    }
    static void clear(Window window) {
        if (ready && window.isDisplayable()) try { clearNative(window, jawtPath()); } catch (LinkageError ignored) { }
    }
    static void attach(Window window) {
        if (!initialize()) return;
        window.addHierarchyListener(event -> { if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) configure(window); });
        window.addWindowListener(new WindowAdapter() { @Override public void windowOpened(WindowEvent event) { configure(window); } });
        configure(window);
    }
    private static native boolean initializeNative();
    private static native boolean configureNative(Window window, String icon, String launcher, String jawt);
    private static native void clearNative(Window window, String jawt);
    static native String processIdentityNative();
    static native String[] windowIdentityNative(Window window, String jawt);
}

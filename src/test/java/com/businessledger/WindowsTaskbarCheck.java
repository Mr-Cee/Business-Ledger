package com.businessledger;
import javax.swing.*;
import java.nio.file.*;
import java.util.Arrays;

/** Invisible Swing peer only: no login, ledger connection, or taskbar pin is created. */
public final class WindowsTaskbarCheck {
    public static void main(String[] args) throws Exception {
        Class.forName("com.businessledger.BusinessLedger");
        if (!WindowsTaskbar.initialize()) throw new AssertionError("Native integration did not initialize");
        if (!WindowsTaskbar.APP_ID.equals(WindowsTaskbar.processIdentityNative())) throw new AssertionError("Java host process identity");
        String expectedIcon=System.getProperty("ledger.windows.icon", Path.of(System.getProperty("ledger.windows.directory", AppPaths.applicationDirectory().toString())).resolve("BusinessLedger.ico").toString());
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Ledger taskbar verification");
            try {
                AppIcon.apply(frame); frame.setSize(320,160); frame.addNotify();
                if (!WindowsTaskbar.configure(frame)) throw new AssertionError("Swing HWND configuration failed");
                String[] state = WindowsTaskbar.windowIdentityNative(frame, WindowsTaskbar.jawtPath());
                if (state == null || !state[0].equals(WindowsTaskbar.APP_ID) || !state[1].equals(expectedIcon+",0")
                        || !state[2].equals("\"" + WindowsTaskbar.launcher() + "\"")) throw new AssertionError("Unexpected taskbar properties: " + Arrays.toString(state));
                WindowsTaskbar.clear(frame);
                String[] cleared = WindowsTaskbar.windowIdentityNative(frame, WindowsTaskbar.jawtPath());
                if (!cleared[0].isEmpty() || !cleared[1].isEmpty() || !cleared[2].isEmpty()) throw new AssertionError("Window properties not released");
            } finally { frame.dispose(); }
        });
        System.out.println("Java host AppUserModelID, real Swing HWND identity/icon/relaunch properties, and cleanup passed. No visible window or ledger database opened.");
        if (System.getProperty("ledger.launch.check") != null) ReleaseCheck.main(args);
    }
}

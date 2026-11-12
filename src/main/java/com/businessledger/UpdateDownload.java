package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.*;

/** Downloads are staged separately; only verified installers can reach the close/install flow. */
final class UpdateDownload {
    static final long MAX_INSTALLER = 1_073_741_824L;
    record Ready(Path installer, String sha256, String version) {}
    private static boolean busy;
    private static Ready pending;

    static void show(Window owner, UpdateChecker.Release release) {
        if (busy) return;
        if (pending != null && pending.version().equals(release.version()) && Files.isRegularFile(pending.installer())) {
            offerInstall(owner, pending); return;
        }
        busy = true;
        JDialog dialog = new JDialog(owner, "Downloading Business Ledger " + release.version(), Dialog.ModalityType.APPLICATION_MODAL);
        JProgressBar bar = new JProgressBar(0, 100); bar.setStringPainted(true);
        JLabel status = new JLabel("Preparing download..."); JButton cancel = new JButton("Cancel");
        JPanel panel = new JPanel(new BorderLayout(10, 12)); panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        panel.add(status, BorderLayout.NORTH); panel.add(bar); JPanel actions = new JPanel(); actions.add(cancel); panel.add(actions, BorderLayout.SOUTH);
        dialog.add(panel); dialog.setSize(480, 170); dialog.setLocationRelativeTo(owner); dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        SwingWorker<Ready, Long> worker = new SwingWorker<>() {
            @Override protected Ready doInBackground() throws Exception {
                return download(release, this::isCancelled, count -> publish(count));
            }
            @Override protected void process(List<Long> counts) {
                long count = counts.get(counts.size() - 1);
                bar.setValue((int) Math.min(100, count * 100 / Math.max(1, release.size())));
                status.setText(String.format(Locale.US, "Downloaded %.1f of %.1f MB", count / 1048576.0, release.size() / 1048576.0));
                if (count == release.size()) status.setText("Verifying downloaded installer...");
            }
            @Override protected void done() {
                busy = false; dialog.dispose();
                if (isCancelled()) return;
                try {
                    pending = get(); offerInstall(owner, pending);
                } catch (CancellationException ignored) { }
                catch (Exception ex) { JOptionPane.showMessageDialog(owner, "The update was not installed.\n" + message(ex)
                    + "\nYou can continue using this version and try again later.", "Unable to update", JOptionPane.ERROR_MESSAGE); }
            }
        };
        Runnable stop = () -> { cancel.setEnabled(false); status.setText("Canceling download..."); worker.cancel(true); };
        cancel.addActionListener(e -> stop.run()); dialog.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { stop.run(); } });
        worker.execute(); dialog.setVisible(true);
    }

    private static void offerInstall(Window owner, Ready ready) {
        int choice = JOptionPane.showOptionDialog(owner, "Business Ledger " + ready.version() + " is downloaded and verified.\n\n"
            + "Install and restart will close Ledger, install the update, and reopen it.\nYour data folder is preserved. Sign in again after restarting.\n"
            + "Your automatic backup-on-close preference will still apply.", "Update ready", JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE, null, new String[]{"Install and restart", "Later"}, "Install and restart");
        if (choice == 0 && owner instanceof BusinessLedger ledger) ledger.installUpdate(ready);
    }

    private static String message(Exception ex) { Throwable cause = ex.getCause() == null ? ex : ex.getCause(); return cause.getMessage() == null ? cause.toString() : cause.getMessage(); }

    static Ready download(UpdateChecker.Release release, BooleanSupplier canceled, LongConsumer progress) throws Exception {
        if (release.checksum() == null || release.size() <= 0 || release.size() > MAX_INSTALLER) throw new IOException("The release has invalid download information.");
        if (canceled.getAsBoolean()) throw new CancellationException();
        String expected;
        try (InputStream in = open(release.checksum())) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[512];
            long deadline = System.nanoTime() + 30_000_000_000L;
            for (int read; (read = in.read(buffer)) != -1;) {
                if (canceled.getAsBoolean()) throw new CancellationException();
                if (System.nanoTime() > deadline || bytes.size() + read > 4096) throw new IOException("Invalid or timed-out checksum download.");
                bytes.write(buffer, 0, read);
            }
            expected = checksum(bytes.toString(StandardCharsets.UTF_8), "BusinessLedger-Setup-" + release.version() + ".exe");
        }
        if (canceled.getAsBoolean()) throw new CancellationException();
        Path directory = Files.createTempDirectory("BusinessLedger-update-");
        Path installer = directory.resolve("BusinessLedger-Setup-" + release.version() + ".exe");
        boolean verified = false;
        try {
            try (InputStream input = open(release.installer()); OutputStream output = Files.newOutputStream(installer, StandardOpenOption.CREATE_NEW)) {
                copyVerified(input, output, release.size(), expected, canceled, progress);
            }
            if (canceled.getAsBoolean()) throw new CancellationException();
            verified = true; return new Ready(installer, expected, release.version());
        } finally { if (!verified) { Files.deleteIfExists(installer); Files.deleteIfExists(directory); } }
    }

    static String checksum(String text, String file) throws IOException {
        String[] fields = text.strip().split("\\s+");
        if (fields.length != 2 || !fields[0].matches("(?i)[0-9a-f]{64}") || !fields[1].replaceFirst("^\\*", "").equals(file))
            throw new IOException("The release checksum file is invalid.");
        return fields[0].toLowerCase(Locale.ROOT);
    }

    static void copyVerified(InputStream input, OutputStream output, long expectedSize, String expectedHash,
            BooleanSupplier canceled, LongConsumer progress) throws Exception {
        if (expectedSize <= 0 || expectedSize > MAX_INSTALLER) throw new IOException("Invalid installer size.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[65536]; long count = 0;
        long deadline = System.nanoTime() + 1_800_000_000_000L;
        for (int read; (read = input.read(buffer)) != -1;) {
            if (canceled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (System.nanoTime() > deadline) throw new IOException("The download timed out.");
            count += read; if (count > expectedSize) throw new IOException("Installer size does not match the release.");
            output.write(buffer, 0, read); digest.update(buffer, 0, read); progress.accept(count);
        }
        if (count != expectedSize || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHash))
            throw new IOException("Installer verification failed. Please try downloading again.");
    }

    static boolean allowed(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
            && Set.of("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com").contains(uri.getHost());
    }

    private static InputStream open(URI initial) throws IOException {
        URI uri = initial;
        for (int redirect = 0; redirect < 6; redirect++) {
            if (!allowed(uri)) throw new IOException("Untrusted update download address.");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(10_000); connection.setReadTimeout(15_000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "BusinessLedger/" + AppVersion.version());
            try {
                int status = connection.getResponseCode();
                if (status == 200) return new FilterInputStream(connection.getInputStream()) {
                    @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
                };
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing download redirect.");
                    uri = uri.resolve(location); connection.disconnect(); continue;
                }
                throw new IOException("GitHub download failed (HTTP " + status + ").");
            } catch (IOException | RuntimeException ex) { connection.disconnect(); throw ex; }
        }
        throw new IOException("Too many download redirects.");
    }

    static List<String> command(Ready ready, long pid, Path home) {
        if (pid <= 0 || pid > Integer.MAX_VALUE || !home.isAbsolute()) throw new IllegalArgumentException("Invalid update handoff.");
        return List.of(ready.installer().toAbsolutePath().toString(), "/update", Long.toString(pid), home.normalize().toString());
    }

    static void verifyReady(Ready ready) throws Exception {
        try (InputStream input = Files.newInputStream(ready.installer())) {
            MessageDigest hash = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[65536];
            for (int count; (count = input.read(buffer)) != -1;) hash.update(buffer, 0, count);
            if (!HexFormat.of().formatHex(hash.digest()).equals(ready.sha256())) throw new IOException("The installer changed after download. Download it again.");
        }
    }

    static void launch(Ready ready, Path home) throws Exception {
        verifyReady(ready);
        new ProcessBuilder(command(ready, ProcessHandle.current().pid(), home)).start();
    }
}

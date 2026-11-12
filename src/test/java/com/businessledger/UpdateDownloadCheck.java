package com.businessledger;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;

public final class UpdateDownloadCheck {
    interface Action { void run() throws Exception; }
    static void rejects(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid download accepted"); }
        catch (IOException | IllegalArgumentException | CancellationException expected) { }
    }
    public static void main(String[] args) throws Exception {
        byte[] bytes = "MZ-test-installer-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String file = "BusinessLedger-Setup-1.4.1.exe";
        if (!UpdateDownload.checksum(hash + "  " + file + "\n", file).equals(hash)) throw new AssertionError("Checksum parsing");
        rejects(() -> UpdateDownload.checksum(hash + " wrong.exe", file));
        rejects(() -> UpdateDownload.checksum("not-a-hash " + file, file));
        rejects(() -> UpdateDownload.checksum(hash + " " + file + "\nextra", file));
        ByteArrayOutputStream output = new ByteArrayOutputStream(); long[] progress = {0};
        UpdateDownload.copyVerified(new ByteArrayInputStream(bytes), output, bytes.length, hash, () -> false, count -> progress[0] = count);
        if (!Arrays.equals(bytes, output.toByteArray()) || progress[0] != bytes.length) throw new AssertionError("Downloaded data/progress");
        rejects(() -> UpdateDownload.copyVerified(new ByteArrayInputStream(bytes), new ByteArrayOutputStream(), bytes.length, "0".repeat(64), () -> false, count -> {}));
        rejects(() -> UpdateDownload.copyVerified(new ByteArrayInputStream(bytes), new ByteArrayOutputStream(), bytes.length + 1, hash, () -> false, count -> {}));
        rejects(() -> UpdateDownload.copyVerified(new ByteArrayInputStream(bytes), new ByteArrayOutputStream(), bytes.length - 1, hash, () -> false, count -> {}));
        rejects(() -> UpdateDownload.copyVerified(new ByteArrayInputStream(bytes), new ByteArrayOutputStream(), bytes.length, hash, () -> true, count -> {}));
        for (String url : new String[]{"http://github.com/file", "https://github.com.evil.example/file", "https://user@github.com/file", "https://github.com:444/file"})
            if (UpdateDownload.allowed(URI.create(url))) throw new AssertionError("Untrusted redirect accepted");
        if (!UpdateDownload.allowed(URI.create("https://release-assets.githubusercontent.com/asset?token=test"))) throw new AssertionError("GitHub asset host blocked");
        Files.createDirectories(Path.of("target/in-app-update"));
        Path directory = Files.createTempDirectory(Path.of("target/in-app-update"), "download-check-");
        Path installer = directory.resolve("installer with spaces.exe");
        try {
            Files.write(installer, bytes); UpdateDownload.Ready ready = new UpdateDownload.Ready(installer, hash, "1.4.1");
            UpdateDownload.verifyReady(ready); Files.writeString(installer, "changed"); rejects(() -> UpdateDownload.verifyReady(ready));
            Path home = Path.of("C:\\Ledger Data\\A & B");
            List<String> command = UpdateDownload.command(ready, 12345, home);
            if (command.size() != 4 || !command.get(3).equals(home.toString()) || !command.get(1).equals("/update")) throw new AssertionError("Argument boundaries");
            rejects(() -> UpdateDownload.command(ready, 0, home));
            rejects(() -> UpdateDownload.command(ready, 123, Path.of("relative-home")));
        } finally { Files.deleteIfExists(installer); Files.deleteIfExists(directory); }
        System.out.println("Download checks passed: checksum format, integrity, size, cancellation, progress, redirect trust, tampering, and handoff arguments. No installer executed.");
    }
}

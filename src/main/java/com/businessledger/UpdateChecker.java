package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public release checks run off the event thread and send no ledger data or credentials. */
final class UpdateChecker {
    static final String REPOSITORY = "Mr-Cee/Business-Ledger";
    static final URI LATEST = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases/latest");
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    record Release(String version, URI installer, URI page) {}

    static void onStartup(Window owner) {
        if (STARTED.compareAndSet(false, true)) check(owner, false);
    }

    static void check(Window owner, boolean manual) {
        if (!BUSY.compareAndSet(false, true)) {
            if (manual) JOptionPane.showMessageDialog(owner, "An update check is already running. Please wait a moment.", "Updates", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new SwingWorker<Release, Void>() {
            @Override protected Release doInBackground() throws Exception {
                return fetch(LATEST);
            }
            @Override protected void done() {
                BUSY.set(false);
                if (!owner.isShowing()) return;
                try {
                    Release release = get();
                    if (release == null || compare(release.version(), AppVersion.version()) <= 0) {
                        if (manual) JOptionPane.showMessageDialog(owner, "You are using Business Ledger " + AppVersion.version()
                            + ".\nNo newer complete release is available.", "Check for updates", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    Object[] choices = {"Download installer", "View release notes", "Later"};
                    int choice = JOptionPane.showOptionDialog(owner, "Business Ledger " + release.version() + " is available.\n"
                        + "You are using " + AppVersion.version() + ".\n\nDownload the installer, close Business Ledger, then run the installer.\n"
                        + "Keep your existing ledger data folder selected to retain your records.",
                        "Update available", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, choices, choices[0]);
                    if (choice == 0) browse(owner, release.installer());
                    else if (choice == 1) browse(owner, release.page());
                } catch (Exception ex) {
                    if (manual) JOptionPane.showMessageDialog(owner,
                        "Unable to check for updates right now.\nCheck your internet connection and try again later.\n"
                        + "GitHub may also be temporarily unavailable or limiting requests.\n\nYou can continue using your current version.",
                        "Check for updates", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }.execute();
    }

    private static void browse(Window owner, URI uri) {
        try { Desktop.getDesktop().browse(uri); }
        catch (Exception ex) {
            JTextArea link = new JTextArea("Open this address in your web browser:\n\n" + uri);
            link.setEditable(false); link.setLineWrap(true); link.setWrapStyleWord(true); link.setColumns(55); link.setRows(5);
            JOptionPane.showMessageDialog(owner, link, "Download update", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    static Release fetch(URI endpoint) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) endpoint.toURL().openConnection();
        connection.setConnectTimeout(8_000); connection.setReadTimeout(8_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "BusinessLedger/" + AppVersion.version());
        try {
            int status = connection.getResponseCode();
            if (status != 200) return response(status, new byte[0]);
            try (InputStream body = connection.getInputStream()) { return response(status, readBody(body)); }
        } finally { connection.disconnect(); }
    }

    static Release response(int status, byte[] body) throws IOException {
        if (status == 404) return null;
        if (status != 200) throw new IOException("Release service unavailable");
        return parse(new String(body, StandardCharsets.UTF_8));
    }

    static byte[] readBody(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; long deadline = System.nanoTime() + 20_000_000_000L;
        for (int read; (read = input.read(buffer)) != -1;) {
            if (System.nanoTime() > deadline) throw new IOException("Release response timed out");
            if (read > 1_048_576 - bytes.size()) throw new IOException("Release response too large");
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    static Release parse(String json) throws IOException {
        Object parsed = new Json(json).read();
        if (!(parsed instanceof Map<?, ?> release)) throw new IOException("Invalid release");
        if (!Boolean.FALSE.equals(release.get("draft")) || !Boolean.FALSE.equals(release.get("prerelease"))) return null;
        if (!(release.get("tag_name") instanceof String tag)) throw new IOException("Missing version");
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        parts(version);
        String base = "https://github.com/" + REPOSITORY + "/releases/";
        String expectedPage = base + "tag/" + tag;
        if (!expectedPage.equals(release.get("html_url"))) throw new IOException("Unexpected release location");
        String file = "BusinessLedger-Setup-" + version + ".exe";
        String checksum = "BusinessLedger-Setup-" + version + ".sha256";
        boolean installerFound = false, checksumFound = false;
        if (!(release.get("assets") instanceof List<?> assets)) throw new IOException("Missing release assets");
        for (Object value : assets) {
            if (!(value instanceof Map<?, ?> asset)) continue;
            String expectedName = file.equals(asset.get("name")) ? file : checksum.equals(asset.get("name")) ? checksum : null;
            if (expectedName == null) continue;
            if (!"uploaded".equals(asset.get("state")) || !(asset.get("size") instanceof Long size) || size <= 0) continue;
            if (!(base + "download/" + tag + "/" + expectedName).equals(asset.get("browser_download_url")))
                throw new IOException("Unexpected download location");
            if (expectedName.equals(file)) installerFound = true; else checksumFound = true;
        }
        // Incomplete publications do not trigger download prompts.
        return installerFound && checksumFound ? new Release(version, URI.create(base + "download/" + tag + "/" + file), URI.create(expectedPage)) : null;
    }

    static int compare(String left, String right) throws IOException {
        int[] a = parts(left), b = parts(right);
        for (int i = 0; i < 3; i++) { int compared = Integer.compare(a[i], b[i]); if (compared != 0) return compared; }
        return 0;
    }
    private static int[] parts(String version) throws IOException {
        if (!version.matches("(0|[1-9][0-9]{0,4})\\.(0|[1-9][0-9]{0,4})\\.(0|[1-9][0-9]{0,4})")) throw new IOException("Invalid release version");
        int[] values = Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
        for (int value : values) if (value > 65535) throw new IOException("Invalid release version");
        return values;
    }

    /** Small bounded JSON reader for public release metadata; no external runtime dependency. */
    private static final class Json {
        private final String text; private int at;
        Json(String text) { this.text = text; }
        Object read() throws IOException { Object result = value(0); space(); if (at != text.length()) throw bad(); return result; }
        private void space() { while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) at++; }
        private IOException bad() { return new IOException("Invalid release JSON"); }
        private boolean take(char c) { space(); if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
        private Object value(int depth) throws IOException {
            if (depth > 40) throw bad(); space(); if (at == text.length()) throw bad();
            char c = text.charAt(at);
            if (c == '"') return string();
            if (take('{')) {
                Map<String, Object> result = new HashMap<>(); if (take('}')) return result;
                do { space(); if (at == text.length() || text.charAt(at) != '"') throw bad(); String key = string();
                    if (!take(':') || result.containsKey(key)) throw bad(); result.put(key, value(depth + 1));
                } while (take(',')); if (!take('}')) throw bad(); return result;
            }
            if (take('[')) { List<Object> result = new ArrayList<>(); if (take(']')) return result;
                do { result.add(value(depth + 1)); } while (take(',')); if (!take(']')) throw bad(); return result; }
            if (text.startsWith("true", at)) { at += 4; return true; }
            if (text.startsWith("false", at)) { at += 5; return false; }
            if (text.startsWith("null", at)) { at += 4; return null; }
            int begin = at; while (at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            String number = text.substring(begin, at);
            if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw bad();
            try { if (number.indexOf('.') < 0 && number.indexOf('e') < 0 && number.indexOf('E') < 0) return Long.valueOf(number);
                return new java.math.BigDecimal(number); } catch (NumberFormatException ex) { throw bad(); }
        }
        private String string() throws IOException {
            at++; StringBuilder result = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++); if (c == '"') return result.toString(); if (c < 32) throw bad();
                if (c != '\\') { result.append(c); continue; }
                if (at == text.length()) throw bad(); c = text.charAt(at++);
                switch (c) {
                    case '"', '\\', '/' -> result.append(c);
                    case 'b' -> result.append('\b'); case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n'); case 'r' -> result.append('\r'); case 't' -> result.append('\t');
                    case 'u' -> { if (at + 4 > text.length()) throw bad(); try { result.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); }
                        catch (NumberFormatException ex) { throw bad(); } at += 4; }
                    default -> throw bad();
                }
            }
            throw bad();
        }
    }
}

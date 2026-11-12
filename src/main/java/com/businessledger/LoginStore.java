package com.businessledger;

import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.sql.*;
import java.util.*;

/** Login identities live outside profile ledgers; each identity maps to one database. */
final class LoginStore implements AutoCloseable {
    static final int ITERATIONS = 600_000;
    record Session(Connection connection, String name, boolean firstLogin) {
        Session(Connection connection, String name) { this(connection, name, false); }
    }
    private final LocalDatabase.Server server;
    private final Connection catalog;
    LoginStore(LocalDatabase.Server server) throws SQLException {
        this.server = server; catalog = server.connect("business_ledger_profiles");
        try (Statement s = catalog.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS login_accounts (username TEXT PRIMARY KEY, display_name TEXT NOT NULL, salt BYTEA NOT NULL, password_hash BYTEA NOT NULL, iterations INTEGER NOT NULL, database_name TEXT NOT NULL UNIQUE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS login_settings (name TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
    }
    List<String> names() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement s = catalog.createStatement(); ResultSet r = s.executeQuery("SELECT display_name FROM login_accounts ORDER BY username")) { while (r.next()) names.add(r.getString(1)); }
        return names;
    }
    String rememberedName() throws SQLException {
        try (Statement s = catalog.createStatement(); ResultSet r = s.executeQuery("SELECT value FROM login_settings WHERE name='last_login'")) { return r.next() ? r.getString(1) : ""; }
    }
    private void remember(String name) throws SQLException {
        try (PreparedStatement s = catalog.prepareStatement("INSERT INTO login_settings(name,value) VALUES ('last_login',?) ON CONFLICT(name) DO UPDATE SET value=EXCLUDED.value")) { s.setString(1, name); s.executeUpdate(); }
    }
    static String normalize(String name) { return name.trim().toLowerCase(Locale.ROOT); }
    static byte[] hash(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    Session login(String name, char[] password) throws Exception {
        if (password.length > 256) throw new IllegalArgumentException("Account name or password is incorrect.");
        String display, database; byte[] expected, salt; int iterations;
        try (PreparedStatement s = catalog.prepareStatement("SELECT display_name,database_name,password_hash,salt,iterations FROM login_accounts WHERE username=?")) {
            s.setString(1, normalize(name));
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) { hash(password, new byte[32], ITERATIONS); throw new IllegalArgumentException("Account name or password is incorrect."); }
                display = r.getString(1); database = r.getString(2); expected = r.getBytes(3); salt = r.getBytes(4); iterations = r.getInt(5);
            }
        }
        byte[] actual = hash(password, salt, iterations);
        boolean matches = MessageDigest.isEqual(expected, actual); Arrays.fill(actual, (byte) 0);
        if (!matches) throw new IllegalArgumentException("Account name or password is incorrect.");
        Connection ledger = server.connect(database);
        try { remember(display); return new Session(ledger, display); }
        catch (SQLException ex) { ledger.close(); throw ex; }
    }
    Session create(String name, char[] password) throws Exception {
        String display = name.trim(), username = normalize(name);
        if (display.isBlank() || display.length() > 64 || display.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Account name must contain 1–64 printable characters.");
        if (password.length < 12 || password.length > 256) throw new IllegalArgumentException("Use a password between 12 and 256 characters.");
        byte[] salt = new byte[32]; new SecureRandom().nextBytes(salt);
        byte[] hashed = hash(password, salt, ITERATIONS);
        String database = null; boolean created = false;
        catalog.setAutoCommit(false);
        try {
            // CREATE DATABASE uses another connection while the catalog lock reserves the first profile.
            try (Statement s = catalog.createStatement()) { s.execute("SELECT pg_advisory_xact_lock(734910284)"); }
            boolean first;
            try (PreparedStatement s = catalog.prepareStatement("SELECT 1 FROM login_accounts WHERE username=?")) {
                s.setString(1, username); try (ResultSet r = s.executeQuery()) { if (r.next()) throw new IllegalArgumentException("That account name already exists."); }
            }
            try (Statement s = catalog.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM login_accounts")) { r.next(); first = r.getLong(1) == 0; }
            database = first ? "business_ledger" : "ledger_profile_" + UUID.randomUUID().toString().replace("-", "");
            if (!first) {
                try (Connection admin = server.connect("postgres"); Statement s = admin.createStatement()) { s.executeUpdate("CREATE DATABASE " + database); created = true; }
            }
            try (PreparedStatement s = catalog.prepareStatement("INSERT INTO login_accounts(username,display_name,salt,password_hash,iterations,database_name) VALUES (?,?,?,?,?,?)")) {
                s.setString(1, username); s.setString(2, display); s.setBytes(3, salt); s.setBytes(4, hashed); s.setInt(5, ITERATIONS); s.setString(6, database); s.executeUpdate();
            }
            catalog.commit();
        } catch (Exception ex) {
            catalog.rollback();
            // Only drop the new empty generated database, never the inherited ledger.
            if (created && database != null) try (Connection admin = server.connect("postgres"); Statement s = admin.createStatement()) { s.executeUpdate("DROP DATABASE " + database); }
            catch (SQLException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        } finally { catalog.setAutoCommit(true); Arrays.fill(hashed, (byte) 0); }
        Connection ledger = server.connect(database);
        try { remember(display); return new Session(ledger, display, true); }
        catch (SQLException ex) { ledger.close(); throw ex; }
    }
    @Override public void close() throws SQLException { catalog.close(); }
}

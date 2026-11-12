package com.businessledger;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Uses only the disposable cluster on port 55439, with fresh profile/ledger databases. */
public final class LoginIntegrationCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        LocalDatabase.Server server = new LocalDatabase.Server("jdbc:postgresql://127.0.0.1:55439/", "ledger_test", "");
        try (Connection admin = server.connect("postgres"); Statement s = admin.createStatement()) {
            s.executeUpdate("CREATE DATABASE business_ledger");
            s.executeUpdate("CREATE DATABASE business_ledger_profiles");
        }
        try (Connection legacy = server.connect("business_ledger"); Statement s = legacy.createStatement()) {
            s.executeUpdate("CREATE TABLE legacy_marker (value TEXT)"); s.executeUpdate("INSERT INTO legacy_marker VALUES ('existing records')");
        }
        char[] password = "a long test password 123!".toCharArray();
        String secondDatabase;
        try (LoginStore store = new LoginStore(server)) {
            check(store.names().isEmpty(), "Fresh catalog has no login profiles");
            LoginStore.Session first = store.create("Josh", password);
            try (Connection c = first.connection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT value FROM legacy_marker")) {
                check(r.next() && r.getString(1).equals("existing records"), "First profile inherits existing ledger");
            }
            LoginStore.Session second = store.create("Other", password);
            try (Connection c = second.connection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT current_database(),to_regclass('legacy_marker')")) {
                r.next(); secondDatabase = r.getString(1); check(r.getString(2) == null, "Second profile cannot see first ledger's tables");
            }
            boolean rejected = false;
            try { store.login("Josh", "incorrect password".toCharArray()); } catch (IllegalArgumentException ex) { rejected = true; }
            check(rejected, "Incorrect password rejected");
            check(store.rememberedName().equals("Other"), "Failed login does not overwrite remembered account");
            rejected = false;
            try { store.create(" jOsH ", password); } catch (IllegalArgumentException ex) { rejected = true; }
            check(rejected, "Case-insensitive duplicate account rejected");
            try (Connection c = store.login(" jOsH ", password).connection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT current_database()")) { r.next(); check(r.getString(1).equals("business_ledger"), "Case-insensitive login selects correct ledger"); }
            check(store.rememberedName().equals("Josh"), "Successful account remembered");
        }
        try (LoginStore reopened = new LoginStore(server)) {
            check(reopened.names().equals(List.of("Josh", "Other")), "Account dropdown survives restart");
            check(reopened.rememberedName().equals("Josh"), "Last login survives restart");
            try (Connection c = reopened.login("Other", password).connection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT current_database()")) { r.next(); check(r.getString(1).equals(secondDatabase), "Second profile database persists"); }
        }
        try (Connection catalog = server.connect("business_ledger_profiles"); Statement s = catalog.createStatement(); ResultSet r = s.executeQuery("SELECT salt,password_hash,iterations FROM login_accounts ORDER BY username")) {
            r.next(); byte[] firstSalt = r.getBytes(1), firstHash = r.getBytes(2);
            check(firstSalt.length == 32 && firstHash.length == 32 && r.getInt(3) == 600000, "Salt and PBKDF2 work factor stored");
            r.next(); check(!Arrays.equals(firstSalt, r.getBytes(1)) && !Arrays.equals(firstHash, r.getBytes(2)), "Identical passwords have unique salts and hashes");
        }
        Path folder = Files.createTempDirectory(Path.of("target"), "secret-test-"); Path secret = folder.resolve("credential.dpapi");
        try {
            WindowsSecrets.save(secret, "database test secret");
            check(WindowsSecrets.read(secret).equals("database test secret"), "Windows DPAPI round trip");
            check(!new String(Files.readAllBytes(secret), java.nio.charset.StandardCharsets.ISO_8859_1).contains("database test secret"), "Database password file is not plaintext");
        } finally { Files.deleteIfExists(secret); Files.deleteIfExists(folder); }
        Arrays.fill(password, '\0'); System.out.println("Login isolation, persistence, hashing, and Windows credential checks passed.");
    }
}

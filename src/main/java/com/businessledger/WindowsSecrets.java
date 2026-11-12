package com.businessledger;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** Windows DPAPI binds the stored database credential to the current Windows user. */
final class WindowsSecrets {
    static byte[] transform(byte[] value, boolean protect) throws Exception {
        String operation = protect ? "Protect" : "Unprotect";
        String script = "$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Security; "
                + "$b=[Convert]::FromBase64String([Console]::In.ReadToEnd()); "
                + "[Console]::Out.Write([Convert]::ToBase64String([Security.Cryptography.ProtectedData]::" + operation
                + "($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)))";
        // Only a fixed script goes into command arguments; the credential travels over stdin.
        String override=System.getProperty("ledger.secrets.helper");
        Path helper=override==null?AppPaths.applicationDirectory().resolve("LedgerSecrets.exe"):Path.of(override);
        Process p=Files.isRegularFile(helper)?new ProcessBuilder(helper.toString(),protect?"protect":"unprotect").start():new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script).start();
        try {
            try (var input = p.getOutputStream()) { input.write(Base64.getEncoder().encode(value)); }
            // DPAPI input and output are tiny, below pipe capacity.
            if (!p.waitFor(30, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("Windows credential protection timed out."); }
            if (p.exitValue() != 0) throw new IllegalStateException("Windows could not " + (protect ? "protect" : "unlock") + " the database credential for this Windows user.");
            return Base64.getDecoder().decode(new String(p.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim());
        } finally { if (p.isAlive()) p.destroyForcibly(); }
    }

    static void save(Path path, String secret) throws Exception {
        byte[] plain = secret.getBytes(StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(path.getParent(), "credential-", ".tmp");
        try {
            Files.write(temporary, transform(plain, true));
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { java.util.Arrays.fill(plain, (byte) 0); Files.deleteIfExists(temporary); }
    }

    static String read(Path path) throws Exception {
        byte[] plain = transform(Files.readAllBytes(path), false);
        try { return new String(plain, StandardCharsets.UTF_8); }
        finally { java.util.Arrays.fill(plain, (byte) 0); }
    }
}

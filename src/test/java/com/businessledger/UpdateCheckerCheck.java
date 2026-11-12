package com.businessledger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

public final class UpdateCheckerCheck {
    private static String fixture() {
        return """
            {"tag_name":"v1.10.0","draft":false,"prerelease":false,
            "html_url":"https://github.com/Mr-Cee/Business-Ledger/releases/tag/v1.10.0",
            "body":"Notes with \\"quotes\\" and a line\\nnext line", "assets":[
            {"name":"BusinessLedger-Setup-1.10.0.exe","state":"uploaded","size":1234,
             "browser_download_url":"https://github.com/Mr-Cee/Business-Ledger/releases/download/v1.10.0/BusinessLedger-Setup-1.10.0.exe"},
            {"name":"BusinessLedger-Setup-1.10.0.sha256","state":"uploaded","size":100,
             "browser_download_url":"https://github.com/Mr-Cee/Business-Ledger/releases/download/v1.10.0/BusinessLedger-Setup-1.10.0.sha256"}]}
            """;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(String json) throws Exception {
        try { UpdateChecker.parse(json); throw new AssertionError("Accepted invalid release metadata"); } catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        String valid = fixture();
        check(UpdateChecker.compare("1.10.0", "1.9.9") > 0, "Numeric version comparison");
        check(UpdateChecker.compare("1.3.0", "1.3.0") == 0, "Same version");
        check(UpdateChecker.compare("1.2.9", "1.3.0") < 0, "Downgrade");
        check(UpdateChecker.parse(valid).version().equals("1.10.0"), "Complete release");
        check(UpdateChecker.parse(valid.replace("\"draft\":false", "\"draft\":true")) == null, "Draft ignored");
        check(UpdateChecker.parse(valid.replace("\"prerelease\":false", "\"prerelease\":true")) == null, "Prerelease ignored");
        check(UpdateChecker.parse(valid.replace("\"size\":1234", "\"size\":0")) == null, "Empty installer ignored");
        check(UpdateChecker.parse(valid.replace("\"state\":\"uploaded\"", "\"state\":\"new\"")) == null, "Incomplete upload ignored");
        check(UpdateChecker.parse(valid.replace(".sha256", ".missing")) == null, "Missing checksum ignored");
        rejects(valid.replace("download/v1.10.0/", "download/v1.10.0/../"));
        rejects(valid.replace("https://github.com/", "https://github.com.evil.example/"));
        rejects(valid.replace("v1.10.0", "v65536.10.0"));
        rejects(valid + " trailing data"); rejects("{");
        rejects(valid.replace("\"tag_name\":", "\"tag_name\":\"v9.0.0\",\"tag_name\":"));
        byte[] bytes = valid.getBytes(StandardCharsets.UTF_8);
        check(UpdateChecker.response(200, bytes).version().equals("1.10.0"), "Successful response");
        check(UpdateChecker.response(404, new byte[0]) == null, "No release");
        for (int status : new int[]{403, 429, 500, 302}) {
            try { UpdateChecker.response(status, bytes); throw new AssertionError("Accepted error or redirect response"); }
            catch (IOException expected) { }
        }
        check(java.util.Arrays.equals(bytes, UpdateChecker.readBody(new ByteArrayInputStream(bytes))), "Body preserved");
        try { UpdateChecker.readBody(new ByteArrayInputStream(new byte[1_048_577])); throw new AssertionError("Oversized response accepted"); }
        catch (IOException expected) { }
        try { UpdateChecker.readBody(new InputStream() { public int read() throws IOException { throw new IOException("offline"); } });
            throw new AssertionError("Connection failure ignored"); } catch (IOException expected) { }
        System.out.println("Update checks passed: versions, complete assets, draft/prerelease handling, URL validation, JSON, response statuses, chunked bodies, connection failures, and response size limits. No network required.");
    }
}

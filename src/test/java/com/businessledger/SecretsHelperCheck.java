package com.businessledger;
public final class SecretsHelperCheck {
    public static void main(String[] args)throws Exception{
        System.setProperty("ledger.secrets.helper",args[0]);byte[] original="Test-only credential".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] encrypted=WindowsSecrets.transform(original,true),roundtrip=WindowsSecrets.transform(encrypted,false);
        if(java.util.Arrays.equals(original,encrypted)||!java.util.Arrays.equals(original,roundtrip))throw new AssertionError("Credential protection roundtrip failed");
        System.out.println("Bundled Windows credential helper protects and restores credentials using DPAPI.");
    }
}

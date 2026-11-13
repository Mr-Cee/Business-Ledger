package com.businessledger;

import java.io.*;
import java.net.URI;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Synthetic installer bytes only. Does not launch installers or make network requests. */
public final class UpdateCacheCheck {
    static final byte[] BYTES="MZ synthetic installer, never execute".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static String hash()throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BYTES));}
    static UpdateChecker.Release release(String version){String base="https://github.com/Mr-Cee/Business-Ledger/releases/";return new UpdateChecker.Release(version,URI.create(base+"download/v"+version+"/"+UpdateCache.file(version)),URI.create(base+"tag/v"+version),URI.create(base+"download/v"+version+"/BusinessLedger-Setup-"+version+".sha256"),BYTES.length);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @FunctionalInterface interface Attempt{void run()throws Exception;}
    static void rejects(Attempt attempt,String message)throws Exception{boolean failed=false;try{attempt.run();}catch(IOException|CancellationException expected){failed=true;}check(failed,message);}
    public static void main(String[] args)throws Exception{
        if(args.length==2&&args[0].equals("--reuse")){
            var cached=UpdateDownload.download(release("2.0.0"),()->false,count->{},Path.of(args[1]),uri->{throw new AssertionError("Fresh process must reuse verified cache offline");});
            UpdateDownload.verifyReady(cached);System.out.println("Fresh JVM reused the saved installer without network access.");return;
        }
        Path temp=Files.createTempDirectory(Path.of("target"),"update-cache-check-").toAbsolutePath();
        int[] checksumRequests={0},installerRequests={0};long[] progress={0};String hash=hash();
        UpdateDownload.Source source=uri->{if(uri.getPath().endsWith(".sha256")){checksumRequests[0]++;String version=uri.getPath().split("/v")[1].split("/")[0];return new ByteArrayInputStream((hash+"  "+UpdateCache.file(version)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}installerRequests[0]++;return new ByteArrayInputStream(BYTES);};
        var release=release("2.0.0");var ready=UpdateDownload.download(release,()->false,count->progress[0]=count,temp,source);
        check(checksumRequests[0]==1&&installerRequests[0]==1&&progress[0]==BYTES.length,"First download verifies and reports progress");
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-cp",System.getProperty("java.class.path"),UpdateCacheCheck.class.getName(),"--reuse",temp.toString()).inheritIO().start();
        if(!process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Fresh process cache check timed out");}check(process.exitValue()==0,"Cache reuse survives process restart");
        var again=UpdateDownload.download(release("2.0.0"),()->false,count->{},temp,uri->{throw new AssertionError("Cache hit must not contact network");});
        check(ready.installer().equals(again.installer()),"Cache survives a new release object and request without in-memory pending state");UpdateDownload.verifyReady(again);
        byte[] changed=BYTES.clone();changed[0]='X';Files.write(ready.installer(),changed);
        UpdateDownload.download(release,()->false,count->{},temp,source);check(installerRequests[0]==2&&Arrays.equals(Files.readAllBytes(ready.installer()),BYTES),"Same-size corruption redownloads instead of executing");
        Path receipt=ready.installer().getParent().resolve("receipt.properties");Files.writeString(receipt,"invalid receipt");
        UpdateDownload.download(release,()->false,count->{},temp,source);check(installerRequests[0]==2,"Missing receipt refreshes checksum and reuses valid binary");
        Files.write(ready.installer(),new byte[]{1,2});UpdateDownload.download(release,()->false,count->{},temp,source);check(installerRequests[0]==3,"Incomplete cache is repaired");
        Path abandoned=ready.installer().resolveSibling(UpdateCache.file("2.0.0")+".part");Files.writeString(abandoned,"abandoned");
        Path guard=ready.installer().resolveSibling("download.lock");
        try(FileChannel channel=FileChannel.open(guard,StandardOpenOption.WRITE);FileLock held=channel.lock()){
            rejects(()->UpdateDownload.download(release,()->false,count->{},temp,source),"Concurrent cache access refuses rather than corrupting a download");
            check(!UpdateCache.cleanup(temp,"2.0.0")&&Files.exists(ready.installer()),"Cleanup waits for cache lock");
        }
        boolean[] canceled={false};var cancelRelease=release("2.1.0");
        rejects(()->UpdateDownload.download(cancelRelease,()->canceled[0],count->canceled[0]=true,temp,source),"Cancellation does not publish partial download");
        Path canceledDirectory=UpdateCache.root(temp).resolve("2.1.0");check(!Files.exists(canceledDirectory.resolve(UpdateCache.file("2.1.0")))&&!Files.exists(canceledDirectory.resolve(UpdateCache.file("2.1.0")+".part")),"Canceled partial removed");
        rejects(()->UpdateDownload.download(release("2.2.0"),()->false,count->{},temp,uri->new ByteArrayInputStream(uri.getPath().endsWith(".sha256")?(hash+"  "+UpdateCache.file("2.2.0")).getBytes(java.nio.charset.StandardCharsets.UTF_8):changed)),"Invalid downloaded bytes are rejected");
        UpdateDownload.download(release("3.0.0"),()->false,count->{},temp,source);
        Path legacy=Files.createDirectory(temp.resolve("BusinessLedger-update-legacy"));Path legacyFile=legacy.resolve(UpdateCache.file("1.9.0"));Files.write(legacyFile,BYTES);Path note=legacy.resolve("keep.txt");Files.writeString(note,"unrelated");
        Path user=Files.createDirectory(temp.resolve("user-files"));Path userFile=user.resolve(UpdateCache.file("1.9.0"));Files.write(userFile,BYTES);
        check(UpdateCache.cleanup(temp,"2.0.0"),"Completed version cleanup finishes when files unlock");
        check(!Files.exists(ready.installer())&&!Files.exists(receipt)&&!Files.exists(abandoned),"Installed binary, receipt and stale partial cleared");
        check(Files.exists(UpdateCache.root(temp).resolve("3.0.0").resolve(UpdateCache.file("3.0.0"))),"Uninstalled newer version retained");
        check(!Files.exists(legacyFile)&&Files.exists(note)&&Files.exists(userFile),"Legacy cache cleaned without touching unrelated files");
        rejects(()->UpdateCache.obtain(temp,release("../bad"),hash,()->new ByteArrayInputStream(BYTES),()->false,count->{}),"Version cannot escape cache directory");
        System.out.println("Persistent/offline reuse, integrity and size checks, receipt repair, cancellation, lock contention, installed/legacy cleanup and newer/unrelated file preservation passed. No installer executed.");
    }
}

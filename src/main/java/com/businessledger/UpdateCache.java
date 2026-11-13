package com.businessledger;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import java.util.Properties;

/** Disposable installer files only; never walks or recursively deletes ledger data. */
final class UpdateCache {
    @FunctionalInterface interface Download {InputStream open()throws IOException;}
    static Path root(Path temporary){return temporary.toAbsolutePath().normalize().resolve("BusinessLedger-update-cache");}
    static String file(String version){return "BusinessLedger-Setup-"+version+".exe";}
    private static void version(String value)throws IOException{UpdateChecker.compare(value,value);}
    private static void safe(Path path)throws IOException{
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(path)||!path.toRealPath().equals(path.toAbsolutePath().normalize())))
            throw new IOException("The update cache contains a redirected path.");
    }
    private static FileLock lock(FileChannel channel)throws IOException{
        try{return channel.tryLock();}catch(OverlappingFileLockException ex){return null;}
    }
    static boolean present(UpdateChecker.Release release){
        try{
            version(release.version());Path directory=root(Path.of(System.getProperty("java.io.tmpdir"))).resolve(release.version());safe(directory);
            Path installer=directory.resolve(file(release.version())),receipt=directory.resolve("receipt.properties");safe(installer);safe(receipt);
            return Files.isRegularFile(installer,LinkOption.NOFOLLOW_LINKS)&&Files.size(installer)==release.size()&&Files.isRegularFile(receipt,LinkOption.NOFOLLOW_LINKS);
        }catch(IOException ex){return false;}
    }
    static UpdateDownload.Ready cached(Path temporary,UpdateChecker.Release release,BooleanSupplier canceled,LongConsumer progress)throws Exception{
        version(release.version());Path directory=root(temporary).resolve(release.version());
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))return null;
        safe(directory);Path receipt=directory.resolve("receipt.properties"),installer=directory.resolve(file(release.version())),guard=directory.resolve("download.lock");
        safe(receipt);safe(installer);safe(guard);
        try(FileChannel channel=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock held=lock(channel)){
            if(held==null)throw new IOException("This update is already being downloaded or cleaned up. Try again shortly.");
            if(!Files.isRegularFile(receipt,LinkOption.NOFOLLOW_LINKS)||Files.size(receipt)>4096||!Files.isRegularFile(installer,LinkOption.NOFOLLOW_LINKS)||Files.size(installer)!=release.size())return null;
            Properties metadata=new Properties();try(Reader input=Files.newBufferedReader(receipt)){metadata.load(input);}catch(IOException|IllegalArgumentException ex){return null;}
            String hash=metadata.getProperty("sha256","");
            if(!hash.matches("[0-9a-f]{64}")||!release.version().equals(metadata.getProperty("version"))||!Long.toString(release.size()).equals(metadata.getProperty("size"))||!release.installer().toString().equals(metadata.getProperty("installer"))||!release.checksum().toString().equals(metadata.getProperty("checksum")))return null;
            try(InputStream input=Files.newInputStream(installer)){
                UpdateDownload.copyVerified(input,OutputStream.nullOutputStream(),release.size(),hash,canceled,progress);
                Path partial=directory.resolve(file(release.version())+".part");
                try{safe(partial);if(Files.isRegularFile(partial,LinkOption.NOFOLLOW_LINKS))Files.deleteIfExists(partial);}catch(IOException ignored){/* Retry stale-file cleanup after installation. */}
                return new UpdateDownload.Ready(installer,hash,release.version());
            }catch(IOException invalid){return null;}
        }
    }
    private static void receipt(Path directory,UpdateChecker.Release release,String hash)throws IOException{
        Path target=directory.resolve("receipt.properties"),partial=directory.resolve("receipt.properties.part");safe(target);safe(partial);Files.deleteIfExists(partial);
        Properties metadata=new Properties();metadata.setProperty("version",release.version());metadata.setProperty("size",Long.toString(release.size()));metadata.setProperty("sha256",hash);metadata.setProperty("installer",release.installer().toString());metadata.setProperty("checksum",release.checksum().toString());
        try{
            try(Writer writer=Files.newBufferedWriter(partial,StandardOpenOption.CREATE_NEW)){metadata.store(writer,"Verified Business Ledger update");}
            move(partial,target);
        }finally{Files.deleteIfExists(partial);}
    }
    private static void move(Path from,Path to)throws IOException{
        try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ex){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}
    }
    static UpdateDownload.Ready obtain(Path temporary,UpdateChecker.Release identity,String hash,Download download,BooleanSupplier canceled,LongConsumer progress)throws Exception{
        String release=identity.version();long size=identity.size();
        version(release);if(size<=0||size>UpdateDownload.MAX_INSTALLER||!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid update cache metadata.");
        if(canceled.getAsBoolean())throw new CancellationException();
        Path root=root(temporary),directory=root.resolve(release);
        Files.createDirectories(root);safe(root);Files.createDirectories(directory);safe(directory);
        Path installer=directory.resolve(file(release)),partial=directory.resolve(file(release)+".part"),guard=directory.resolve("download.lock");
        safe(installer);safe(partial);safe(guard);
        try(FileChannel channel=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock held=lock(channel)){
            if(held==null)throw new IOException("This update is already being downloaded or cleaned up. Try again shortly.");
            if(Files.exists(installer,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(installer,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid cached installer file.");
            Files.deleteIfExists(partial);
            if(Files.isRegularFile(installer,LinkOption.NOFOLLOW_LINKS)&&Files.size(installer)==size){
                boolean verified=false;
                try(InputStream input=Files.newInputStream(installer)){
                    UpdateDownload.copyVerified(input,OutputStream.nullOutputStream(),size,hash,canceled,progress);
                    verified=true;
                }catch(IOException invalid){/* A changed or incomplete installer must be replaced, never run. */}
                if(verified){receipt(directory,identity,hash);return new UpdateDownload.Ready(installer,hash,release);}
            }
            Files.deleteIfExists(partial);boolean complete=false;
            try{
                try(InputStream input=download.open();OutputStream output=Files.newOutputStream(partial,StandardOpenOption.CREATE_NEW)){
                    UpdateDownload.copyVerified(input,output,size,hash,canceled,progress);
                }
                if(canceled.getAsBoolean())throw new CancellationException();
                move(partial,installer);receipt(directory,identity,hash);
                complete=true;return new UpdateDownload.Ready(installer,hash,release);
            }finally{if(!complete)Files.deleteIfExists(partial);}
        }
    }
    static boolean cleanup(Path temporary,String installed)throws IOException{
        version(installed);Path temp=temporary.toAbsolutePath().normalize(),root=root(temp);boolean done=true;
        if(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)){
            safe(root);
            try(DirectoryStream<Path> directories=Files.newDirectoryStream(root)){
                for(Path directory:directories){
                    String release=directory.getFileName().toString();
                    try{version(release);}catch(IOException unrelated){continue;}
                    try{if(UpdateChecker.compare(release,installed)>0||!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))continue;safe(directory);
                        Path guard=directory.resolve("download.lock");safe(guard);
                        try(FileChannel channel=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock held=lock(channel)){
                            if(held==null){done=false;continue;}
                            for(String name:new String[]{file(release),file(release)+".part","receipt.properties","receipt.properties.part"}){Path target=directory.resolve(name);safe(target);if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))Files.deleteIfExists(target);}
                        }
                    }catch(IOException ex){done=false;}
                }
            }
        }
        // Earlier releases used a unique temporary folder for each download.
        if(Files.isDirectory(temp))try(DirectoryStream<Path> legacy=Files.newDirectoryStream(temp,"BusinessLedger-update-*")){
            for(Path directory:legacy){
                if(directory.equals(root)||!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))continue;
                try{safe(directory);try(DirectoryStream<Path> files=Files.newDirectoryStream(directory,"BusinessLedger-Setup-*.exe")){
                    for(Path target:files){String name=target.getFileName().toString();String release=name.substring("BusinessLedger-Setup-".length(),name.length()-4);
                        try{version(release);}catch(IOException unrelated){continue;}
                        try{if(UpdateChecker.compare(release,installed)<=0){safe(target);if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))Files.deleteIfExists(target);}}catch(IOException ex){done=false;}
                    }
                }
                boolean empty;try(DirectoryStream<Path> contents=Files.newDirectoryStream(directory)){empty=!contents.iterator().hasNext();}if(empty)Files.deleteIfExists(directory);
                }catch(IOException ex){done=false;}
            }
        }
        return done;
    }
    static void cleanupInstalled(){
        Thread worker=new Thread(()->{
            for(int attempt=0;attempt<120;attempt++){
                try{if(cleanup(Path.of(System.getProperty("java.io.tmpdir")),AppVersion.version()))return;}
                catch(Exception ignored){/* Cache cleanup must not block access to the ledger. */}
                try{Thread.sleep(500);}catch(InterruptedException ex){Thread.currentThread().interrupt();return;}
            }
        },"ledger-update-cache-cleanup");worker.setDaemon(true);worker.start();
    }
}

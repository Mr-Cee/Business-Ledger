package com.businessledger;

import java.nio.channels.*;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Separate five-file sets; manual and safety snapshots are never rotation candidates. */
final class AutomaticBackups {
    enum Kind {CLOSING("closing"),TIMED("timed");final String key;Kind(String key){this.key=key;}}
    static final int KEEP=5;
    private static final Map<String,Object> GUARDS=new ConcurrentHashMap<>();
    record Result(Path path,boolean created){}
    static Path directory(LedgerBackup backups,Kind kind)throws Exception{
        return directory(backups.options().folder(),backups.profile(),kind);
    }
    private static Path directory(Path chosen,String profile,Kind kind)throws Exception{
        Files.createDirectories(chosen);
        Path base=chosen.toRealPath().resolve("automatic").resolve(profile.substring(0,32));Files.createDirectories(base);
        if(!base.toRealPath().equals(base.toAbsolutePath().normalize()))throw new java.io.IOException("Automatic backup folder is redirected.");
        Path folder=base.resolve(kind.key);Files.createDirectories(folder);
        if(!folder.toRealPath().equals(folder.toAbsolutePath().normalize()))throw new java.io.IOException("Automatic backup folder is redirected.");return folder;
    }
    static List<LedgerBackup.Snapshot> owned(Path folder,String profile,Kind kind)throws Exception{
        List<LedgerBackup.Snapshot> result=new ArrayList<>();
        try(DirectoryStream<Path> files=Files.newDirectoryStream(folder,"*.ledgerbackup")){
            for(Path path:files)if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))try{
                var info=LedgerBackup.snapshotInfo(path);
                if(info.profile().equals(profile)&&info.kind().equals(kind.key)&&info.generation()>0)result.add(info);
            }catch(Exception ignored){/* Unrecognized, manual or damaged files must never be deleted. */}
        }return result;
    }
    static Result take(Connection db,Kind kind,String ledger)throws Exception{
        LedgerBackup backups=new LedgerBackup(db);String profile=backups.profile();Path destination=backups.options().folder();
        synchronized(GUARDS.computeIfAbsent(profile,k->new Object())){
            Path folder=directory(destination,profile,kind),guard=folder.getParent().resolve("backup.lock");
            if(Files.isSymbolicLink(guard))throw new java.io.IOException("Automatic backup lock is redirected.");
            try(FileChannel channel=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock held=channel.lock()){
                List<LedgerBackup.Snapshot> current=owned(folder,profile,kind);
                if(kind==Kind.TIMED){
                    List<LedgerBackup.Snapshot> known=new ArrayList<>(current);known.addAll(owned(directory(destination,profile,Kind.CLOSING),profile,Kind.CLOSING));
                    Path last=backups.lastSuccessful(destination);if(last!=null&&Files.isRegularFile(last))try{var info=LedgerBackup.snapshotInfo(last);if(info.profile().equals(profile))known.add(info);}catch(Exception ignored){}
                    var latest=known.stream().max(Comparator.comparing(LedgerBackup.Snapshot::created));
                    if(latest.isPresent()&&latest.get().fingerprint().equals(backups.fingerprint()))return new Result(latest.get().path(),false);
                }
                long generation=Math.addExact(current.stream().mapToLong(LedgerBackup.Snapshot::generation).max().orElse(0),1);
                Path target=LedgerBackup.datedFile(folder,ledger,kind.key);backups.create(target,ledger,kind.key,generation,destination);
                List<LedgerBackup.Snapshot> retained=owned(folder,profile,kind);retained.sort(Comparator.comparingLong(LedgerBackup.Snapshot::generation).reversed());
                for(int i=KEEP;i<retained.size();i++){
                    Path old=retained.get(i).path();if(!old.getParent().equals(folder)||!Files.isRegularFile(old,LinkOption.NOFOLLOW_LINKS))throw new java.io.IOException("Backup retention path changed.");Files.delete(old);
                }
                return new Result(target,true);
            }
        }
    }
}

package com.businessledger;

import java.nio.file.*;
import java.util.Properties;

public final class ReleaseCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        String launchReport=System.getProperty("ledger.launch.check");
        Path checks=launchReport==null?Path.of("target").toAbsolutePath():Path.of(launchReport).toAbsolutePath().getParent();Files.createDirectories(checks);
        Path root=Files.createTempDirectory(checks,"release-path-check-").toAbsolutePath();
        Path settings=root.resolve("config"),legacy=root.resolve("existing-ledger");Files.createDirectories(settings);
        check(AppPaths.resolve(legacy.toString(),settings).equals(legacy),"Explicit portable data home preserved");
        check(AppPaths.resolve(null,settings).equals(settings.resolve("Data")),"Fresh installed data home independent of app directory");
        Files.writeString(settings.resolve("data-location.txt"),legacy.toString());
        check(AppPaths.resolve(null,settings).equals(legacy),"Installer-selected legacy home honored");
        check(AppPaths.resolve(root.toString(),settings).equals(root),"Explicit home takes priority over installed location");
        Files.writeString(settings.resolve("data-location.txt"),"relative/path");
        try{AppPaths.resolve(null,settings);throw new AssertionError("Invalid pointer silently selected fresh data");}catch(java.io.IOException expected){}
        Properties release=new Properties();try(var input=ReleaseCheck.class.getResourceAsStream("/release.properties")){check(input!=null,"Version metadata embedded");release.load(input);}
        check(AppVersion.version().equals(release.getProperty("version"))&&AppVersion.version().matches("\\d+\\.\\d+\\.\\d+"),"About version matches build metadata");
        System.out.println("Release version "+AppVersion.version()+", explicit/installed/legacy data paths, and invalid pointer checks passed.");
        String report=System.getProperty("ledger.launch.check");if(report!=null)Files.writeString(Path.of(report),"Native Windows launcher loaded Business Ledger "+AppVersion.version()+" with its packaged Java runtime. No ledger database was opened.");
    }
}

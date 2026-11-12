package com.businessledger;
import java.io.IOException;
import java.nio.file.*;
/** Data locations are independent of installed application versions. */
final class AppPaths {
    static Path applicationDirectory()throws java.net.URISyntaxException{return Path.of(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent();}
    static Path userDirectory(){
        String override=System.getProperty("ledger.user.config");if(override!=null)return Path.of(override).toAbsolutePath().normalize();
        String local=System.getenv("LOCALAPPDATA");return (local==null?Path.of(System.getProperty("user.home"),"AppData","Local"):Path.of(local)).resolve("BusinessLedger").toAbsolutePath().normalize();
    }
    static Path resolve(String explicit,Path configuration)throws IOException{
        if(explicit!=null&&!explicit.isBlank())return Path.of(explicit).toAbsolutePath().normalize();
        Path pointer=configuration.resolve("data-location.txt");
        if(Files.exists(pointer)){
            String stored=Files.readString(pointer).trim();if(stored.isBlank()||!Path.of(stored).isAbsolute())throw new IOException("The saved ledger data location is invalid: "+pointer);
            return Path.of(stored).normalize();
        }
        return configuration.resolve("Data").toAbsolutePath().normalize();
    }
    static void initialize()throws IOException{System.setProperty("ledger.home",resolve(System.getProperty("ledger.home"),userDirectory()).toString());}
}

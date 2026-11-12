package com.businessledger;
import java.io.InputStream;
import java.util.Properties;
/** Release metadata is generated from pom.xml by each build entry point. */
final class AppVersion {
    private static final Properties INFO=new Properties();
    static{try(InputStream in=AppVersion.class.getResourceAsStream("/release.properties")){if(in!=null)INFO.load(in);}catch(Exception ex){throw new ExceptionInInitializerError(ex);}}
    static String version(){return INFO.getProperty("version",System.getProperty("jpackage.app-version","development"));}
    static String built(){return INFO.getProperty("built","Local development build");}
}

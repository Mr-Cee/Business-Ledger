using System;using System.IO;
namespace BusinessLedgerDeployment {
    internal static class ReleaseInfo {internal const string Version="0.0.0";internal const string LegacyHome="";internal const string PayloadHash="";}
    internal static class DeploymentCheck {
        static int Main(string[] args){
            try{
                if(args.Length!=2||!args[0].Contains("verified-payload-")||!args[1].Contains("target"))throw new Exception("Only extracted test payloads and disposable test metadata are allowed.");
                Deployment.StopOwnedDatabases(args[0],args[1]);
                Console.WriteLine("Verified bundled database ownership and graceful shutdown.");return 0;
            }catch(Exception ex){Console.Error.WriteLine(ex.ToString());return 1;}
        }
    }
}

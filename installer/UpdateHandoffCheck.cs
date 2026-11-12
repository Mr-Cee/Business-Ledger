using System;using System.Diagnostics;using System.IO;
namespace BusinessLedgerDeployment {
    internal static class UpdateHandoffCheck {
        static void Reject(string[] arguments) {
            int parent;string home;
            try{Entry.UpdateArguments(arguments,out parent,out home);throw new Exception("Invalid handoff accepted.");}
            catch(InvalidOperationException){}
        }
        static int Main() {
            try {
                string home=Path.GetFullPath(Path.Combine("target","update test data"));int parent;string parsed;
                Entry.UpdateArguments(new string[]{"/update","12345",home},out parent,out parsed);
                if(parent!=12345||parsed!=home)throw new Exception("Handoff argument boundaries changed.");
                Reject(new string[]{"/update","0",home});Reject(new string[]{"/update","-1",home});
                Reject(new string[]{"/update","12345","relative"});Reject(new string[]{"/update","12345"});
                Reject(new string[]{"/update",Process.GetCurrentProcess().Id.ToString(),home});
                bool timedOut=false;try{Entry.WaitForParent(Process.GetCurrentProcess().Id,0);}catch(IOException){timedOut=true;}
                if(!timedOut)throw new Exception("Running parent was not protected.");
                Console.WriteLine("Installer handoff validation and wait timeout checks passed. No install, registry, shortcuts, or database changes.");return 0;
            }catch(Exception ex){Console.Error.WriteLine(ex);return 1;}
        }
    }
}

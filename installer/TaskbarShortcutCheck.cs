using System;using System.IO;using System.Reflection;using System.Runtime.InteropServices;
namespace BusinessLedgerDeployment {
    internal static class TaskbarShortcutCheck {
        [STAThread] static int Main(string[] args) {
            object shell=null,link=null,view=null,folder=null,item=null;
            string path=null;
            try {
                if(args.Length!=2||!Path.GetFullPath(args[1]).Contains(Path.DirectorySeparatorChar+"target"+Path.DirectorySeparatorChar))throw new Exception("Use a disposable target folder.");
                Directory.CreateDirectory(args[1]);path=Path.Combine(args[1],"test-ledger.lnk");
                shell=Activator.CreateInstance(Type.GetTypeFromProgID("WScript.Shell"));
                link=shell.GetType().InvokeMember("CreateShortcut",BindingFlags.InvokeMethod,null,shell,new object[]{path});
                link.GetType().InvokeMember("TargetPath",BindingFlags.SetProperty,null,link,new object[]{Path.Combine(Environment.SystemDirectory,"notepad.exe")});
                link.GetType().InvokeMember("Save",BindingFlags.InvokeMethod,null,link,null);
                Deployment.ApplyShortcutIdentity(Path.GetFullPath(args[0]),path);
                view=Activator.CreateInstance(Type.GetTypeFromProgID("Shell.Application"));
                folder=view.GetType().InvokeMember("NameSpace",BindingFlags.InvokeMethod,null,view,new object[]{Path.GetDirectoryName(path)});
                item=folder.GetType().InvokeMember("ParseName",BindingFlags.InvokeMethod,null,folder,new object[]{Path.GetFileName(path)});
                string identity=(string)item.GetType().InvokeMember("ExtendedProperty",BindingFlags.InvokeMethod,null,item,new object[]{"System.AppUserModel.ID"});
                if(identity!="BusinessLedger.Desktop")throw new Exception("Shortcut identity did not match the running app.");
                Console.WriteLine("Disposable Windows shortcut identity readback passed; installed shortcuts, registry, pins, and ledgers were not changed.");return 0;
            }catch(Exception ex){Console.Error.WriteLine(ex);return 1;}
            finally{
                foreach(object value in new object[]{item,folder,view,link,shell})if(value!=null)Marshal.FinalReleaseComObject(value);
                if(path!=null&&File.Exists(path))File.Delete(path);
            }
        }
    }
}

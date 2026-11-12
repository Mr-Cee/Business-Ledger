using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;
using System.Collections.Generic;

namespace BusinessLedgerDeployment {
    internal sealed class InstallGate : IDisposable {
        private readonly Mutex mutex;internal InstallGate(Mutex value){mutex=value;}
        public void Dispose(){mutex.ReleaseMutex();mutex.Dispose();}
    }
    internal static class Deployment {
        internal const string RegistryPath = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\BusinessLedger";
        internal static string Root { get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","BusinessLedger"); } }
        internal static string UserData { get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"BusinessLedger"); } }
        internal static string Launcher { get { return Path.Combine(Root,"BusinessLedgerLauncher.exe"); } }
        internal static string Pointer { get { return Path.Combine(Root,"current.txt"); } }
        internal static string DataPointer { get { return Path.Combine(UserData,"data-location.txt"); } }
        internal static string Full(string path) { return Path.GetFullPath(path).TrimEnd(Path.DirectorySeparatorChar,Path.AltDirectorySeparatorChar); }
        internal static bool Within(string root,string child) { string a=Full(root), b=Full(child); return b.Equals(a,StringComparison.OrdinalIgnoreCase)||b.StartsWith(a+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase); }
        internal static void AtomicText(string path,string text) {
            Directory.CreateDirectory(Path.GetDirectoryName(path)); string temp=path+"."+Guid.NewGuid().ToString("N")+".tmp";
            File.WriteAllText(temp,text,new UTF8Encoding(false));
            if(File.Exists(path))File.Replace(temp,path,null);else File.Move(temp,path);
        }
        internal static InstallGate Acquire() {
            Mutex mutex=new Mutex(false,@"Local\BusinessLedgerInstallation"); bool held=false;
            try { held=mutex.WaitOne(0); } catch(AbandonedMutexException) { held=true; }
            if(!held){mutex.Dispose();throw new InvalidOperationException("An installation or uninstall is already running. Try again when it finishes.");} return new InstallGate(mutex);
        }
        internal static void EnsureClosed() {
            foreach(string name in new string[]{"BusinessLedger","BusinessLedgerHost"})foreach(Process process in Process.GetProcessesByName(name))using(process) { if(!process.HasExited)throw new InvalidOperationException("Close Business Ledger before installing or uninstalling. Your ledger data will be kept."); }
        }
        internal static string ExistingData() {
            if(File.Exists(DataPointer))return File.ReadAllText(DataPointer).Trim();
            if(Directory.Exists(Path.Combine(ReleaseInfo.LegacyHome,".local","postgres-data")))return ReleaseInfo.LegacyHome;
            return Path.Combine(UserData,"Data");
        }
        internal static void ValidateData(string path) {
            if(String.IsNullOrWhiteSpace(path)||!Path.IsPathRooted(path))throw new InvalidOperationException("Choose an absolute data-folder path.");
            if(Within(Root,path))throw new InvalidOperationException("Choose a data folder outside the application installation folder.");
        }
        internal static string CurrentExecutable() {
            if(!File.Exists(Pointer))throw new InvalidOperationException("Business Ledger is not installed completely. Run setup again.");
            string release=File.ReadAllText(Pointer).Trim();
            if(!Regex.IsMatch(release,@"^\d+\.\d+\.\d+-[a-f0-9]{32}$"))throw new InvalidOperationException("The installed release pointer is invalid. Run setup again.");
            string exe=Path.Combine(Root,"versions",release,"BusinessLedger.exe");
            if(!File.Exists(exe))throw new InvalidOperationException("The installed application is missing. Run setup again.");return exe;
        }
        internal static string StartMenuLink { get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs),"Business Ledger","Business Ledger.lnk"); } }
        internal static string DesktopLink { get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),"Business Ledger.lnk"); } }
        private static object Shell() { Type type=Type.GetTypeFromProgID("WScript.Shell");if(type==null)throw new InvalidOperationException("Windows shortcuts are unavailable.");return Activator.CreateInstance(type); }
        internal static void Shortcut(string path) {
            Directory.CreateDirectory(Path.GetDirectoryName(path));object shell=Shell(),link=null;
            try {
                link=shell.GetType().InvokeMember("CreateShortcut",BindingFlags.InvokeMethod,null,shell,new object[]{path});
                Type type=link.GetType();type.InvokeMember("TargetPath",BindingFlags.SetProperty,null,link,new object[]{Launcher});
                type.InvokeMember("WorkingDirectory",BindingFlags.SetProperty,null,link,new object[]{Root});
                type.InvokeMember("Description",BindingFlags.SetProperty,null,link,new object[]{"Business Ledger"});
                type.InvokeMember("IconLocation",BindingFlags.SetProperty,null,link,new object[]{CurrentExecutable()+",0"});
                type.InvokeMember("Save",BindingFlags.InvokeMethod,null,link,null);
            } finally { if(link!=null)Marshal.FinalReleaseComObject(link);Marshal.FinalReleaseComObject(shell); }
        }
        internal static void RemoveShortcut(string path) {
            if(!File.Exists(path))return;object shell=Shell(),link=null;
            try {
                link=shell.GetType().InvokeMember("CreateShortcut",BindingFlags.InvokeMethod,null,shell,new object[]{path});
                string target=(string)link.GetType().InvokeMember("TargetPath",BindingFlags.GetProperty,null,link,null);
                if(Full(target).Equals(Full(Launcher),StringComparison.OrdinalIgnoreCase))File.Delete(path);
            }finally{if(link!=null)Marshal.FinalReleaseComObject(link);Marshal.FinalReleaseComObject(shell);}
        }
        internal static void Register() {
            using(RegistryKey key=Registry.CurrentUser.CreateSubKey(RegistryPath)) {
                key.SetValue("DisplayName","Business Ledger");key.SetValue("DisplayVersion",ReleaseInfo.Version);key.SetValue("Publisher","Business Ledger");
                key.SetValue("InstallLocation",Root);key.SetValue("DisplayIcon",CurrentExecutable()+",0");
                key.SetValue("UninstallString","\""+Path.Combine(Root,"Uninstall.exe")+"\"");key.SetValue("NoModify",1);key.SetValue("NoRepair",1);
            }
        }
        internal static void CheckVersion() {
            using(RegistryKey key=Registry.CurrentUser.OpenSubKey(RegistryPath)) {
                string value=key==null?null:key.GetValue("DisplayVersion") as string;Version installed;
                if(value!=null&&Version.TryParse(value,out installed)&&installed>new Version(ReleaseInfo.Version))throw new InvalidOperationException("A newer Business Ledger version is installed. Downgrades are blocked to protect compatibility.");
            }
        }
        [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,int pid);
        [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool QueryFullProcessImageName(IntPtr process,int flags,StringBuilder name,ref int size);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        private static string ProcessPath(int pid) {
            IntPtr handle=OpenProcess(0x1000,false,pid);if(handle==IntPtr.Zero){if(Marshal.GetLastWin32Error()==87)return null;throw new IOException("Cannot verify a database process before stopping it.");}
            try{StringBuilder path=new StringBuilder(32768);int size=path.Capacity;if(!QueryFullProcessImageName(handle,0,path,ref size))throw new IOException("Cannot verify the database executable path.");return path.ToString();}finally{CloseHandle(handle);}
        }
        internal static void StopOwnedDatabases(string applicationRoot,string locationsFile) {
            if(!File.Exists(locationsFile))return;
            foreach(string home in File.ReadAllLines(locationsFile)) {
                if(String.IsNullOrWhiteSpace(home)||!Path.IsPathRooted(home))continue;
                string data=Path.Combine(home,".local","postgres-data"),pidFile=Path.Combine(data,"postmaster.pid");if(!File.Exists(pidFile))continue;
                string[] lines=File.ReadAllLines(pidFile);int pid;if(lines.Length==0||!Int32.TryParse(lines[0],out pid)||pid<=0)continue;
                string program;try{program=ProcessPath(pid);}catch(ArgumentException){continue;}
                if(program==null||!Within(applicationRoot,program)||!Path.GetFileName(program).Equals("postgres.exe",StringComparison.OrdinalIgnoreCase))continue;
                string control=Path.Combine(Path.GetDirectoryName(program),"pg_ctl.exe");if(!File.Exists(control))throw new IOException("Cannot stop the bundled database safely. Its control program is missing.");
                using(Process stop=Process.Start(new ProcessStartInfo(control,"stop -D \""+data+"\" -m fast -w -t 45"){UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true})){
                    if(!stop.WaitForExit(60000))throw new IOException("The bundled database did not stop in time. App files were retained.");
                    string detail=stop.StandardOutput.ReadToEnd()+stop.StandardError.ReadToEnd();if(stop.ExitCode!=0)throw new IOException("Could not stop the bundled database: "+detail);
                }
            }
        }
        internal static void NoReparseParents(string root,string path) {
            if(!Within(root,path))throw new IOException("Path leaves the application folder.");
            string current=Full(path),stop=Full(root);
            while(true) {
                if((File.Exists(current)||Directory.Exists(current))&&(File.GetAttributes(current)&FileAttributes.ReparsePoint)!=0)throw new IOException("Linked application folders are not supported.");
                if(current.Equals(stop,StringComparison.OrdinalIgnoreCase))break;current=Path.GetDirectoryName(current);
            }
        }
        internal static string SafeEntry(string root,string name) {
            if(Path.IsPathRooted(name)||name.IndexOf(':')>=0)throw new IOException("Invalid package path.");
            string target=Path.GetFullPath(Path.Combine(root,name.Replace('/',Path.DirectorySeparatorChar)));
            if(!Within(root,target)||Full(target).Equals(Full(root),StringComparison.OrdinalIgnoreCase))throw new IOException("Package path leaves the application folder.");return target;
        }
        internal static void DeleteEmpty(string path) { if(Directory.Exists(path)&&Directory.GetFileSystemEntries(path).Length==0)Directory.Delete(path); }
    }

    internal static class Entry {
        [STAThread] static int Main(string[] args) {
            Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);
            try {
#if SETUP
                if(args.Length==2&&args[0]=="/verify") { Verify(args[1]);return 0; }
                if(args.Length>0&&args[0]=="/update") {
                    int parent;string home;UpdateArguments(args,out parent,out home);
                    WaitForParent(parent,60000);
                    // The version-stamped bridge may take a moment to exit after its Java host.
                    for(int attempt=0;;attempt++) {
                        try{Deployment.EnsureClosed();break;}
                        catch(InvalidOperationException){if(attempt>=40)throw;Thread.Sleep(250);}
                    }
                    UpdateForm update=new UpdateForm(home);Application.Run(update);
                    if(update.Failure!=null)throw new IOException("The update could not be installed. "+update.Failure.Message,update.Failure);
                    Process.Start(new ProcessStartInfo(Deployment.Launcher){UseShellExecute=true});return 0;
                }
                Application.Run(new SetupForm());
#elif LAUNCHER
                using(InstallGate gate=Deployment.Acquire()) {
                    string exe=Deployment.CurrentExecutable();Process.Start(new ProcessStartInfo(exe){WorkingDirectory=Path.GetDirectoryName(exe),UseShellExecute=true});
                }
#elif UNINSTALL
                if(args.Length==2&&args[0]=="/remove") {
                    int parent;if(!Int32.TryParse(args[1],out parent))throw new InvalidOperationException("Invalid uninstall request.");
                    try{using(Process process=Process.GetProcessById(parent)){if(!process.WaitForExit(15000))throw new IOException("The uninstall launcher did not close.");}}catch(ArgumentException){}
                    using(InstallGate gate=Deployment.Acquire()){Deployment.EnsureClosed();Uninstall();}
                    MessageBox.Show("Business Ledger was removed. Ledger data and backups were retained.\n\n"+Deployment.ExistingData(),"Business Ledger",MessageBoxButtons.OK,MessageBoxIcon.Information);
                }else{
                    using(InstallGate gate=Deployment.Acquire()){Deployment.EnsureClosed();}
                    if(MessageBox.Show("Uninstall Business Ledger? Ledger data and backups will be retained.","Uninstall Business Ledger",MessageBoxButtons.OKCancel,MessageBoxIcon.Question)!=DialogResult.OK)return 0;
                    string helper=Path.Combine(Path.GetTempPath(),"BusinessLedger-Uninstall-"+Guid.NewGuid().ToString("N")+".exe");File.Copy(Assembly.GetExecutingAssembly().Location,helper);
                    Process.Start(new ProcessStartInfo(helper,"/remove "+Process.GetCurrentProcess().Id){UseShellExecute=false,CreateNoWindow=true});
                }
#endif
                return 0;
            }catch(Exception ex){
#if SETUP
                if(args.Length==2&&args[0]=="/verify"){File.WriteAllText(args[1],"FAILED: "+ex);return 1;}
#endif
                MessageBox.Show(ex.Message,"Business Ledger",MessageBoxButtons.OK,MessageBoxIcon.Error);return 1;
            }
        }
#if SETUP
        internal static void UpdateArguments(string[] args,out int parent,out string home) {
            parent=0;home=null;
            if(args.Length!=3||args[0]!="/update"||!Int32.TryParse(args[1],out parent)||parent<=0||parent==Process.GetCurrentProcess().Id)
                throw new InvalidOperationException("Invalid update handoff.");
            Deployment.ValidateData(args[2]);home=Deployment.Full(args[2]);
        }
        internal static void WaitForParent(int parent,int timeout) {
            try{using(Process process=Process.GetProcessById(parent)){if(!process.WaitForExit(timeout))throw new IOException("Ledger did not close. The update was not installed.");}}
            catch(ArgumentException){} // The original process has already exited.
        }
        internal static void InstallFiles(string dataPath) {
            using(InstallGate gate=Deployment.Acquire()) {
                Deployment.EnsureClosed();Deployment.CheckVersion();Deployment.ValidateData(dataPath);
                Deployment.StopOwnedDatabases(Deployment.Root,Path.Combine(Deployment.UserData,"database-locations.txt"));
                string release=ReleaseInfo.Version+"-"+Guid.NewGuid().ToString("N"),destination=Path.Combine(Deployment.Root,"versions",release);
                Directory.CreateDirectory(Deployment.Root);Deployment.NoReparseParents(Deployment.Root,Deployment.Root);Extract(destination);
                string oldRelease=File.Exists(Deployment.Pointer)?File.ReadAllText(Deployment.Pointer):null,oldData=File.Exists(Deployment.DataPointer)?File.ReadAllText(Deployment.DataPointer):null;
                try {
                    ResourceFile("launcher.exe",Deployment.Launcher);ResourceFile("uninstaller.exe",Path.Combine(Deployment.Root,"Uninstall.exe"));
                    Directory.CreateDirectory(Deployment.Full(dataPath));Deployment.AtomicText(Deployment.DataPointer,Deployment.Full(dataPath));Deployment.AtomicText(Deployment.Pointer,release);
                    Deployment.Shortcut(Deployment.StartMenuLink);Deployment.Shortcut(Deployment.DesktopLink);Deployment.Register();
                }catch {
                    if(oldRelease!=null)Deployment.AtomicText(Deployment.Pointer,oldRelease);else if(File.Exists(Deployment.Pointer))File.Delete(Deployment.Pointer);
                    if(oldData!=null)Deployment.AtomicText(Deployment.DataPointer,oldData);else if(File.Exists(Deployment.DataPointer))File.Delete(Deployment.DataPointer);throw;
                }
            }
        }
        internal static void Extract(string destination) {
            Directory.CreateDirectory(destination);Deployment.NoReparseParents(destination,destination);
            using(Stream resource=Assembly.GetExecutingAssembly().GetManifestResourceStream("payload.zip")) {
                if(resource==null)throw new IOException("The installer payload is missing.");
                string hash;using(SHA256 sha=SHA256.Create()){hash=BitConverter.ToString(sha.ComputeHash(resource)).Replace("-","").ToLowerInvariant();}
                if(hash!=ReleaseInfo.PayloadHash)throw new IOException("Installer payload checksum failed.");resource.Position=0;
                List<string> files=new List<string>();
                using(ZipArchive zip=new ZipArchive(resource,ZipArchiveMode.Read,false))foreach(ZipArchiveEntry entry in zip.Entries) {
                    string target=Deployment.SafeEntry(destination,entry.FullName);
                    if(entry.FullName.EndsWith("/")){Directory.CreateDirectory(target);continue;}
                    Directory.CreateDirectory(Path.GetDirectoryName(target));Deployment.NoReparseParents(destination,target);
                    using(Stream input=entry.Open())using(Stream output=new FileStream(target,FileMode.CreateNew,FileAccess.Write)){input.CopyTo(output);}files.Add(entry.FullName);
                }
                File.WriteAllLines(Path.Combine(destination,".installed-files.txt"),files,new UTF8Encoding(false));
            }
            if(!File.Exists(Path.Combine(destination,"BusinessLedger.exe"))||!Directory.Exists(Path.Combine(destination,"runtime"))||!File.Exists(Path.Combine(destination,"postgres","bin","pg_ctl.exe"))||!File.Exists(Path.Combine(destination,"postgres","share","postgres.bki")))throw new IOException("The application payload is incomplete.");
        }
        static void Verify(string report) {
            string root=Path.Combine(Path.GetDirectoryName(Path.GetFullPath(report)),"verified-payload-"+Guid.NewGuid().ToString("N"));Extract(root);
            bool blocked=false;try{Deployment.SafeEntry(root,"../outside.txt");}catch(IOException){blocked=true;}if(!blocked)throw new IOException("Path validation failed.");
            if(!(new Version("1.0.1")>new Version("1.0.0")))throw new IOException("Version comparison failed.");
            string config=File.ReadAllText(Path.Combine(root,"app","BusinessLedger.cfg"));if(config.Contains("-Dledger.home="))throw new IOException("Installed payload has a fixed data folder.");
            foreach(string relative in new string[]{"vcruntime140.dll","msvcp140.dll",@"postgres\bin\vcruntime140.dll"})if(new FileInfo(Path.Combine(root,relative)).Length<1024)throw new IOException("A required native runtime file is missing or invalid.");
            File.WriteAllText(report,"Verified installer payload SHA-256, safe extraction, version "+ReleaseInfo.Version+", included Java/PostgreSQL/C++ runtimes, and independent data location.\nExtracted test payload: "+root);
        }
        internal static void ResourceFile(string name,string target) {
            string temporary=target+"."+Guid.NewGuid().ToString("N")+".tmp";
            using(Stream input=Assembly.GetExecutingAssembly().GetManifestResourceStream(name))using(Stream output=File.Create(temporary)){if(input==null)throw new IOException("Missing installer component.");input.CopyTo(output);}
            if(File.Exists(target))File.Replace(temporary,target,null);else File.Move(temporary,target);
        }
#endif
#if UNINSTALL
        static void Uninstall() {
            Deployment.ValidateData(Deployment.ExistingData());Deployment.NoReparseParents(Deployment.Root,Deployment.Root);
            Deployment.StopOwnedDatabases(Deployment.Root,Path.Combine(Deployment.UserData,"database-locations.txt"));
            string versions=Path.Combine(Deployment.Root,"versions");
            if(Directory.Exists(versions))foreach(string version in Directory.GetDirectories(versions)) {
                if(!Regex.IsMatch(Path.GetFileName(version),@"^\d+\.\d+\.\d+-[a-f0-9]{32}$"))continue;
                Deployment.NoReparseParents(Deployment.Root,version);string manifest=Path.Combine(version,".installed-files.txt");if(!File.Exists(manifest))continue;
                HashSet<string> folders=new HashSet<string>();
                foreach(string name in File.ReadAllLines(manifest)) {
                    string target=Deployment.SafeEntry(version,name);Deployment.NoReparseParents(version,target);if(File.Exists(target))File.Delete(target);
                    string folder=Path.GetDirectoryName(target);while(Deployment.Within(version,folder)&&!folder.Equals(version,StringComparison.OrdinalIgnoreCase)){folders.Add(folder);folder=Path.GetDirectoryName(folder);}
                }
                List<string> ordered=new List<string>(folders);ordered.Sort((a,b)=>b.Length.CompareTo(a.Length));foreach(string folder in ordered)Deployment.DeleteEmpty(folder);
                File.Delete(manifest);Deployment.DeleteEmpty(version);
            }
            Deployment.RemoveShortcut(Deployment.StartMenuLink);Deployment.RemoveShortcut(Deployment.DesktopLink);Deployment.DeleteEmpty(Path.GetDirectoryName(Deployment.StartMenuLink));
            foreach(string name in new string[]{"current.txt","BusinessLedgerLauncher.exe","Uninstall.exe"}){string target=Path.Combine(Deployment.Root,name);if(File.Exists(target))File.Delete(target);}
            Registry.CurrentUser.DeleteSubKeyTree(Deployment.RegistryPath,false);Deployment.DeleteEmpty(versions);Deployment.DeleteEmpty(Deployment.Root);
        }
#endif
    }
#if SETUP
    internal sealed class UpdateForm : Form {
        internal Exception Failure;
        bool installing=true;
        internal UpdateForm(string home) {
            Icon=System.Drawing.Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location);
            Text="Updating Business Ledger";Width=480;Height=150;StartPosition=FormStartPosition.CenterScreen;ControlBox=false;FormBorderStyle=FormBorderStyle.FixedDialog;
            Controls.Add(new Label(){Text="Installing Business Ledger "+ReleaseInfo.Version+". The app will reopen when finished.",Dock=DockStyle.Top,Height=50,Padding=new Padding(12)});
            Controls.Add(new ProgressBar(){Dock=DockStyle.Bottom,Height=24,Style=ProgressBarStyle.Marquee});
            Shown+=(s,e)=>{
                Thread worker=new Thread(()=>{
                    try{Entry.InstallFiles(home);}catch(Exception ex){Failure=ex;}
                    BeginInvoke((Action)(()=>{installing=false;Close();}));
                });
                // Shortcut creation uses COM; match the standard installer's STA context.
                worker.SetApartmentState(ApartmentState.STA);worker.IsBackground=true;worker.Start();
            };
            FormClosing+=(s,e)=>{if(installing)e.Cancel=true;};
        }
    }
    internal sealed class SetupForm : Form {
        readonly TextBox data=new TextBox();readonly CheckBox launch=new CheckBox();readonly Button install=new Button();readonly Label status=new Label();
        internal SetupForm() {
            Icon=System.Drawing.Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location);
            Text="Business Ledger "+ReleaseInfo.Version+" Setup";Width=660;Height=440;StartPosition=FormStartPosition.CenterScreen;Font=new System.Drawing.Font("Segoe UI",10);FormBorderStyle=FormBorderStyle.FixedDialog;MaximizeBox=false;
            Label title=new Label(){Text="Install or update Business Ledger "+ReleaseInfo.Version,Left=20,Top=20,Width=580,Height=30,Font=new System.Drawing.Font("Segoe UI",15,System.Drawing.FontStyle.Bold)};Controls.Add(title);
            Controls.Add(new Label(){Text="All-in-one setup: Java and PostgreSQL are included. No extra downloads.\nLedger data is kept outside the app and preserved during updates.",Left=20,Top=62,Width=600,Height=52});
            Controls.Add(new Label(){Text="Application location (executable and shortcuts):",Left=20,Top=118,Width=600});
            Controls.Add(new TextBox(){Text=Deployment.Root,ReadOnly=true,Left=20,Top=144,Width=600});
            Controls.Add(new Label(){Text="Ledger data location (databases and backups, not the executable):",Left=20,Top=184,Width=600});
            data.SetBounds(20,211,495,28);data.Text=Deployment.ExistingData();Controls.Add(data);
            Button browse=new Button(){Text="Browse...",Left=525,Top=209,Width=95,Height=30};browse.Click+=(s,e)=>{using(FolderBrowserDialog dialog=new FolderBrowserDialog()){dialog.Description="Select the ledger home folder containing .local, or choose a new empty data folder.";if(Directory.Exists(data.Text))dialog.SelectedPath=data.Text;if(dialog.ShowDialog(this)==DialogResult.OK)data.Text=dialog.SelectedPath;}};Controls.Add(browse);
            Controls.Add(new Label(){Text="Choose a new data folder for an empty ledger, then create a login and restore your backup.",Left=20,Top=248,Width=600,Height=36});
            launch.SetBounds(20,290,400,25);launch.Text="Open Business Ledger after installation";launch.Checked=true;Controls.Add(launch);
            status.SetBounds(20,322,600,28);status.Text="Start the app from its desktop or Start menu shortcut.";Controls.Add(status);
            install.SetBounds(450,363,170,36);install.Text="Install / Update";install.Click+=Install;Controls.Add(install);
            Button cancel=new Button(){Text="Cancel",Left=340,Top=363,Width=100,Height=36};cancel.Click+=(s,e)=>Close();Controls.Add(cancel);CancelButton=cancel;
        }
        void Install(object sender,EventArgs e) {
            try {
                install.Enabled=false;status.Text="Installing application files...";status.Refresh();UseWaitCursor=true;
                Entry.InstallFiles(data.Text);
                UseWaitCursor=false;MessageBox.Show(this,"Business Ledger "+ReleaseInfo.Version+" is installed. Your ledger data was preserved.","Setup complete",MessageBoxButtons.OK,MessageBoxIcon.Information);
                if(launch.Checked)Process.Start(new ProcessStartInfo(Deployment.Launcher){UseShellExecute=true});Close();
            }catch(Exception ex){UseWaitCursor=false;install.Enabled=true;status.Text="Setup did not complete.";MessageBox.Show(this,ex.Message,"Business Ledger Setup",MessageBoxButtons.OK,MessageBoxIcon.Error);}
        }
    }
#endif
}

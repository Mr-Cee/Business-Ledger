using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

namespace BusinessLedgerDeployment {
    // Version-stamped bridge for environments that cannot edit native jpackage resources.
    internal static class AppLauncher {
        [STAThread] static int Main() {
            try {
                string root=Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
                string host=Path.Combine(root,"BusinessLedgerHost.exe");
                if(!File.Exists(host))throw new IOException("Application launcher files are missing. Run setup again.");
                using(Process process=Process.Start(new ProcessStartInfo(host){WorkingDirectory=root,UseShellExecute=false,CreateNoWindow=true})){
                    process.WaitForExit();return process.ExitCode;
                }
            }catch(Exception ex){MessageBox.Show(ex.Message,"Business Ledger",MessageBoxButtons.OK,MessageBoxIcon.Error);return 1;}
        }
    }
}

using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace BusinessLedgerDeployment {
    internal static class SecretsHelper {
        static int Main(string[] args){
            byte[] input=null,output=null;
            try{
                if(args.Length!=1||(args[0]!="protect"&&args[0]!="unprotect"))throw new ArgumentException("Invalid credential operation.");
                using(StreamReader reader=new StreamReader(Console.OpenStandardInput(),Encoding.ASCII))input=Convert.FromBase64String(reader.ReadToEnd());
                output=args[0]=="protect"?ProtectedData.Protect(input,null,DataProtectionScope.CurrentUser):ProtectedData.Unprotect(input,null,DataProtectionScope.CurrentUser);
                using(StreamWriter writer=new StreamWriter(Console.OpenStandardOutput(),new UTF8Encoding(false))){writer.Write(Convert.ToBase64String(output));writer.Flush();}
                return 0;
            }catch(Exception ex){Console.Error.Write(ex.Message);return 1;}
            finally{if(input!=null)Array.Clear(input,0,input.Length);if(output!=null)Array.Clear(output,0,output.Length);}
        }
    }
}

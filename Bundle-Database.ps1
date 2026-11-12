param([Parameter(Mandatory=$true)][string]$Image,[string]$PostgresRoot='C:\Program Files\PostgreSQL\17')
$ErrorActionPreference='Stop'
if(!(Test-Path -LiteralPath (Join-Path $PostgresRoot 'bin\postgres.exe'))){throw 'PostgreSQL 17 build-time binaries are required.'}
$postgres=Join-Path $Image 'postgres'
New-Item -ItemType Directory -Force -Path $postgres | Out-Null
foreach($part in @('bin','lib','share')){Copy-Item -LiteralPath (Join-Path $PostgresRoot $part) -Destination $postgres -Recurse}
$licenses=Join-Path $postgres 'licenses'
New-Item -ItemType Directory -Force -Path $licenses | Out-Null
foreach($name in @('server_license.txt','commandlinetools_3rd_party_licenses.txt')){Copy-Item -LiteralPath (Join-Path $PostgresRoot $name) -Destination $licenses}
$redist=Join-Path $PostgresRoot 'installer\vcredist_x64.exe'
Copy-Item -LiteralPath $redist -Destination (Join-Path $licenses 'Microsoft-VC-Redistributable-x64.exe')
$staging=Join-Path $PSScriptRoot ('target\crt-extract-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $staging | Out-Null
if(-not ('LedgerCabinetReader' -as [type])){
 Add-Type -TypeDefinition @'
using System;using System.IO;using System.Collections.Generic;
public static class LedgerCabinetReader {
 public static string[] ExtractContainers(string source,string directory){
  byte[] data=File.ReadAllBytes(source);var paths=new List<string>();
  for(int i=0;i<data.Length-36;i++){
   if(data[i]!=77||data[i+1]!=83||data[i+2]!=67||data[i+3]!=70||BitConverter.ToUInt32(data,i+4)!=0)continue;
   uint size=BitConverter.ToUInt32(data,i+8),files=BitConverter.ToUInt32(data,i+16);
   if(size<36||size>data.Length-i||files<36||files>=size||data[i+25]!=1||data[i+24]!=3)continue;
   string path=Path.Combine(directory,"container-"+paths.Count+".cab");using(var output=File.Create(path))output.Write(data,i,(int)size);paths.Add(path);
  }return paths.ToArray();
 }
 public static bool IsNative64(string path){
  using(var stream=File.OpenRead(path))using(var reader=new BinaryReader(stream)){
   if(stream.Length<128||reader.ReadUInt16()!=0x5a4d)return false;stream.Position=0x3c;int offset=reader.ReadInt32();if(offset<0||offset>stream.Length-6)return false;
   stream.Position=offset;return reader.ReadUInt32()==0x4550&&reader.ReadUInt16()==0x8664;
  }
 }
 public static bool HasRuntimeFiles(string path){
  byte[] data=File.ReadAllBytes(path);if(data.Length<36)return false;int position=(int)BitConverter.ToUInt32(data,16),count=BitConverter.ToUInt16(data,28);
  for(int i=0;i<count;i++){
   position+=16;if(position>=data.Length)return false;int start=position;while(position<data.Length&&data[position]!=0)position++;if(position==data.Length)return false;
   string name=System.Text.Encoding.UTF8.GetString(data,start,position-start);position++;
   if(System.Text.RegularExpressions.Regex.IsMatch(name,@"^(?:[au]\d+|0)$|(?:msvcp140|vcruntime140|concrt140).*\.dll",System.Text.RegularExpressions.RegexOptions.IgnoreCase))return true;
  }return false;
 }
}
'@
}
$queue=[Collections.Generic.Queue[string]]::new()
foreach($cab in [LedgerCabinetReader]::ExtractContainers($redist,$staging)){$queue.Enqueue($cab)}
$seen=[Collections.Generic.HashSet[string]]::new()
$sequence=0
while($queue.Count -gt 0){
 $cab=$queue.Dequeue();$hash=(Get-FileHash -LiteralPath $cab -Algorithm SHA256).Hash
 if(!$seen.Add($hash)){continue}
 if(![LedgerCabinetReader]::HasRuntimeFiles($cab)){continue}
 $output=Join-Path $staging ('expanded-'+$sequence);$sequence++
 New-Item -ItemType Directory -Path $output | Out-Null
 & "$env:WINDIR\System32\expand.exe" '-F:*' $cab $output | Out-Null
 if($LASTEXITCODE -ne 0){continue} # Some embedded OS-update cabinets are not needed for app-local CRT deployment.
 foreach($file in Get-ChildItem -LiteralPath $output -File -Recurse){
  $stream=[IO.File]::OpenRead($file.FullName)
  try{$magic=New-Object byte[] 4;[void]$stream.Read($magic,0,4)}finally{$stream.Dispose()}
  if([Text.Encoding]::ASCII.GetString($magic) -eq 'MSCF'){$queue.Enqueue($file.FullName)}
 }
}
$crt=Join-Path $staging 'runtime'
New-Item -ItemType Directory -Path $crt | Out-Null
foreach($file in Get-ChildItem -LiteralPath $staging -File -Recurse){
 if($file.FullName.StartsWith($crt,[StringComparison]::OrdinalIgnoreCase)){continue}
 if($file.Name -match '^(?<dll>(?:msvcp140.*|vcruntime140.*|concrt140)\.dll)(?:_amd64)?$' -and [LedgerCabinetReader]::IsNative64($file.FullName)){
  $original=$Matches['dll'];Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $crt $original.ToLowerInvariant()) -Force
 }
}
foreach($required in @('vcruntime140.dll','vcruntime140_1.dll','msvcp140.dll')){
 if(!(Test-Path -LiteralPath (Join-Path $crt $required))){throw "Redistributable extraction did not produce $required."}
}
foreach($destination in @($Image,(Join-Path $Image 'runtime\bin'),(Join-Path $postgres 'bin'))){Copy-Item -Path (Join-Path $crt '*.dll') -Destination $destination -Force}
$versionOutput=(& (Join-Path $postgres 'bin\postgres.exe') --version | Out-String).Trim()
if($LASTEXITCODE -ne 0 -or $versionOutput -notmatch 'PostgreSQL\) 17\.') {throw 'Bundled PostgreSQL version verification failed.'}
[IO.File]::WriteAllText((Join-Path $postgres 'bundle.properties'),"major=17`nversion=$versionOutput`n",[Text.UTF8Encoding]::new($false))
$inventory=Get-ChildItem -LiteralPath $crt -File | ForEach-Object {"$($_.Name) $($_.VersionInfo.FileVersion) $((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash)"}
[IO.File]::WriteAllLines((Join-Path $licenses 'Microsoft-runtime-files.txt'),$inventory,[Text.UTF8Encoding]::new($false))
Write-Host "Bundled $versionOutput and $(@($inventory).Count) app-local Microsoft runtime DLLs."

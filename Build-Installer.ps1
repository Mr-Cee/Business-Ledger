param([string]$Destination = (Join-Path $PSScriptRoot 'releases'),[switch]$UseExistingRuntime,[switch]$Rebuild)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. (Join-Path $PSScriptRoot 'Release.Common.ps1')
$version = Get-LedgerVersion
if ((Test-Path -LiteralPath (Join-Path $Destination "BusinessLedger-Setup-$version.exe")) -and !$Rebuild) { throw 'This release already exists. Increment pom.xml for a new release, or use -Rebuild only before distribution.' }
$build = Join-Path $PSScriptRoot "target\installer\$version-$([Guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Force -Path $build,$Destination | Out-Null
& (Join-Path $PSScriptRoot 'Build-Executable.ps1') -Layout Installed -Destination (Join-Path $build 'image') -UseExistingRuntime:$UseExistingRuntime
$python = Get-Command python.exe -ErrorAction SilentlyContinue
if(!$python){throw 'Python 3 is required on the build PC for the native-dependency audit; it is not required by end users.'}
& $python.Source (Join-Path $PSScriptRoot 'installer\audit-native.py') (Join-Path $build 'image\BusinessLedger')
if($LASTEXITCODE -ne 0){throw 'Native dependency audit failed.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$payload = Join-Path $build 'payload.zip'
[IO.Compression.ZipFile]::CreateFromDirectory((Join-Path $build 'image\BusinessLedger'),$payload,[IO.Compression.CompressionLevel]::Optimal,$false)
$hash = (Get-FileHash -LiteralPath $payload -Algorithm SHA256).Hash.ToLowerInvariant()
$legacy = $PSScriptRoot.Replace('"','""')
$metadata = Join-Path $build 'ReleaseInfo.cs'
$source = @"
using System.Reflection;
[assembly: AssemblyTitle("Business Ledger")]
[assembly: AssemblyProduct("Business Ledger")]
[assembly: AssemblyCompany("Business Ledger")]
[assembly: AssemblyVersion("$version.0")]
[assembly: AssemblyFileVersion("$version.0")]
namespace BusinessLedgerDeployment { internal static class ReleaseInfo {
internal const string Version = "$version";
internal const string LegacyHome = @"$legacy";
internal const string PayloadHash = "$hash";
} }
"@
[IO.File]::WriteAllText($metadata,$source,[Text.UTF8Encoding]::new($false))
$framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
$compiler = Join-Path $framework 'csc.exe'
if (!(Test-Path -LiteralPath $compiler)) { throw 'The Windows .NET Framework C# compiler is required to build setup.' }
$references = @('/r:System.Windows.Forms.dll','/r:System.Drawing.dll',"/r:$framework\System.IO.Compression.dll","/r:$framework\System.IO.Compression.FileSystem.dll")
$deployment = Join-Path $PSScriptRoot 'installer\Deployment.cs'
$manifest = Join-Path $PSScriptRoot 'installer\app.manifest'
$launcher = Join-Path $build 'BusinessLedgerLauncher.exe'
$uninstaller = Join-Path $build 'Uninstall.exe'
& $compiler /nologo /target:winexe /platform:x64 /define:LAUNCHER "/win32manifest:$manifest" "/out:$launcher" @references $metadata $deployment
if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
& $compiler /nologo /target:winexe /platform:x64 /define:UNINSTALL "/win32manifest:$manifest" "/out:$uninstaller" @references $metadata $deployment
if ($LASTEXITCODE -ne 0) { throw 'Uninstaller compilation failed.' }
$installer = Join-Path $Destination "BusinessLedger-Setup-$version.exe"
& $compiler /nologo /target:winexe /platform:x64 /define:SETUP "/win32manifest:$manifest" "/out:$installer" "/resource:$payload,payload.zip" "/resource:$launcher,launcher.exe" "/resource:$uninstaller,uninstaller.exe" @references $metadata $deployment
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed.' }
$checksum = (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $Destination "BusinessLedger-Setup-$version.sha256"),"$checksum  BusinessLedger-Setup-$version.exe`n",[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'CHANGELOG.md') -Destination (Join-Path $Destination 'CHANGELOG.md') -Force
Write-Host "Built installable release: $installer"
Write-Host "SHA-256: $checksum"

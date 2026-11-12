param([ValidateSet('Portable','Installed')][string]$Layout = 'Portable',[string]$Destination,[switch]$UseExistingRuntime)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. (Join-Path $PSScriptRoot 'Release.Common.ps1')
$version = Get-LedgerVersion
$tag = "$version-$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss'))-$([Guid]::NewGuid().ToString('N').Substring(0,8))"
if (!$Destination) { $Destination = Join-Path $PSScriptRoot "releases\$tag\$Layout" }
$build = Join-Path $PSScriptRoot "target\releases\$tag"
$classes = Join-Path $build 'classes'
$inputDir = Join-Path $build 'input'
New-Item -ItemType Directory -Force -Path $build,$inputDir,$Destination | Out-Null
if (Test-Path -LiteralPath (Join-Path $Destination 'BusinessLedger')) { throw 'Choose a new destination; existing application files will not be overwritten.' }
$driver = Join-Path $PSScriptRoot 'lib\postgresql-42.7.8.jar'
if (!(Test-Path -LiteralPath $driver)) { throw 'The PostgreSQL JDBC driver is missing. Run Start-Ledger.ps1 first.' }
Compile-Ledger $classes
$jarName = "business-ledger-$version.jar"
& jar "-J-Djava.io.tmpdir=$build" --create --file (Join-Path $inputDir $jarName) --main-class com.businessledger.BusinessLedger -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR build failed.' }
Copy-Item -LiteralPath $driver -Destination $inputDir
if ($UseExistingRuntime) {
    $existing = Join-Path $PSScriptRoot 'dist\BusinessLedger'
    $existingExe = Join-Path $existing 'BusinessLedgerHost.exe'
    if (!(Test-Path -LiteralPath $existingExe)) { $existingExe = Join-Path $existing 'BusinessLedger.exe' }
    if (!(Test-Path -LiteralPath $existingExe) -or !(Test-Path -LiteralPath (Join-Path $existing 'runtime'))) { throw 'A working application image is needed to reuse its runtime.' }
    $image = Join-Path $Destination 'BusinessLedger'
    $app = Join-Path $image 'app'
    New-Item -ItemType Directory -Force -Path $image,$app | Out-Null
    Copy-Item -LiteralPath $existingExe -Destination (Join-Path $image 'BusinessLedgerHost.exe')
    Copy-Item -LiteralPath (Join-Path $existing 'runtime') -Destination $image -Recurse
    Copy-Item -Path (Join-Path $inputDir '*') -Destination $app
    $configText = '[Application]' + "`napp.classpath=`$APPDIR\$jarName`napp.mainclass=com.businessledger.BusinessLedger`napp.classpath=`$APPDIR\postgresql-42.7.8.jar`n`n[JavaOptions]`njava-options=-Djpackage.app-version=$version`n"
    [IO.File]::WriteAllText((Join-Path $app 'BusinessLedger.cfg'),$configText,[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $app 'BusinessLedgerHost.cfg'),$configText,[Text.UTF8Encoding]::new($false))
    $metadata = Join-Path $build 'LauncherVersion.cs'
    [IO.File]::WriteAllText($metadata,"using System.Reflection;[assembly:AssemblyTitle(`"Business Ledger`")][assembly:AssemblyFileVersion(`"$version.0`")][assembly:AssemblyVersion(`"$version.0`")]",[Text.UTF8Encoding]::new($false))
    $compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    & $compiler /nologo /target:winexe /platform:x64 /r:System.Windows.Forms.dll "/win32manifest:$PSScriptRoot\installer\app.manifest" "/out:$image\BusinessLedger.exe" $metadata (Join-Path $PSScriptRoot 'installer\AppLauncher.cs')
    if ($LASTEXITCODE -ne 0) { throw 'Versioned application launcher build failed.' }
} else {
    $nativeDestination = Join-Path ([IO.Path]::GetTempPath()) "BusinessLedger-package-$([Guid]::NewGuid().ToString('N'))"
    New-Item -ItemType Directory -Path $nativeDestination | Out-Null
    & jpackage "-J-Djava.io.tmpdir=$build" --type app-image --name BusinessLedger --app-version $version --vendor 'Business Ledger' --input $inputDir --main-jar $jarName --main-class com.businessledger.BusinessLedger --dest $nativeDestination --add-modules java.desktop,java.sql,java.naming,java.security.jgss,java.management
    if ($LASTEXITCODE -ne 0) { throw 'Application image build failed.' }
    Copy-Item -LiteralPath (Join-Path $nativeDestination 'BusinessLedger') -Destination $Destination -Recurse
}
$image=Join-Path $Destination 'BusinessLedger'
$helperVersion=Join-Path $build 'SecretsVersion.cs'
[IO.File]::WriteAllText($helperVersion,"using System.Reflection;[assembly:AssemblyFileVersion(`"$version.0`")][assembly:AssemblyVersion(`"$version.0`" )]",[Text.UTF8Encoding]::new($false))
$compiler=Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $compiler /nologo /target:winexe /platform:x64 /r:System.Security.dll "/win32manifest:$PSScriptRoot\installer\app.manifest" "/out:$image\LedgerSecrets.exe" $helperVersion (Join-Path $PSScriptRoot 'installer\SecretsHelper.cs')
if($LASTEXITCODE -ne 0){throw 'Credential helper compilation failed.'}
if ($Layout -eq 'Portable') {
    $config = Join-Path $Destination 'BusinessLedger\app\BusinessLedger.cfg'
    [IO.File]::AppendAllText($config,"`njava-options=-Dledger.home=$PSScriptRoot`n",[Text.UTF8Encoding]::new($false))
    $hostConfig=Join-Path $Destination 'BusinessLedger\app\BusinessLedgerHost.cfg'
    if(Test-Path -LiteralPath $hostConfig){[IO.File]::AppendAllText($hostConfig,"`njava-options=-Dledger.home=$PSScriptRoot`n",[Text.UTF8Encoding]::new($false))}
}
& (Join-Path $PSScriptRoot 'Bundle-Database.ps1') -Image (Join-Path $Destination 'BusinessLedger')
Write-Host "Built Business Ledger $version ($Layout): $(Join-Path $Destination 'BusinessLedger\BusinessLedger.exe')"

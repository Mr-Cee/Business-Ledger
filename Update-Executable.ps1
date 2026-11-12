param([string]$AppDirectory = (Join-Path $PSScriptRoot 'dist\BusinessLedger'))
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. (Join-Path $PSScriptRoot 'Release.Common.ps1')
$version = Get-LedgerVersion
$tag = "$version-$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss'))-$([Guid]::NewGuid().ToString('N').Substring(0,8))"
$build = Join-Path $PSScriptRoot "target\updates\$tag"
$classes = Join-Path $build 'classes'
$config = Join-Path $AppDirectory 'app\BusinessLedger.cfg'
if (!(Test-Path -LiteralPath $config)) { throw 'Build an application image first.' }
Compile-Ledger $classes
$jarName = "business-ledger-$tag.jar"
$jarPath = Join-Path $AppDirectory "app\$jarName"
& jar "-J-Djava.io.tmpdir=$build" --create --file $jarPath --main-class com.businessledger.BusinessLedger -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging the update failed.' }
$native=Join-Path $build 'native'
& (Join-Path $PSScriptRoot 'Build-WindowsTaskbar.ps1') -Destination $native
$nativeName="LedgerWindows-$tag.dll"
$iconName="BusinessLedger-$tag.ico"
Copy-Item -LiteralPath (Join-Path $native 'LedgerWindows.dll') -Destination (Join-Path $AppDirectory "app\$nativeName")
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'src/main/resources/icons/business-ledger.ico') -Destination (Join-Path $AppDirectory "app\$iconName")
$configs = @($config)
$hostConfig = Join-Path $AppDirectory 'app\BusinessLedgerHost.cfg'
if(Test-Path -LiteralPath $hostConfig){$configs += $hostConfig}
foreach($config in $configs){
$configText = [IO.File]::ReadAllText($config)
if ($configText -notmatch 'business-ledger(?:-[A-Za-z0-9_.-]+)?\.jar') { throw 'The application classpath was not recognized.' }
$configText = [regex]::Replace($configText,'business-ledger(?:-[A-Za-z0-9_.-]+)?\.jar',$jarName)
$configText = [regex]::Replace($configText,'-Djpackage\.app-version=[^\r\n]+',"-Djpackage.app-version=$version")
$configText = [regex]::Replace($configText,'(?m)^java-options=-Dledger\.windows\.(?:library|icon)=[^\r\n]*\r?\n?','')
if(!$configText.Contains('java-options=--enable-native-access=ALL-UNNAMED')){$configText += "`njava-options=--enable-native-access=ALL-UNNAMED`n"}
$configText += "`njava-options=-Dledger.windows.library=`$APPDIR\$nativeName`njava-options=-Dledger.windows.icon=`$APPDIR\$iconName`n"
$temporaryConfig = "$config.$([Guid]::NewGuid().ToString('N')).tmp"
[IO.File]::WriteAllText($temporaryConfig,$configText,[Text.UTF8Encoding]::new($false))
[IO.File]::Replace($temporaryConfig,$config,"$config.$tag.previous")
}
Write-Host "Ready for next launch: Business Ledger $version"
Write-Host "Files already used by a running instance were retained. New JAR: $jarPath"

param([Parameter(Mandatory=$true)][string]$Destination)
$ErrorActionPreference='Stop'
$jdk=Split-Path -Parent (Split-Path -Parent (Get-Command javac).Source)
$compiler=Get-Command cl.exe -ErrorAction SilentlyContinue
if($compiler -and $compiler.Source -match '\\x64\\cl\.exe$'){$compilerPath=$compiler.Source}else{
    $compilerPath=Get-ChildItem 'C:\Program Files\Microsoft Visual Studio\*\*\VC\Tools\MSVC\*\bin\Hostx64\x64\cl.exe' -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if(!$compilerPath){throw 'The Windows taskbar helper requires the Visual Studio x64 C++ build tools.'}
$msvc=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $compilerPath)))
$kits=Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10'
$sdk=Get-ChildItem (Join-Path $kits 'Include') -Directory | Where-Object {Test-Path (Join-Path $_.FullName 'um\Windows.h')} | Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty Name
if(!$sdk){throw 'The Windows SDK is required to build taskbar integration.'}
$build=Join-Path $PSScriptRoot ('target\windows-taskbar-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $build,$Destination | Out-Null
$resource=Join-Path $build 'identity.rc'
[IO.File]::WriteAllText($resource,"STRINGTABLE`r`nBEGIN`r`n 101 `"Business Ledger`"`r`nEND`r`n")
$resourceOutput=Join-Path $build 'identity.res'
& (Join-Path $kits "bin\$sdk\x64\rc.exe") /nologo "/fo$resourceOutput" $resource
if($LASTEXITCODE -ne 0){throw 'Taskbar name resource compilation failed.'}
$include=@((Join-Path $msvc 'include'),(Join-Path $kits "Include\$sdk\ucrt"),(Join-Path $kits "Include\$sdk\shared"),(Join-Path $kits "Include\$sdk\um"),(Join-Path $jdk 'include'),(Join-Path $jdk 'include\win32')) | ForEach-Object {'/I'+$_}
$libraries=@((Join-Path $msvc 'lib\x64'),(Join-Path $kits "Lib\$sdk\ucrt\x64"),(Join-Path $kits "Lib\$sdk\um\x64")) | ForEach-Object {'/LIBPATH:'+$_}
& $compilerPath /nologo /LD /MT /EHsc /W4 /utf-8 /DUNICODE /D_UNICODE @include "/Fo$build\WindowsTaskbar.obj" (Join-Path $PSScriptRoot 'installer\WindowsTaskbar.cpp') $resourceOutput /link @libraries Shell32.lib Ole32.lib Propsys.lib User32.lib Uuid.lib "/OUT:$Destination\LedgerWindows.dll" "/IMPLIB:$build\LedgerWindows.lib"
if($LASTEXITCODE -ne 0){throw 'Windows taskbar helper compilation failed.'}

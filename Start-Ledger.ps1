$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!(Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Java JDK 21 or newer must be on PATH.' }
New-Item -ItemType Directory -Force lib,target\classes | Out-Null
$driver = Join-Path $PSScriptRoot 'lib\postgresql-42.7.8.jar'
if (!(Test-Path -LiteralPath $driver)) {
    Write-Host 'Downloading the PostgreSQL Java database driver...'
    $download = "$driver.download"
    Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar' -OutFile $download
    Move-Item -LiteralPath $download -Destination $driver -Force
}
. (Join-Path $PSScriptRoot 'Release.Common.ps1')
Compile-Ledger (Join-Path $PSScriptRoot 'target\classes')
& java "-Dledger.home=$PSScriptRoot" -cp "target\classes;lib\postgresql-42.7.8.jar" com.businessledger.BusinessLedger
if ($LASTEXITCODE -ne 0) { throw 'Business Ledger failed.' }

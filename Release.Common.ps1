$ErrorActionPreference = 'Stop'
function Get-LedgerVersion {
    [xml]$project = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pom.xml') -Raw
    $releaseVersion = [string]$project.project.version
    if ($releaseVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'pom.xml must use a major.minor.patch release version.' }
    foreach ($part in $releaseVersion.Split('.')) { if ([int]$part -gt 65535) { throw 'Version components must be at most 65535.' } }
    return $releaseVersion
}
function Compile-Ledger([string]$Classes) {
    New-Item -ItemType Directory -Force -Path $Classes | Out-Null
    & javac --release 21 -encoding UTF-8 -d $Classes (Get-ChildItem (Join-Path $PSScriptRoot 'src/main/java/com/businessledger/*.java') | ForEach-Object FullName)
    if ($LASTEXITCODE -ne 0) { throw 'Compiling Business Ledger failed.' }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'src/main/resources/icons') -Destination $Classes -Recurse -Force
    $version = Get-LedgerVersion
    $built = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    [IO.File]::WriteAllText((Join-Path $Classes 'release.properties'), "version=$version`nbuilt=$built`n", [Text.UTF8Encoding]::new($false))
}

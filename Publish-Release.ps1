param([ValidateSet('Mr-Cee/Business-Ledger')][string]$Repository = 'Mr-Cee/Business-Ledger')
$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 turns redirected native stderr into PowerShell errors.
# Probes deliberately accept nonzero exits when a repository, remote, or release is absent.
function Invoke-NativeProbe {
    param([string]$File, [string[]]$Arguments)
    $ErrorActionPreference = 'Continue'
    $output = @(& $File @Arguments 2>$null)
    [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}
Set-Location -LiteralPath $PSScriptRoot
. (Join-Path $PSScriptRoot 'Release.Common.ps1')
if ($Repository -notmatch '^[A-Za-z0-9-]+/[A-Za-z0-9_.-]+$') { throw 'Use an owner/repository GitHub name.' }
$version = Get-LedgerVersion
$tag = "v$version"
$setup = Join-Path $PSScriptRoot "releases/BusinessLedger-Setup-$version.exe"
$checksum = Join-Path $PSScriptRoot "releases/BusinessLedger-Setup-$version.sha256"
if (!(Test-Path -LiteralPath $setup) -or !(Test-Path -LiteralPath $checksum)) { throw 'Build and verify this release with Build-Installer.ps1 first.' }
$expected = ((Get-Content -LiteralPath $checksum -Raw).Trim() -split '\s+')[0]
if ((Get-FileHash -LiteralPath $setup -Algorithm SHA256).Hash -ne $expected) { throw 'Installer does not match its checksum. Rebuild before publishing.' }
$ghCommand = Get-Command gh -ErrorAction SilentlyContinue
$gh = if ($ghCommand) { $ghCommand.Source } else { Join-Path $PSScriptRoot 'target/tools/github-cli/bin/gh.exe' }
if (!(Test-Path -LiteralPath $gh)) { throw 'Install GitHub CLI (https://cli.github.com), then run gh auth login.' }
& $gh auth status --hostname github.com
if ($LASTEXITCODE -ne 0) { throw 'Sign in to GitHub CLI with gh auth login, then run this script again.' }

# Stage only known application source and documentation. Never stage the whole ledger folder.
$source = @('.gitignore','.gitattributes','AGENTS.md','README.md','RELEASING.md','CHANGELOG.md','pom.xml')
$source += Get-ChildItem -LiteralPath $PSScriptRoot -File -Filter '*.ps1' | ForEach-Object Name
$source += foreach ($folder in @('src/main/java/com/businessledger','src/test/java/com/businessledger')) {
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot $folder) -File -Filter '*.java' | ForEach-Object { "$folder/$($_.Name)" }
}
$source += 'src/main/resources/release.properties','sql/setup.sql'
$source += 'artwork/business-ledger-source.png'
$source += Get-ChildItem 'src/main/resources/icons' -File | Where-Object { $_.Extension -in '.png','.ico' } | ForEach-Object { 'src/main/resources/icons/'+$_.Name }
$source += Get-ChildItem installer -File | Where-Object { $_.Extension -in '.cs','.cpp','.py','.manifest' } | ForEach-Object { 'installer/'+$_.Name }
if (!(Test-Path -LiteralPath '.git')) {
    git init --initial-branch=main
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize source control.' }
}
$tracked = @(git ls-files)
if ($LASTEXITCODE -ne 0) { throw 'Unable to review tracked source.' }
foreach ($path in $tracked) { if ($path -notin $source) { throw "Unexpected tracked file: $path. Review it before publishing." } }
git add -- @source
if ($LASTEXITCODE -ne 0) { throw 'Unable to stage application source.' }
git diff --cached --quiet
if ($LASTEXITCODE -eq 1) {
    $authorName = git config user.name
    $authorEmail = git config user.email
    $authorArguments = @()
    if (!$authorName -or !$authorEmail) {
        $identity = & $gh api user | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or !$identity.login -or !$identity.id) { throw 'Unable to determine the GitHub commit identity.' }
        if (!$authorName) { $authorArguments += @('-c', "user.name=$($identity.login)") }
        if (!$authorEmail) { $authorArguments += @('-c', "user.email=$($identity.id)+$($identity.login)@users.noreply.github.com") }
    }
    git @authorArguments commit -m "Release Business Ledger $version"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to commit. Configure your Git author name and email, then retry.' }
} elseif ($LASTEXITCODE -ne 0) { throw 'Unable to review source changes.' }

$repoProbe = Invoke-NativeProbe $gh @('repo','view',$Repository,'--json','nameWithOwner')
if ($repoProbe.ExitCode -ne 0) {
    & $gh repo create $Repository --public --description 'Windows desktop business ledger with bundled Java and PostgreSQL.'
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create the public repository.' }
}
$remoteProbe = Invoke-NativeProbe 'git' @('remote','get-url','origin')
if ($remoteProbe.ExitCode -eq 0) {
    $remote = ($remoteProbe.Output -join "`n").Trim()
    if ($remote -notin @("https://github.com/$Repository.git","https://github.com/$Repository","git@github.com:$Repository.git")) { throw 'The origin remote points to a different repository.' }
} else { git remote add origin "https://github.com/$Repository.git"; if ($LASTEXITCODE -ne 0) { throw 'Unable to configure GitHub remote.' } }
git push -u origin HEAD:main
if ($LASTEXITCODE -ne 0) { throw 'Source push failed. Check GitHub authentication before retrying.' }
$releaseProbe = Invoke-NativeProbe $gh @('release','view',$tag,'--repo',$Repository,'--json','tagName')
if ($releaseProbe.ExitCode -eq 0) { throw 'This release already exists. Never replace a distributed release; increment pom.xml for the next release.' }
$notes = Join-Path $PSScriptRoot "target/release-notes-$version.md"
$history = Get-Content -LiteralPath CHANGELOG.md -Raw
$section = [regex]::Match($history, '(?ms)^## '+[regex]::Escape($version)+'[^\r\n]*\r?\n(.*?)(?=^## |\z)')
if (!$section.Success) { throw 'Add this release to CHANGELOG.md first.' }
[IO.File]::WriteAllText($notes, $section.Groups[1].Value.Trim()+"`n`nDownload the Windows x64 installer below. Close Ledger before installing and keep your existing ledger data folder selected.`n", [Text.UTF8Encoding]::new($false))
& $gh release create $tag --repo $Repository --target main --draft --title "Business Ledger $version" --notes-file $notes
if ($LASTEXITCODE -ne 0) { throw 'Unable to create the draft release.' }
& $gh release upload $tag $setup $checksum --repo $Repository
if ($LASTEXITCODE -ne 0) { throw 'Asset upload failed; the release remains a draft and will not trigger update prompts.' }
& $gh release edit $tag --repo $Repository --draft=false --latest
if ($LASTEXITCODE -ne 0) { throw 'Unable to publish the completed draft release.' }
Write-Host "Published https://github.com/$Repository/releases/tag/$tag"

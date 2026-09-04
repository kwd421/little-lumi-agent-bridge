[CmdletBinding()]
param(
    [string]$Owner = 'kwd421',
    [string]$Repository = 'little-lumi-agent-bridge'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Git = Get-Command git -ErrorAction SilentlyContinue
if ($null -eq $Git) { throw 'Git is not installed or not available in PATH.' }
$Gh = Get-Command gh -ErrorAction SilentlyContinue
if ($null -eq $Gh) { throw 'GitHub CLI (gh) is not installed. Install it, then run gh auth login.' }

& gh auth status
if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI is not authenticated. Run gh auth login.' }

if (-not (Test-Path (Join-Path $Root '.git'))) {
    & git -C $Root init -b main
    if ($LASTEXITCODE -ne 0) { throw 'git init failed.' }
}

$HasHead = $true
& git -C $Root rev-parse --verify HEAD *> $null
if ($LASTEXITCODE -ne 0) { $HasHead = $false }

& git -C $Root add --all
if ($LASTEXITCODE -ne 0) { throw 'git add failed.' }
if (-not $HasHead) {
    $ConfigJava = Join-Path $Root 'src\main\java\io\github\kwd421\lumitoolbridge\Config.java'
    $Version = [regex]::Match([IO.File]::ReadAllText($ConfigJava), 'VERSION\s*=\s*"([^"]+)"').Groups[1].Value
    & git -C $Root commit -m "Initial release: Little LUMI Agent Bridge $Version"
    if ($LASTEXITCODE -ne 0) {
        throw 'Initial commit failed. Configure git user.name and user.email, then retry.'
    }
} else {
    & git -C $Root diff --cached --quiet
    if ($LASTEXITCODE -ne 0) {
        & git -C $Root commit -m 'Update Little LUMI Agent Bridge'
        if ($LASTEXITCODE -ne 0) { throw 'Commit failed.' }
    }
}

$CurrentBranch = (& git -C $Root branch --show-current).Trim()
if ($CurrentBranch -ne 'main') {
    & git -C $Root branch -M main
    if ($LASTEXITCODE -ne 0) { throw 'Could not rename the branch to main.' }
}

$ConfigJava = Join-Path $Root 'src\main\java\io\github\kwd421\lumitoolbridge\Config.java'
$Version = [regex]::Match([IO.File]::ReadAllText($ConfigJava), 'VERSION\s*=\s*"([^"]+)"').Groups[1].Value
$Tag = "v$Version"
& git -C $Root rev-parse --verify "refs/tags/$Tag" *> $null
if ($LASTEXITCODE -ne 0) {
    & git -C $Root tag -a $Tag -m "Little LUMI Agent Bridge $Tag"
    if ($LASTEXITCODE -ne 0) { throw "Could not create tag $Tag." }
}

$FullName = "$Owner/$Repository"
$ExpectedRemote = "https://github.com/$FullName.git"
& gh repo view $FullName --json nameWithOwner *> $null
$RepositoryExists = $LASTEXITCODE -eq 0

if (-not $RepositoryExists) {
    & gh repo create $FullName --public --description `
        'Unofficial external tool-calling, web search, and Codex bridge for Little LUMI.'
    if ($LASTEXITCODE -ne 0) { throw "Could not create public repository $FullName." }
}

$Origin = (& git -C $Root remote get-url origin 2>$null)
if ([string]::IsNullOrWhiteSpace($Origin)) {
    & git -C $Root remote add origin $ExpectedRemote
} elseif ($Origin.Trim() -ne $ExpectedRemote) {
    & git -C $Root remote set-url origin $ExpectedRemote
}
if ($LASTEXITCODE -ne 0) { throw 'Could not configure the origin remote.' }

& git -C $Root push -u origin main
if ($LASTEXITCODE -ne 0) { throw 'Pushing main failed.' }
& git -C $Root push origin $Tag
if ($LASTEXITCODE -ne 0) { throw "Pushing $Tag failed." }

Write-Host "Published https://github.com/$FullName" -ForegroundColor Green
Write-Host "The $Tag tag triggers the GitHub release workflow." -ForegroundColor Green

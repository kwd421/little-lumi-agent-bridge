[CmdletBinding()]
param([string]$InstallDir = '')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-common.ps1')
$InstallDir = Resolve-BridgeInstallDir $InstallDir
$Jar = Join-Path $InstallDir 'little-lumi-agent-bridge.jar'
$PidFile = Join-Path $InstallDir 'bridge-process.json'
$Candidates = @()
if (Test-Path $PidFile) {
    try {
        $PidState = Get-Content $PidFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($null -ne $PidState.pid) { $Candidates += [int]$PidState.pid }
    } catch {}
}
try {
    $EscapedJar = [regex]::Escape($Jar)
    $Found = Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_.CommandLine) -and $_.CommandLine -match $EscapedJar -and $_.CommandLine -notmatch '(^|\s)--manager(\s|$)'
    }
    $Candidates += @($Found | ForEach-Object { $_.ProcessId })
} catch {}
$Candidates = @($Candidates | Sort-Object -Unique)
foreach ($ProcessId in $Candidates) {
    try {
        $Info = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop
        if (-not [string]::IsNullOrWhiteSpace($Info.CommandLine) -and $Info.CommandLine.IndexOf($Jar, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and $Info.CommandLine -notmatch '(^|\s)--manager(\s|$)') {
            Stop-Process -Id $ProcessId -Force -ErrorAction Stop
        }
    } catch {}
}
Remove-Item $PidFile -Force -ErrorAction SilentlyContinue

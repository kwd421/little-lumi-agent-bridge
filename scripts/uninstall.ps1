[CmdletBinding()]
param(
    [string]$InstallDir = '',
    [switch]$KeepFiles,
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'runtime-common.ps1')
$InstallDir = Resolve-BridgeInstallDir $InstallDir
$State = Read-BridgeState -InstallDir $InstallDir
if ($null -eq $State) { throw "Install state not found under $InstallDir" }

$StopScript = Join-Path $InstallDir 'scripts\stop-bridge.ps1'
if (Test-Path $StopScript) { & $StopScript -InstallDir $InstallDir }
$AiProperties = [string]$State.aiProperties
if (Test-Path $AiProperties) {
    $Current = Get-PropertyLine -Path $AiProperties -Key 'llm.base.ollama'
    if ($Force -or $Current -eq [string]$State.localBaseLine) {
        Set-PropertyLine -Path $AiProperties -Key 'llm.base.ollama' -NewLine ([string]$State.originalBaseLine) | Out-Null
        Write-Host 'Restored the original llm.base.ollama line.' -ForegroundColor Green
    } else {
        Write-Warning 'The Ollama base line changed after installation, so it was not overwritten. Use -Force only after checking install-state.json.'
    }
} else {
    Write-Warning "Little LUMI AI config no longer exists: $AiProperties"
}

$StartupShortcut = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Startup\Little LUMI Agent Bridge.lnk'
$DesktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Little LUMI Agent.lnk'
$ManagerShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Little LUMI Agent Manager.lnk'
Remove-Item $StartupShortcut, $DesktopShortcut, $ManagerShortcut -Force -ErrorAction SilentlyContinue

if (-not $KeepFiles) {
    try {
        Remove-Item $InstallDir -Recurse -Force -ErrorAction Stop
    } catch {
        Write-Warning "Could not remove $InstallDir while the script was running. It is safe to delete manually after this window closes."
    }
}
Write-Host 'Little LUMI Agent Bridge removed.' -ForegroundColor Green

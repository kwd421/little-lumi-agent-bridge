[CmdletBinding()]
param(
    [string]$InstallDir = '',
    [switch]$Disable
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'runtime-common.ps1')

function New-Shortcut {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][string]$Target,
        [Parameter(Mandatory=$true)][string]$Arguments,
        [Parameter(Mandatory=$true)][string]$WorkingDirectory,
        [string]$Icon = ''
    )
    New-Item (Split-Path $Path -Parent) -ItemType Directory -Force | Out-Null
    $Shell = New-Object -ComObject WScript.Shell
    $Shortcut = $Shell.CreateShortcut($Path)
    $Shortcut.TargetPath = $Target
    $Shortcut.Arguments = $Arguments
    $Shortcut.WorkingDirectory = $WorkingDirectory
    if (-not [string]::IsNullOrWhiteSpace($Icon)) { $Shortcut.IconLocation = $Icon }
    $Shortcut.Save()
}

$InstallDir = Resolve-BridgeInstallDir $InstallDir
$State = Read-BridgeState -InstallDir $InstallDir
if ($null -eq $State) { throw "Install state not found under $InstallDir" }
$ShortcutPath = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Startup\Little LUMI Agent Bridge.lnk'
if ($Disable) {
    Remove-Item -LiteralPath $ShortcutPath -Force -ErrorAction SilentlyContinue
    Write-Host 'Bridge auto-start disabled.'
    exit 0
}
$PowerShellExe = (Get-Command powershell.exe -ErrorAction Stop).Source
$StartScript = Join-Path $InstallDir 'scripts\start-bridge.ps1'
$Arguments = '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $StartScript + '" -InstallDir "' + $InstallDir + '"'
$Icon = Join-Path ([string]$State.appRoot) 'Little LUMI.exe'
New-Shortcut -Path $ShortcutPath -Target $PowerShellExe -Arguments $Arguments -WorkingDirectory $InstallDir -Icon $Icon
Write-Host 'Bridge auto-start enabled.'

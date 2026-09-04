[CmdletBinding()]
param(
    [string]$LittleLumiRoot = '',
    [string]$InstallDir = '',
    [ValidateRange(1,65535)][int]$Port = 11435,
    [switch]$NoAutoStart,
    [switch]$NoDesktopShortcut,
    [switch]$EnableCodex,
    [string]$CodexWorkspace = '',
    [switch]$EnableCodexWrite,
    [switch]$EnableLocalFiles,
    [string[]]$LocalFileRoots = @(),
    [switch]$EnableMcp,
    [string]$McpConfig = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'runtime-common.ps1')

function Find-LittleLumiRoot {
    param([string]$Requested)
    $Candidates = [Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($Requested)) { $Candidates.Add($Requested) }

    $ProgramFilesX86 = [Environment]::GetEnvironmentVariable('ProgramFiles(x86)')
    if (-not [string]::IsNullOrWhiteSpace($ProgramFilesX86)) {
        $Candidates.Add((Join-Path $ProgramFilesX86 'Steam\steamapps\common\Little LUMI'))
    }
    $ProgramFiles = [Environment]::GetEnvironmentVariable('ProgramFiles')
    if (-not [string]::IsNullOrWhiteSpace($ProgramFiles)) {
        $Candidates.Add((Join-Path $ProgramFiles 'Steam\steamapps\common\Little LUMI'))
    }

    $SteamRoots = [Collections.Generic.List[string]]::new()
    foreach ($RegistryPath in @('HKCU:\Software\Valve\Steam','HKLM:\SOFTWARE\WOW6432Node\Valve\Steam','HKLM:\SOFTWARE\Valve\Steam')) {
        try {
            $Item = Get-ItemProperty $RegistryPath -ErrorAction Stop
            foreach ($Name in @('SteamPath','InstallPath')) {
                if ($null -ne $Item.$Name -and -not [string]::IsNullOrWhiteSpace([string]$Item.$Name)) {
                    $SteamRoots.Add([string]$Item.$Name)
                }
            }
        } catch {}
    }
    foreach ($SteamRoot in @($SteamRoots | Sort-Object -Unique)) {
        $Candidates.Add((Join-Path $SteamRoot 'steamapps\common\Little LUMI'))
        $Vdf = Join-Path $SteamRoot 'steamapps\libraryfolders.vdf'
        if (Test-Path $Vdf) {
            $Text = Get-Content $Vdf -Raw -Encoding UTF8
            foreach ($Match in [regex]::Matches($Text, '"path"\s+"([^"]+)"')) {
                $Library = $Match.Groups[1].Value.Replace('\\','\')
                $Candidates.Add((Join-Path $Library 'steamapps\common\Little LUMI'))
            }
        }
    }
    foreach ($Candidate in @($Candidates | Sort-Object -Unique)) {
        $Expanded = [Environment]::ExpandEnvironmentVariables($Candidate)
        if ((Test-Path (Join-Path $Expanded 'Little LUMI.exe')) -and
            (Test-Path (Join-Path $Expanded 'app\conf\ai.properties'))) {
            return (Resolve-Path $Expanded).Path
        }
    }
    throw 'Little LUMI installation was not found. Pass -LittleLumiRoot with the Steam installation path.'
}

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

function Restore-FileState {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][bool]$Existed,
        [Parameter(Mandatory=$true)][string]$Backup
    )
    if ($Existed -and (Test-Path $Backup)) {
        New-Item (Split-Path $Path -Parent) -ItemType Directory -Force | Out-Null
        Copy-Item -LiteralPath $Backup -Destination $Path -Force
    } else {
        Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    }
}

if ($EnableCodexWrite -and -not $EnableCodex) {
    throw '-EnableCodexWrite requires -EnableCodex.'
}
if ($EnableCodex -and [string]::IsNullOrWhiteSpace($CodexWorkspace)) {
    throw '-EnableCodex requires -CodexWorkspace.'
}
if ($EnableCodex -and -not (Test-Path $CodexWorkspace -PathType Container)) {
    throw "Codex workspace does not exist: $CodexWorkspace"
}
if ($EnableLocalFiles -and $LocalFileRoots.Count -eq 0) {
    throw '-EnableLocalFiles requires one or more -LocalFileRoots.'
}
$ResolvedLocalFileRoots = [Collections.Generic.List[string]]::new()
if ($EnableLocalFiles) {
    foreach ($Root in $LocalFileRoots) {
        if (-not (Test-Path $Root -PathType Container)) { throw "Local file root does not exist: $Root" }
        $ResolvedLocalFileRoots.Add((Resolve-Path $Root).Path.Replace('\','/'))
    }
}
if ($EnableMcp -and [string]::IsNullOrWhiteSpace($McpConfig)) {
    throw '-EnableMcp requires -McpConfig pointing to an MCP JSON config.'
}
if ($EnableMcp -and -not (Test-Path $McpConfig -PathType Leaf)) {
    throw "MCP config does not exist: $McpConfig"
}

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$JarSource = Get-ChildItem (Join-Path $ProjectRoot 'dist') -Filter 'little-lumi-agent-bridge-*.jar' -File -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $JarSource) {
    throw 'Built JAR not found under dist. Run scripts\build.ps1 first or use a release ZIP.'
}
$VersionMatch = [regex]::Match($JarSource.BaseName, '^little-lumi-agent-bridge-(.+)$')
$BridgeVersion = if ($VersionMatch.Success) { $VersionMatch.Groups[1].Value } else { 'unknown' }
$ConfigTemplate = Join-Path $ProjectRoot 'config\bridge.properties.example'
if (-not (Test-Path $ConfigTemplate)) { throw "Config template missing: $ConfigTemplate" }

$LittleLumiRoot = Find-LittleLumiRoot $LittleLumiRoot
$InstallDir = Resolve-BridgeInstallDir $InstallDir
$InstallRoot = [IO.Path]::GetPathRoot($InstallDir)
if ([string]::Equals($InstallDir, $InstallRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'InstallDir cannot be a drive root.'
}
if ([string]::Equals($InstallDir, $ProjectRoot, [StringComparison]::OrdinalIgnoreCase) -or
    [string]::Equals($InstallDir, $LittleLumiRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'InstallDir must be a dedicated bridge directory, not the release or Little LUMI directory.'
}

$AiProperties = Join-Path $LittleLumiRoot 'app\conf\ai.properties'
$Provider = Get-PropertyValue -Path $AiProperties -Key 'llm.provider'
if ($Provider -ne 'ollama') {
    throw "Little LUMI currently uses provider '$Provider'. Select Ollama and verify chat works before installing."
}

$ExistingState = Read-BridgeState -InstallDir $InstallDir
$HadExistingInstall = Test-Path $InstallDir -PathType Container
if ($HadExistingInstall -and $null -eq $ExistingState) {
    $ExistingItems = @(Get-ChildItem -LiteralPath $InstallDir -Force)
    if ($ExistingItems.Count -gt 0) {
        throw "InstallDir is not empty and has no Little LUMI Agent Bridge state file: $InstallDir"
    }
}

$CurrentBaseLine = Get-PropertyLine -Path $AiProperties -Key 'llm.base.ollama'
if ($null -eq $CurrentBaseLine) { throw "llm.base.ollama is missing from $AiProperties" }
$LocalBaseLine = "llm.base.ollama=http\://127.0.0.1\:$Port/v1"
if ($null -ne $ExistingState -and [string]$ExistingState.aiProperties -eq $AiProperties) {
    $KnownLocalLine = [string]$ExistingState.localBaseLine
    $KnownOriginalLine = [string]$ExistingState.originalBaseLine
    if ($CurrentBaseLine -ne $KnownLocalLine -and $CurrentBaseLine -ne $KnownOriginalLine) {
        throw 'llm.base.ollama changed outside the bridge after installation. Run uninstall.ps1 or reconcile install-state.json before reinstalling.'
    }
    $OriginalBaseLine = $KnownOriginalLine
} elseif ($CurrentBaseLine -eq $LocalBaseLine) {
    throw 'Little LUMI already points at this bridge but no matching install state exists. Restore the cloud URL before reinstalling.'
} else {
    $OriginalBaseLine = $CurrentBaseLine
}

$PowerShellExe = (Get-Command powershell.exe -ErrorAction Stop).Source
$StartupShortcut = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Startup\Little LUMI Agent Bridge.lnk'
$DesktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Little LUMI Agent.lnk'
$RollbackRoot = Join-Path ([IO.Path]::GetTempPath()) ('LittleLumiAgentBridge-' + [guid]::NewGuid().ToString('N'))
$BackupInstall = Join-Path $RollbackRoot 'install'
$BackupStartup = Join-Path $RollbackRoot 'startup.lnk'
$BackupDesktop = Join-Path $RollbackRoot 'desktop.lnk'
$HadStartupShortcut = Test-Path $StartupShortcut
$HadDesktopShortcut = Test-Path $DesktopShortcut
$OldWasRunning = $false
$ChangesStarted = $false
$Health = $null

New-Item $RollbackRoot -ItemType Directory -Force | Out-Null
try {
    if ($HadExistingInstall) {
        try { $OldWasRunning = Test-IsBridgeHealth (Get-BridgeHealth -InstallDir $InstallDir) } catch {}
        $OldStop = Join-Path $InstallDir 'scripts\stop-bridge.ps1'
        if (Test-Path $OldStop) {
            try { & $OldStop -InstallDir $InstallDir } catch {}
        }
        New-Item $BackupInstall -ItemType Directory -Force | Out-Null
        Get-ChildItem -LiteralPath $InstallDir -Force | Copy-Item -Destination $BackupInstall -Recurse -Force
    }
    if ($HadStartupShortcut) { Copy-Item -LiteralPath $StartupShortcut -Destination $BackupStartup -Force }
    if ($HadDesktopShortcut) { Copy-Item -LiteralPath $DesktopShortcut -Destination $BackupDesktop -Force }

    $ChangesStarted = $true
    New-Item $InstallDir -ItemType Directory -Force | Out-Null
    New-Item (Join-Path $InstallDir 'scripts') -ItemType Directory -Force | Out-Null
    Copy-Item $JarSource.FullName (Join-Path $InstallDir 'little-lumi-agent-bridge.jar') -Force
    foreach ($Script in @('runtime-common.ps1','start-bridge.ps1','stop-bridge.ps1','doctor.ps1','uninstall.ps1')) {
        Copy-Item (Join-Path $PSScriptRoot $Script) (Join-Path $InstallDir "scripts\$Script") -Force
    }

    $BridgeConfig = Join-Path $InstallDir 'bridge.properties'
    if (-not (Test-Path $BridgeConfig)) { Copy-Item $ConfigTemplate $BridgeConfig }
    Set-PropertyLine -Path $BridgeConfig -Key 'server.port' -NewLine "server.port=$Port" | Out-Null

    if ($EnableLocalFiles) {
        Set-PropertyLine -Path $BridgeConfig -Key 'tools.files.enabled' -NewLine 'tools.files.enabled=true' | Out-Null
        Set-PropertyLine -Path $BridgeConfig -Key 'tools.files.roots' -NewLine ("tools.files.roots=" + ($ResolvedLocalFileRoots -join ';')) | Out-Null
    }

    if ($EnableMcp) {
        $McpInstallDir = Join-Path $InstallDir 'config'
        New-Item $McpInstallDir -ItemType Directory -Force | Out-Null
        $McpDestination = Join-Path $McpInstallDir 'mcp.json'
        Copy-Item (Resolve-Path $McpConfig).Path $McpDestination -Force
        Set-PropertyLine -Path $BridgeConfig -Key 'tools.mcp.enabled' -NewLine 'tools.mcp.enabled=true' | Out-Null
        Set-PropertyLine -Path $BridgeConfig -Key 'tools.mcp.config' -NewLine 'tools.mcp.config=config/mcp.json' | Out-Null
    }

    if ($EnableCodex) {
        $WorkspaceValue = (Resolve-Path $CodexWorkspace).Path.Replace('\','/')
        Set-PropertyLine -Path $BridgeConfig -Key 'tools.codex.enabled' -NewLine 'tools.codex.enabled=true' | Out-Null
        Set-PropertyLine -Path $BridgeConfig -Key 'tools.codex.workspace' -NewLine "tools.codex.workspace=$WorkspaceValue" | Out-Null
        Set-PropertyLine -Path $BridgeConfig -Key 'tools.codex.writeEnabled' -NewLine ("tools.codex.writeEnabled=" + ([bool]$EnableCodexWrite).ToString().ToLowerInvariant()) | Out-Null
        $Codex = Get-Command codex -ErrorAction SilentlyContinue
        if ($null -ne $Codex -and -not [string]::IsNullOrWhiteSpace($Codex.Source)) {
            $CommandValue = $Codex.Source.Replace('\','/')
            Set-PropertyLine -Path $BridgeConfig -Key 'tools.codex.command' -NewLine "tools.codex.command=$CommandValue" | Out-Null
        }
    }

    $State = [ordered]@{
        version = $BridgeVersion
        installedAt = [DateTimeOffset]::UtcNow.ToString('o')
        installDir = $InstallDir
        appRoot = $LittleLumiRoot
        aiProperties = $AiProperties
        originalBaseLine = $OriginalBaseLine
        localBaseLine = $LocalBaseLine
        port = $Port
    }
    Write-BridgeState -InstallDir $InstallDir -State $State

    $StartScript = Join-Path $InstallDir 'scripts\start-bridge.ps1'
    $Icon = Join-Path $LittleLumiRoot 'Little LUMI.exe'
    if ($NoAutoStart) {
        Remove-Item $StartupShortcut -Force -ErrorAction SilentlyContinue
    } else {
        $Arguments = '-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $StartScript + '" -InstallDir "' + $InstallDir + '"'
        New-Shortcut -Path $StartupShortcut -Target $PowerShellExe -Arguments $Arguments -WorkingDirectory $InstallDir -Icon $Icon
    }
    if ($NoDesktopShortcut) {
        Remove-Item $DesktopShortcut -Force -ErrorAction SilentlyContinue
    } else {
        $Arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $StartScript + '" -InstallDir "' + $InstallDir + '" -LaunchLumi'
        New-Shortcut -Path $DesktopShortcut -Target $PowerShellExe -Arguments $Arguments -WorkingDirectory $InstallDir -Icon $Icon
    }

    & $StartScript -InstallDir $InstallDir
    $Health = Get-BridgeHealth -InstallDir $InstallDir
    if (-not (Test-IsBridgeHealth $Health)) { throw 'Bridge health check failed.' }

    # Patch Little LUMI only after the new bridge has passed its health check.
    Set-PropertyLine -Path $AiProperties -Key 'llm.base.ollama' -NewLine $LocalBaseLine | Out-Null
} catch {
    $InstallError = $_
    if ($ChangesStarted) {
        try {
            $NewStop = Join-Path $InstallDir 'scripts\stop-bridge.ps1'
            if (Test-Path $NewStop) { & $NewStop -InstallDir $InstallDir }
        } catch {}
        try { Set-PropertyLine -Path $AiProperties -Key 'llm.base.ollama' -NewLine $CurrentBaseLine | Out-Null } catch {}
        try {
            Remove-Item $InstallDir -Recurse -Force -ErrorAction SilentlyContinue
            if ($HadExistingInstall) {
                New-Item $InstallDir -ItemType Directory -Force | Out-Null
                Get-ChildItem -LiteralPath $BackupInstall -Force | Copy-Item -Destination $InstallDir -Recurse -Force
            }
        } catch {
            Write-Warning "Could not fully restore the bridge directory: $($_.Exception.Message)"
        }
        try { Restore-FileState -Path $StartupShortcut -Existed $HadStartupShortcut -Backup $BackupStartup } catch {}
        try { Restore-FileState -Path $DesktopShortcut -Existed $HadDesktopShortcut -Backup $BackupDesktop } catch {}
    }
    if ($OldWasRunning -and $HadExistingInstall) {
        try {
            $RestoredStart = Join-Path $InstallDir 'scripts\start-bridge.ps1'
            if (Test-Path $RestoredStart) { & $RestoredStart -InstallDir $InstallDir }
        } catch {
            Write-Warning "The previous bridge could not be restarted automatically: $($_.Exception.Message)"
        }
    }
    throw "Installation failed and rollback was attempted. $($InstallError.Exception.Message)"
} finally {
    Remove-Item $RollbackRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "Installed Little LUMI Agent Bridge $($Health.version)." -ForegroundColor Green
Write-Host "Little LUMI root: $LittleLumiRoot"
Write-Host "Bridge: http://127.0.0.1:$Port/v1"
Write-Host 'API keys were not copied or printed.'
if ($EnableLocalFiles) { Write-Host ('Local file roots: ' + ($ResolvedLocalFileRoots -join '; ')) }
if ($EnableMcp) { Write-Host 'MCP: enabled (write-capable tools remain disabled unless explicitly allowed in configuration).' }
if ($EnableCodex) { Write-Host ('Codex workspace: ' + (Resolve-Path $CodexWorkspace).Path) }

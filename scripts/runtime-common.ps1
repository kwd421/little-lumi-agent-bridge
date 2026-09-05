Set-StrictMode -Version Latest

function Resolve-BridgeInstallDir {
    param([string]$InstallDir)
    if (-not [string]::IsNullOrWhiteSpace($InstallDir)) {
        return [IO.Path]::GetFullPath([Environment]::ExpandEnvironmentVariables($InstallDir))
    }
    $Parent = Split-Path $PSScriptRoot -Parent
    if (Test-Path (Join-Path $Parent 'install-state.json')) { return $Parent }
    return (Join-Path $env:LOCALAPPDATA 'LittleLumiAgentBridge')
}

function Read-BridgeState {
    param([Parameter(Mandatory=$true)][string]$InstallDir)
    $Path = Join-Path $InstallDir 'install-state.json'
    if (-not (Test-Path $Path)) { return $null }
    return (Get-Content $Path -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Get-PropertyLine {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][string]$Key
    )
    if (-not (Test-Path $Path)) { return $null }
    $Pattern = '^(\s*' + [regex]::Escape($Key) + '\s*=.*)$'
    $FoundLines = @(Get-Content $Path -Encoding UTF8 | Where-Object { $_ -match $Pattern })
    if ($FoundLines.Count -eq 0) { return $null }
    if ($FoundLines.Count -gt 1) { throw "Duplicate property '$Key' in $Path" }
    return [string]$FoundLines[0]
}

function Get-PropertyValue {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][string]$Key,
        [string]$Default = ''
    )
    $Line = Get-PropertyLine -Path $Path -Key $Key
    if ($null -eq $Line) { return $Default }
    $Value = $Line.Substring($Line.IndexOf('=') + 1).Trim()
    $Value = $Value.Replace('\:', ':').Replace('\=', '=').Replace('\\', '\')
    return $Value
}

function Set-PropertyLine {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][string]$Key,
        [Parameter(Mandatory=$true)][string]$NewLine
    )
    if (-not (Test-Path $Path)) { throw "Properties file not found: $Path" }
    $Utf8 = [Text.UTF8Encoding]::new($false)
    $Text = [IO.File]::ReadAllText($Path, $Utf8)
    $Eol = if ($Text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $Lines = [regex]::Split($Text, "`r?`n")
    $Pattern = '^\s*' + [regex]::Escape($Key) + '\s*='
    $Indexes = @()
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match $Pattern) { $Indexes += $i }
    }
    if ($Indexes.Count -gt 1) { throw "Duplicate property '$Key' in $Path" }
    $OldLine = $null
    if ($Indexes.Count -eq 1) {
        $OldLine = [string]$Lines[$Indexes[0]]
        $Lines[$Indexes[0]] = $NewLine
    } else {
        if ($Lines.Count -gt 0 -and $Lines[$Lines.Count - 1] -eq '') {
            $Lines[$Lines.Count - 1] = $NewLine
            $Lines += ''
        } else {
            $Lines += $NewLine
        }
    }
    $Temp = "$Path.lumi-agent-bridge.tmp"
    [IO.File]::WriteAllText($Temp, [string]::Join($Eol, $Lines), $Utf8)
    Move-Item $Temp $Path -Force
    return $OldLine
}

function Get-BridgePort {
    param([Parameter(Mandatory=$true)][string]$InstallDir)
    $Config = Join-Path $InstallDir 'bridge.properties'
    $Raw = Get-PropertyValue -Path $Config -Key 'server.port' -Default '11435'
    $Port = 11435
    if (-not [int]::TryParse($Raw, [ref]$Port)) { $Port = 11435 }
    return $Port
}

function Get-BridgeHealth {
    param(
        [Parameter(Mandatory=$true)][string]$InstallDir,
        [int]$TimeoutSeconds = 1
    )
    $Port = Get-BridgePort -InstallDir $InstallDir
    try {
        return Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/health" -TimeoutSec $TimeoutSeconds
    } catch {
        return $null
    }
}


function Test-IsBridgeHealth {
    param($Health)
    if ($null -eq $Health) { return $false }
    $Property = $Health.PSObject.Properties['name']
    return $null -ne $Property -and [string]$Property.Value -eq 'little-lumi-agent-bridge'
}

function Get-BridgeJavaPath {
    param(
        [Parameter(Mandatory=$true)][string]$InstallDir,
        [switch]$Console
    )
    $State = Read-BridgeState -InstallDir $InstallDir
    if ($null -ne $State -and -not [string]::IsNullOrWhiteSpace([string]$State.appRoot)) {
        $Name = if ($Console) { 'java.exe' } else { 'javaw.exe' }
        $Bundled = Join-Path ([string]$State.appRoot) "app\jre\bin\$Name"
        if (Test-Path $Bundled) { return $Bundled }
    }
    $FallbackName = if ($Console) { 'java.exe' } else { 'javaw.exe' }
    $Found = Get-Command $FallbackName -ErrorAction SilentlyContinue
    if ($null -ne $Found) { return $Found.Source }
    throw "Java runtime not found. Reinstall with a valid Little LUMI path."
}

function Write-BridgeState {
    param(
        [Parameter(Mandatory=$true)][string]$InstallDir,
        [Parameter(Mandatory=$true)]$State
    )
    New-Item $InstallDir -ItemType Directory -Force | Out-Null
    $Json = $State | ConvertTo-Json -Depth 8
    [IO.File]::WriteAllText((Join-Path $InstallDir 'install-state.json'), $Json, [Text.UTF8Encoding]::new($false))
}

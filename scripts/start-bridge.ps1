[CmdletBinding()]
param(
    [string]$InstallDir = '',
    [switch]$LaunchLumi
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-common.ps1')
$InstallDir = Resolve-BridgeInstallDir $InstallDir
$Jar = Join-Path $InstallDir 'little-lumi-agent-bridge.jar'
$Config = Join-Path $InstallDir 'bridge.properties'
$State = Read-BridgeState -InstallDir $InstallDir
if (-not (Test-Path $Jar)) { throw "Bridge JAR not found: $Jar" }
if (-not (Test-Path $Config)) { throw "Bridge config not found: $Config" }

$Health = Get-BridgeHealth -InstallDir $InstallDir
if ($null -ne $Health) {
    if (-not (Test-IsBridgeHealth $Health)) {
        throw "Port $(Get-BridgePort $InstallDir) is occupied by another service."
    }
} else {
    # Refresh API keys from the user registry so the bridge always starts with
    # current values even when the parent process was launched before the keys
    # were set. Keys are never written to files.
    foreach ($KeyName in @('GOOGLE_API_KEY', 'TAVILY_API_KEY', 'OLLAMA_API_KEY')) {
        $RegValue = [Environment]::GetEnvironmentVariable($KeyName, 'User')
        if (-not [string]::IsNullOrWhiteSpace($RegValue)) {
            [Environment]::SetEnvironmentVariable($KeyName, $RegValue, 'Process')
        }
    }
    $Java = Get-BridgeJavaPath -InstallDir $InstallDir
    $Logs = Join-Path $InstallDir 'logs'
    New-Item $Logs -ItemType Directory -Force | Out-Null
    $StdOut = Join-Path $Logs 'bridge.out.log'
    $StdErr = Join-Path $Logs 'bridge.err.log'
    $Arguments = @(
        '--add-modules', 'jdk.httpserver',
        '-jar', ('"{0}"' -f $Jar),
        '--config', ('"{0}"' -f $Config)
    )
    $Process = Start-Process -FilePath $Java -ArgumentList $Arguments -WorkingDirectory $InstallDir `
        -WindowStyle Hidden -RedirectStandardOutput $StdOut -RedirectStandardError $StdErr -PassThru
    $PidState = [ordered]@{
        pid = $Process.Id
        startedAt = [DateTimeOffset]::UtcNow.ToString('o')
        jar = $Jar
    }
    [IO.File]::WriteAllText((Join-Path $InstallDir 'bridge-process.json'),
        ($PidState | ConvertTo-Json), [Text.UTF8Encoding]::new($false))

    $Ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 250
        $Health = Get-BridgeHealth -InstallDir $InstallDir
        if (Test-IsBridgeHealth $Health) {
            $Ready = $true
            break
        }
        if ($Process.HasExited) { break }
    }
    if (-not $Ready) {
        if (-not $Process.HasExited) { Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue }
        $Tail = if (Test-Path $StdErr) { (Get-Content $StdErr -Tail 20 -ErrorAction SilentlyContinue) -join "`n" } else { '' }
        throw "Bridge failed to start. $Tail"
    }
}

if ($LaunchLumi) {
    if ($null -eq $State -or [string]::IsNullOrWhiteSpace([string]$State.appRoot)) {
        throw 'Little LUMI installation path is missing from install-state.json.'
    }
    $Exe = Join-Path ([string]$State.appRoot) 'Little LUMI.exe'
    if (-not (Test-Path $Exe)) { throw "Little LUMI executable not found: $Exe" }
    Start-Process -FilePath $Exe -WorkingDirectory ([string]$State.appRoot) | Out-Null
}

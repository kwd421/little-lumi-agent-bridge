[CmdletBinding()]
param([string]$InstallDir = '')
$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot 'runtime-common.ps1')
$InstallDir = Resolve-BridgeInstallDir $InstallDir
$Failures = 0
Write-Host "Little LUMI Agent Bridge doctor"
Write-Host "Install directory: $InstallDir"

$State = Read-BridgeState -InstallDir $InstallDir
if ($null -eq $State) {
    Write-Host '[FAIL] install-state.json is missing.' -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Little LUMI root: $($State.appRoot)" -ForegroundColor Green

$Jar = Join-Path $InstallDir 'little-lumi-agent-bridge.jar'
$Config = Join-Path $InstallDir 'bridge.properties'
foreach ($Path in @($Jar, $Config)) {
    if (Test-Path $Path) { Write-Host "[OK] $Path" -ForegroundColor Green }
    else { Write-Host "[FAIL] Missing: $Path" -ForegroundColor Red; $Failures++ }
}

$AiProperties = [string]$State.aiProperties
if (Test-Path $AiProperties) {
    $Provider = Get-PropertyValue -Path $AiProperties -Key 'llm.provider'
    $Base = Get-PropertyValue -Path $AiProperties -Key 'llm.base.ollama'
    Write-Host "[INFO] Little LUMI provider: $Provider"
    Write-Host "[INFO] Ollama base: $Base"
    if ($Provider -ne 'ollama') { Write-Host '[WARN] Select Ollama in Little LUMI for this bridge.' -ForegroundColor Yellow }
    $Expected = "http://127.0.0.1:$($State.port)/v1"
    if ($Base -eq $Expected) { Write-Host '[OK] Little LUMI points to the bridge.' -ForegroundColor Green }
    else { Write-Host "[FAIL] Expected Ollama base $Expected" -ForegroundColor Red; $Failures++ }
} else {
    Write-Host "[FAIL] Little LUMI AI config missing: $AiProperties" -ForegroundColor Red
    $Failures++
}

$Health = Get-BridgeHealth -InstallDir $InstallDir
if (Test-IsBridgeHealth $Health) {
    Write-Host "[OK] Bridge $($Health.version) is running." -ForegroundColor Green
    Write-Host "[INFO] Enabled tools: $($Health.tools -join ', ')"
} else {
    Write-Host '[FAIL] Bridge health endpoint is not responding.' -ForegroundColor Red
    $Failures++
}

if ((Test-Path $Jar) -and (Test-Path $Config)) {
    try {
        $Java = Get-BridgeJavaPath -InstallDir $InstallDir -Console
        & $Java --add-modules jdk.httpserver -jar $Jar --check --config $Config
        if ($LASTEXITCODE -ne 0) { $Failures++ }
    } catch {
        Write-Host "[FAIL] Java check: $($_.Exception.Message)" -ForegroundColor Red
        $Failures++
    }
}

if (Test-Path $Config) {
    $CodexEnabled = Get-PropertyValue -Path $Config -Key 'tools.codex.enabled' -Default 'false'
    if ($CodexEnabled -eq 'true') {
        $CodexCommand = Get-PropertyValue -Path $Config -Key 'tools.codex.command' -Default 'codex'
        $CodexWorkspace = Get-PropertyValue -Path $Config -Key 'tools.codex.workspace'
        Write-Host "[INFO] Codex workspace: $CodexWorkspace"
        $Codex = Get-Command $CodexCommand -ErrorAction SilentlyContinue
        if ($null -eq $Codex -and -not (Test-Path $CodexCommand)) {
            Write-Host "[FAIL] Codex command not found: $CodexCommand" -ForegroundColor Red
            $Failures++
        } else {
            Write-Host "[OK] Codex command found: $CodexCommand" -ForegroundColor Green
        }
        if (-not (Test-Path $CodexWorkspace -PathType Container)) {
            Write-Host '[FAIL] Codex workspace does not exist.' -ForegroundColor Red
            $Failures++
        }
    }
}

if ($Failures -eq 0) { Write-Host 'All checks passed.' -ForegroundColor Green }
else { Write-Host "$Failures check(s) failed." -ForegroundColor Red }
exit $Failures

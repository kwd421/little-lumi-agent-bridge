[CmdletBinding()]
param([string]$LittleLumiRoot = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Show-LaunchError {
    param([string]$Message)
    try {
        Add-Type -AssemblyName PresentationFramework -ErrorAction Stop
        [System.Windows.MessageBox]::Show($Message, 'Little LUMI Agent Manager', 'OK', 'Error') | Out-Null
    } catch {
        try {
            Add-Type -AssemblyName System.Windows.Forms -ErrorAction Stop
            [System.Windows.Forms.MessageBox]::Show($Message, 'Little LUMI Agent Manager') | Out-Null
        } catch {}
    }
}

function Quote-ProcessArgument {
    param([Parameter(Mandatory=$true)][string]$Value)
    return '"' + $Value.Replace('"', '\"') + '"'
}

function Find-LittleLumiRoot {
    param([string]$Requested)
    $Candidates = [Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($Requested)) { $Candidates.Add($Requested) }
    foreach ($Base in @([Environment]::GetEnvironmentVariable('ProgramFiles(x86)'), [Environment]::GetEnvironmentVariable('ProgramFiles'))) {
        if (-not [string]::IsNullOrWhiteSpace($Base)) { $Candidates.Add((Join-Path $Base 'Steam\steamapps\common\Little LUMI')) }
    }
    $SteamRoots = [Collections.Generic.List[string]]::new()
    foreach ($RegistryPath in @('HKCU:\Software\Valve\Steam','HKLM:\SOFTWARE\WOW6432Node\Valve\Steam','HKLM:\SOFTWARE\Valve\Steam')) {
        try {
            $Item = Get-ItemProperty $RegistryPath -ErrorAction Stop
            foreach ($Name in @('SteamPath','InstallPath')) {
                if ($null -ne $Item.$Name -and -not [string]::IsNullOrWhiteSpace([string]$Item.$Name)) { $SteamRoots.Add([string]$Item.$Name) }
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
        if ((Test-Path (Join-Path $Candidate 'Little LUMI.exe')) -and (Test-Path (Join-Path $Candidate 'app\jre\bin\java.exe'))) {
            return (Resolve-Path $Candidate).Path
        }
    }
    return $null
}

try {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    $Jar = Get-ChildItem (Join-Path $ProjectRoot 'dist') -Filter 'little-lumi-agent-bridge-*.jar' -File |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($null -eq $Jar) { throw 'Manager JAR not found under dist.' }

    $Detected = Find-LittleLumiRoot $LittleLumiRoot
    $JavaExe = $null
    if ($null -ne $Detected) {
        $Bundled = Join-Path $Detected 'app\jre\bin\java.exe'
        if (Test-Path $Bundled) { $JavaExe = $Bundled }
    }
    if ($null -eq $JavaExe) {
        $Found = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($null -ne $Found) { $JavaExe = $Found.Source }
    }
    if ($null -eq $JavaExe) {
        throw 'Little LUMI의 Java 런타임을 찾지 못했습니다. Little LUMI 설치 경로를 확인해 주세요.'
    }

    $Logs = Join-Path $env:LOCALAPPDATA 'LittleLumiAgentBridge\logs'
    New-Item $Logs -ItemType Directory -Force | Out-Null
    $StdOut = Join-Path $Logs 'manager.out.log'
    $StdErr = Join-Path $Logs 'manager.err.log'

    # Windows PowerShell 5.1 joins ArgumentList entries into one command line.
    # Explicitly quote every path so Program Files / Little LUMI paths stay intact.
    $ArgumentParts = @(
        '--add-modules',
        'jdk.httpserver',
        '-jar',
        (Quote-ProcessArgument $Jar.FullName),
        '--manager',
        '--release-root',
        (Quote-ProcessArgument $ProjectRoot)
    )
    if ($null -ne $Detected) {
        $ArgumentParts += @('--little-lumi-root', (Quote-ProcessArgument $Detected))
    }
    $ArgumentLine = [string]::Join(' ', $ArgumentParts)

    $Process = Start-Process -FilePath $JavaExe -ArgumentList $ArgumentLine -WorkingDirectory $ProjectRoot `
        -WindowStyle Hidden -RedirectStandardOutput $StdOut -RedirectStandardError $StdErr -PassThru

    Start-Sleep -Milliseconds 900
    $Process.Refresh()
    if ($Process.HasExited) {
        $Tail = ''
        if (Test-Path $StdErr) { $Tail = (Get-Content $StdErr -Tail 30 -ErrorAction SilentlyContinue) -join "`n" }
        if ([string]::IsNullOrWhiteSpace($Tail) -and (Test-Path $StdOut)) {
            $Tail = (Get-Content $StdOut -Tail 30 -ErrorAction SilentlyContinue) -join "`n"
        }
        if ([string]::IsNullOrWhiteSpace($Tail)) { $Tail = "Java exited with code $($Process.ExitCode)." }
        throw "GUI Manager 실행에 실패했습니다.`n`n$Tail`n`n로그: $StdErr"
    }
} catch {
    $Message = $_.Exception.Message
    try {
        $FallbackLog = Join-Path $env:TEMP 'little-lumi-agent-manager-launch.log'
        [IO.File]::WriteAllText($FallbackLog, ($_ | Out-String), [Text.UTF8Encoding]::new($false))
        $Message += "`n`n진단 로그: $FallbackLog"
    } catch {}
    Show-LaunchError $Message
    exit 1
}

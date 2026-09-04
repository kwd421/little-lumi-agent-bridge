[CmdletBinding()]
param([string]$LittleLumiRoot = '')
$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Jar = Get-ChildItem (Join-Path $ProjectRoot 'dist') -Filter 'little-lumi-agent-bridge-*.jar' -File |
    Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $Jar) { throw 'Manager JAR not found under dist.' }

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
        if ((Test-Path (Join-Path $Candidate 'Little LUMI.exe')) -and (Test-Path (Join-Path $Candidate 'app\jre\bin\javaw.exe'))) {
            return (Resolve-Path $Candidate).Path
        }
    }
    return $null
}

$Detected = Find-LittleLumiRoot $LittleLumiRoot
$Javaw = $null
if ($null -ne $Detected) {
    $Bundled = Join-Path $Detected 'app\jre\bin\javaw.exe'
    if (Test-Path $Bundled) { $Javaw = $Bundled }
}
if ($null -eq $Javaw) {
    $Found = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($null -ne $Found) { $Javaw = $Found.Source }
}
if ($null -eq $Javaw) {
    Add-Type -AssemblyName PresentationFramework
    [System.Windows.MessageBox]::Show('Little LUMI의 Java 런타임을 찾지 못했습니다. Little LUMI를 기본 Steam 경로에 설치했는지 확인해 주세요.', 'Little LUMI Agent Manager') | Out-Null
    exit 1
}
$Args = @('-jar', $Jar.FullName, '--manager', '--release-root', $ProjectRoot)
if ($null -ne $Detected) { $Args += @('--little-lumi-root', $Detected) }
Start-Process -FilePath $Javaw -ArgumentList $Args -WorkingDirectory $ProjectRoot

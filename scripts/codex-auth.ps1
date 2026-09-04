[CmdletBinding()]
param([switch]$Status)
$ErrorActionPreference = 'Stop'
$Codex = Get-Command codex -ErrorAction SilentlyContinue
if ($null -eq $Codex) { throw 'Codex CLI를 찾지 못했습니다. 먼저 공식 Codex CLI를 설치해 주세요.' }
if ($Status) {
    & $Codex.Source login status 2>&1 | ForEach-Object { Write-Output $_ }
    exit 0
}
& $Codex.Source login 2>&1 | ForEach-Object { Write-Output $_ }
exit $LASTEXITCODE

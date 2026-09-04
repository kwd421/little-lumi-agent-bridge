[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& (Join-Path $PSScriptRoot 'test.ps1')
$ConfigJava = Join-Path $Root 'src\main\java\io\github\kwd421\lumitoolbridge\Config.java'
$Version = [regex]::Match([IO.File]::ReadAllText($ConfigJava), 'VERSION\s*=\s*"([^"]+)"').Groups[1].Value
$Stage = Join-Path $Root "build\release\little-lumi-agent-bridge-$Version"
$Archive = Join-Path $Root "release\little-lumi-agent-bridge-v$Version.zip"
Remove-Item $Stage -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $Archive -Force -ErrorAction SilentlyContinue
New-Item (Join-Path $Stage 'dist') -ItemType Directory -Force | Out-Null
New-Item (Join-Path $Stage 'config') -ItemType Directory -Force | Out-Null
New-Item (Join-Path $Stage 'scripts') -ItemType Directory -Force | Out-Null
New-Item (Join-Path $Stage 'docs') -ItemType Directory -Force | Out-Null
New-Item (Split-Path $Archive -Parent) -ItemType Directory -Force | Out-Null
Copy-Item (Join-Path $Root "dist\little-lumi-agent-bridge-$Version.jar") (Join-Path $Stage 'dist')
Copy-Item (Join-Path $Root 'config\bridge.properties.example') (Join-Path $Stage 'config')
Copy-Item (Join-Path $Root 'config\mcp.example.json') (Join-Path $Stage 'config')
foreach ($File in @('README.md','LICENSE','NOTICE.md','SECURITY.md','CHANGELOG.md')) { Copy-Item (Join-Path $Root $File) $Stage }
Copy-Item (Join-Path $Root 'docs\*.md') (Join-Path $Stage 'docs')
foreach ($File in @('install.ps1','uninstall.ps1','start-bridge.ps1','stop-bridge.ps1','doctor.ps1','runtime-common.ps1','set-startup.ps1','launch-manager.ps1','codex-auth.ps1')) {
    Copy-Item (Join-Path $PSScriptRoot $File) (Join-Path $Stage 'scripts')
}
Copy-Item (Join-Path $Root 'Little LUMI Agent Manager.vbs') $Stage -Force
Compress-Archive -Path $Stage -DestinationPath $Archive -CompressionLevel Optimal
Write-Host "Packaged $Archive"

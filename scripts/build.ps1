[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ConfigJava = Join-Path $Root 'src\main\java\io\github\kwd421\lumitoolbridge\Config.java'
$VersionMatch = [regex]::Match([IO.File]::ReadAllText($ConfigJava), 'VERSION\s*=\s*"([^"]+)"')
if (-not $VersionMatch.Success) { throw 'Could not determine project version.' }
$Version = $VersionMatch.Groups[1].Value
$Classes = Join-Path $Root 'build\classes'
$Dist = Join-Path $Root 'dist'
Remove-Item $Classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item $Classes -ItemType Directory -Force | Out-Null
New-Item $Dist -ItemType Directory -Force | Out-Null
$Sources = Get-ChildItem (Join-Path $Root 'src\main\java') -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object FullName
$ArgFile = Join-Path $Root 'build\javac-main.args'
$Args = @('--release','17','--add-modules','jdk.httpserver','-Xlint:all','-encoding','UTF-8','-d',('"{0}"' -f $Classes))
$Args += $Sources | ForEach-Object { '"{0}"' -f $_ }
[IO.File]::WriteAllLines($ArgFile, $Args, [Text.UTF8Encoding]::new($false))
& javac "@$ArgFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }
$Manifest = Join-Path $Root 'build\MANIFEST.MF'
$ManifestText = "Manifest-Version: 1.0`nMain-Class: io.github.kwd421.lumitoolbridge.Main`nImplementation-Title: Little LUMI Agent Bridge`nImplementation-Version: $Version`n`n"
[IO.File]::WriteAllText($Manifest, $ManifestText, [Text.UTF8Encoding]::new($false))
$Jar = Join-Path $Dist "little-lumi-agent-bridge-$Version.jar"
& jar --create --file $Jar --manifest $Manifest -C $Classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }
Write-Host "Built $Jar"

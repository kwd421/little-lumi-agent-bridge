[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& (Join-Path $PSScriptRoot 'build.ps1')
$TestClasses = Join-Path $Root 'build\test-classes'
Remove-Item $TestClasses -Recurse -Force -ErrorAction SilentlyContinue
New-Item $TestClasses -ItemType Directory -Force | Out-Null
$Sources = Get-ChildItem (Join-Path $Root 'src\test\java') -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object FullName
$ArgFile = Join-Path $Root 'build\javac-test.args'
$Args = @('--release','17','--add-modules','jdk.httpserver','-Xlint:all','-encoding','UTF-8','-cp',('"{0}"' -f (Join-Path $Root 'build\classes')),'-d',('"{0}"' -f $TestClasses))
$Args += $Sources | ForEach-Object { '"{0}"' -f $_ }
[IO.File]::WriteAllLines($ArgFile, $Args, [Text.UTF8Encoding]::new($false))
& javac "@$ArgFile"
if ($LASTEXITCODE -ne 0) { throw "test javac failed with exit code $LASTEXITCODE" }
$Separator = [IO.Path]::PathSeparator
$ClassPath = (Join-Path $Root 'build\classes') + $Separator + $TestClasses
& java --add-modules jdk.httpserver -cp $ClassPath io.github.kwd421.lumitoolbridge.AllTests
if ($LASTEXITCODE -ne 0) { throw "tests failed with exit code $LASTEXITCODE" }
& java --add-modules jdk.httpserver -cp $ClassPath io.github.kwd421.lumitoolbridge.manager.ManagerTests
if ($LASTEXITCODE -ne 0) { throw "manager tests failed with exit code $LASTEXITCODE" }

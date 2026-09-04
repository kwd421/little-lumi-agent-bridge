#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/scripts/test.sh"
VERSION="$(grep -oE 'VERSION = "[^"]+"' "$ROOT/src/main/java/io/github/kwd421/lumitoolbridge/Config.java" | head -1 | cut -d'"' -f2)"
STAGE="$ROOT/build/release/little-lumi-agent-bridge-$VERSION"
ARCHIVE="$ROOT/release/little-lumi-agent-bridge-v$VERSION.zip"
rm -rf "$STAGE" "$ARCHIVE"
mkdir -p "$STAGE/dist" "$STAGE/config" "$STAGE/scripts" "$STAGE/docs" "$ROOT/release"
cp "$ROOT/dist/little-lumi-agent-bridge-$VERSION.jar" "$STAGE/dist/"
cp "$ROOT/config/bridge.properties.example" "$ROOT/config/mcp.example.json" "$STAGE/config/"
cp "$ROOT"/{README.md,LICENSE,NOTICE.md,SECURITY.md,CHANGELOG.md} "$STAGE/"
cp "$ROOT/docs"/*.md "$STAGE/docs/"
cp "$ROOT/scripts"/{install.ps1,uninstall.ps1,start-bridge.ps1,stop-bridge.ps1,doctor.ps1,runtime-common.ps1,set-startup.ps1,launch-manager.ps1,codex-auth.ps1} "$STAGE/scripts/"
cp "$ROOT/Little LUMI Agent Manager.vbs" "$STAGE/"
(cd "$(dirname "$STAGE")" && zip -qr "$ARCHIVE" "$(basename "$STAGE")")
printf 'Packaged %s\n' "$ARCHIVE"

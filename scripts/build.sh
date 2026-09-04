#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(grep -oE 'VERSION = "[^"]+"' "$ROOT/src/main/java/io/github/kwd421/lumitoolbridge/Config.java" | head -1 | cut -d'"' -f2)"
CLASSES="$ROOT/build/classes"
DIST="$ROOT/dist"
rm -rf "$CLASSES"
mkdir -p "$CLASSES" "$DIST"
mapfile -t SOURCES < <(find "$ROOT/src/main/java" -name '*.java' -type f | sort)
javac --release 17 --add-modules jdk.httpserver -Xlint:all -encoding UTF-8 -d "$CLASSES" "${SOURCES[@]}"
MANIFEST="$ROOT/build/MANIFEST.MF"
printf 'Manifest-Version: 1.0\nMain-Class: io.github.kwd421.lumitoolbridge.Main\nImplementation-Title: Little LUMI Agent Bridge\nImplementation-Version: %s\n\n' "$VERSION" > "$MANIFEST"
JAR="$DIST/little-lumi-agent-bridge-$VERSION.jar"
jar --create --file "$JAR" --manifest "$MANIFEST" -C "$CLASSES" .
printf 'Built %s\n' "$JAR"

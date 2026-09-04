#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/scripts/build.sh"
TEST_CLASSES="$ROOT/build/test-classes"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"
mapfile -t TEST_SOURCES < <(find "$ROOT/src/test/java" -name '*.java' -type f | sort)
javac --release 17 --add-modules jdk.httpserver -Xlint:all -encoding UTF-8 \
  -cp "$ROOT/build/classes" -d "$TEST_CLASSES" "${TEST_SOURCES[@]}"
java --add-modules jdk.httpserver -cp "$ROOT/build/classes:$TEST_CLASSES" \
  io.github.kwd421.lumitoolbridge.AllTests

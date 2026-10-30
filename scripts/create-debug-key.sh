#!/usr/bin/env bash
set -euo pipefail
JARVIS_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$JARVIS_ROOT/signing"
if [ ! -f "$JARVIS_ROOT/signing/debug.keystore" ]; then
  keytool -genkeypair -keystore "$JARVIS_ROOT/signing/debug.keystore" \
    -storepass android -keypass android -alias androiddebugkey -keyalg RSA \
    -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Jarvis Development,C=IT'
fi
keytool -list -v -keystore "$JARVIS_ROOT/signing/debug.keystore" -storepass android -alias androiddebugkey

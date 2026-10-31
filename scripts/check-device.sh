#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-reports
trap 'adb logcat -d > device-reports/logcat.txt 2>/dev/null || true' EXIT
apk=artifacts/app/build/outputs/apk/debug/app-debug.apk
tests=artifacts/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb install artifacts/compatibility-fixtures/upgrade-from.apk
adb install "$tests"
# Real framework tests: wheel/theme, neon setting, local/background and offline Drive playback.
timeout 360s adb shell am instrument -w it.jarvis.ipod.test/androidx.test.runner.AndroidJUnitRunner | tee device-reports/instrumentation.txt
grep -q 'OK (3 tests)' device-reports/instrumentation.txt
# The app is debuggable: seed a sentinel in its private sandbox and check it after install -r.
adb shell am force-stop it.jarvis.ipod
adb shell run-as it.jarvis.ipod sh -c "'echo retained > files/update-sentinel'"
adb shell run-as it.jarvis.ipod cat files/datastore/jarvis.preferences_pb > device-reports/preferences-before.pb
adb shell run-as it.jarvis.ipod cat databases/library.db > device-reports/library-before.db
adb install -r "$apk" | tee device-reports/update-install.txt
adb shell run-as it.jarvis.ipod cat files/update-sentinel | tee device-reports/update-sentinel.txt
grep -q retained device-reports/update-sentinel.txt
adb shell run-as it.jarvis.ipod cat files/datastore/jarvis.preferences_pb > device-reports/preferences-after.pb
cmp device-reports/preferences-before.pb device-reports/preferences-after.pb
adb shell run-as it.jarvis.ipod cat databases/library.db > device-reports/library-after.db
cmp device-reports/library-before.db device-reports/library-after.db

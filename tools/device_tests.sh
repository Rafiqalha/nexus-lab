#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_SERIAL:?Set explicitly to the authorized adb device}"
adb -s "$ANDROID_SERIAL" get-state
if adb -s "$ANDROID_SERIAL" shell dumpsys window policy | grep -q "^[[:space:]]*showing=true"; then echo "Unlock phone before testing" >&2; exit 1; fi
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/release/app-release.apk
adb -s "$ANDROID_SERIAL" install -r -t app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb -s "$ANDROID_SERIAL" shell pm grant org.nexus.lab android.permission.CAMERA
# Runner output contains counters/measurements/identity only. No screenshots or frame logs.
# Check instrumentation markers as `am instrument` can exit 0 on a test failure.
mkdir -p build
python3 tools/run_instrumentation.py

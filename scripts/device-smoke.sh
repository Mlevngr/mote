#!/usr/bin/env bash
# Run only on a disposable emulator: this suite creates notes in the target application's vault.
set -euo pipefail
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to a disposable emulator serial}"
[[ "$ANDROID_SERIAL" == emulator-* ]] || { echo 'Refusing to modify a non-emulator device'; exit 1; }
adb="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}/platform-tools/adb"
log="${SMOKE_LOG:-build/editor-smoke.log}"
if [[ "${1:-}" != --skip-build ]]; then
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
fi
mkdir -p "$(dirname "$log")"
booted=false
for _ in $(seq 1 60); do
  if [[ "$(timeout 10 "$adb" -s "$ANDROID_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
    booted=true
    break
  fi
  sleep 5
done
$booted || { echo 'Emulator did not finish booting'; exit 1; }
"$adb" -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_WAKEUP
"$adb" -s "$ANDROID_SERIAL" shell wm size 540x1200
"$adb" -s "$ANDROID_SERIAL" shell wm density 210
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$adb" -s "$ANDROID_SERIAL" shell settings put global "$setting" 0
done
"$adb" -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb" -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Raw output preserves result fields even when the runner provides a human-readable stream.
# am instrument may return exit code 0 for failed tests; require explicit success markers.
timeout 600 "$adb" -s "$ANDROID_SERIAL" shell am instrument -w -r \
  com.mlevngr.inknote.test/com.mlevngr.inknote.EditorSmokeInstrumentation | tee "$log"
grep -q '^INSTRUMENTATION_RESULT: failures=0' "$log"
grep -q '^INSTRUMENTATION_CODE: -1' "$log"

#!/usr/bin/env bash
# Fresh disposable emulator only. Tests the minified release without linking app classes.
set -euo pipefail
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to a disposable emulator}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
[[ "$ANDROID_SERIAL" == emulator-* ]] || { echo 'Emulator required'; exit 1; }
test_output=app/build/release-heading-test
test_tools="$ANDROID_HOME/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
test_platform="$ANDROID_HOME/platforms/android-36/android.jar"
test_adb="$ANDROID_HOME/platform-tools/adb"
mkdir -p "$test_output/classes" "$test_output/dex"
cat > "$test_output/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.mlevngr.inknote.releasecheck">
    <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="36" />
    <application android:label="Mote release heading test" />
    <instrumentation android:name="com.mlevngr.inknote.ReleaseHeadingSmokeInstrumentation" android:targetPackage="com.mlevngr.inknote" />
</manifest>
XML
"$JAVA_HOME/bin/javac" -source 8 -target 8 -classpath "$test_platform" -d "$test_output/classes" scripts/tests/ReleaseHeadingSmokeInstrumentation.java
mapfile -t test_classes < <(find "$test_output/classes" -name '*.class')
"$JAVA_HOME/bin/java" -cp "$test_tools/lib/d8.jar" com.android.tools.r8.D8 --lib "$test_platform" --min-api 28 --output "$test_output/dex" "${test_classes[@]}"
"$test_tools/aapt2" link --manifest "$test_output/AndroidManifest.xml" -I "$test_platform" -o "$test_output/unsigned.apk"
python3 - "$test_output" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import sys
output = Path(sys.argv[1])
with ZipFile(output / 'unsigned.apk', 'a') as apk:
    apk.write(output / 'dex/classes.dex', 'classes.dex')
    apk.write('docs/skins/warm-paper.json', 'assets/skin.json')
PY
"$test_tools/apksigner" sign --ks signing/inknote-dev.jks --ks-key-alias inknote --ks-pass pass:inknote-dev-v1 --key-pass pass:inknote-dev-v1 --out "$test_output/test.apk" "$test_output/unsigned.apk"
"$test_adb" -s "$ANDROID_SERIAL" shell cmd uimode night no
"$test_adb" -s "$ANDROID_SERIAL" install -r "${1:-app/build/outputs/apk/release/app-release.apk}"
"$test_adb" -s "$ANDROID_SERIAL" install -r "$test_output/test.apk"
"$test_adb" -s "$ANDROID_SERIAL" shell am instrument -w -r com.mlevngr.inknote.releasecheck/com.mlevngr.inknote.ReleaseHeadingSmokeInstrumentation | tee "$test_output/result.log"
grep -q '^INSTRUMENTATION_RESULT: failures=0' "$test_output/result.log"
grep -q '^INSTRUMENTATION_CODE: -1' "$test_output/result.log"

#!/bin/bash
set -e

# Detect Java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
elif [ -x "/opt/homebrew/opt/openjdk/bin/javac" ]; then
    JAVAC="/opt/homebrew/opt/openjdk/bin/javac"
    JAR="/opt/homebrew/opt/openjdk/bin/jar"
else
    JAVAC="$(which javac)"
    JAR="$(which jar)"
fi

# Detect Android SDK & D8
ANDROID_SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
D8="$(find "$ANDROID_SDK/build-tools" -name d8 -type f 2>/dev/null | sort -V | tail -n 1)"
ANDROID_JAR="$(find "$ANDROID_SDK/platforms" -name android.jar -type f 2>/dev/null | sort -V | tail -n 1)"

if [ -z "$D8" ] || [ -z "$ANDROID_JAR" ]; then
    echo "Error: Android SDK build-tools (d8) or android.jar not found in $ANDROID_SDK"
    exit 1
fi

echo "Using Java: $JAVAC"
echo "Using d8: $D8"
echo "Using android.jar: $ANDROID_JAR"

# Paths
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPS_DIR="$ROOT_DIR/build_deps"
BUILD_DIR="/tmp/hushgram_local_build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/shared" "$BUILD_DIR/instagram" "$BUILD_DIR/dex/shared" "$BUILD_DIR/dex/instagram" "$BUILD_DIR/generated_notice"

echo "=== 1. Compiling extensions/shared ==="
find "$ROOT_DIR/extensions/shared" -name "*.java" | sed -e 's/^/"/' -e 's/$/"/' > "$BUILD_DIR/shared_sources.txt"
"$JAVAC" --release 17 \
  -cp "$ANDROID_JAR:$DEPS_DIR/libs/annotation-jvm-1.8.0.jar" \
  -d "$BUILD_DIR/shared" \
  @"$BUILD_DIR/shared_sources.txt"

"$JAR" -cf "$BUILD_DIR/shared.jar" -C "$BUILD_DIR/shared" .

echo "=== 2. Generating LicenseNotice.java from NOTICE ==="
python3 -c "
import os
notice_file = '$ROOT_DIR/NOTICE'
with open(notice_file, 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

literal = ''
for ch in text:
    if ch == '\\\\': literal += '\\\\\\\\'
    elif ch == '\"': literal += '\\\\\"'
    elif ch == '\n': literal += '\\\\n'
    elif ord(ch) < 0x20 or ord(ch) > 0x7e: literal += f'\\\\u{ord(ch):04x}'
    else: literal += ch

out_dir = '$BUILD_DIR/generated_notice/app/hushgram/extension/instagram/settings'
os.makedirs(out_dir, exist_ok=True)
with open(os.path.join(out_dir, 'LicenseNotice.java'), 'w', encoding='utf-8') as f:
    f.write(f'package app.hushgram.extension.instagram.settings;\n\npublic final class LicenseNotice {{\n    public static final String TEXT = \"{literal}\";\n    private LicenseNotice() {{}}\n}}\n')
"

echo "=== 3. Compiling extensions/instagram ==="
find "$ROOT_DIR/extensions/instagram/src/main/java" "$BUILD_DIR/generated_notice" -name "*.java" | sed -e 's/^/"/' -e 's/$/"/' > "$BUILD_DIR/instagram_sources.txt"
KOTLIN_STDLIB="$DEPS_DIR/libs/kotlin-stdlib.jar"
if [ ! -f "$KOTLIN_STDLIB" ]; then
    KOTLIN_STDLIB="$(find /opt/homebrew/Cellar/kotlin -name "kotlin-stdlib.jar" 2>/dev/null | sort -V | tail -n 1)"
fi
"$JAVAC" --release 17 \
  -cp "$ANDROID_JAR:$DEPS_DIR/libs/annotation-jvm-1.8.0.jar:$KOTLIN_STDLIB:$BUILD_DIR/shared.jar" \
  -d "$BUILD_DIR/instagram" \
  @"$BUILD_DIR/instagram_sources.txt"

echo "=== 4. Dexing extensions with d8 ==="
"$D8" --min-api 28 --output "$BUILD_DIR/dex/shared" $(find "$BUILD_DIR/shared" -name "*.class")
"$D8" --min-api 28 --classpath "$ANDROID_JAR" --classpath "$BUILD_DIR/shared.jar" \
  --output "$BUILD_DIR/dex/instagram" $(find "$BUILD_DIR/instagram" -name "*.class")

echo "=== 5. Packaging patches-0.0.18.mpp ==="
python3 -c "
import os, shutil, zipfile

staging = '$BUILD_DIR/staging'
if os.path.exists(staging):
    shutil.rmtree(staging)
os.makedirs(staging)

with zipfile.ZipFile('$DEPS_DIR/libs/gson-2.15.0.jar', 'r') as z:
    z.extractall(staging)

shutil.copytree('$DEPS_DIR/patches_base/app', os.path.join(staging, 'app'), dirs_exist_ok=True)
shutil.copy2('$DEPS_DIR/patches_base/META-INF/app.morphe_patches.kotlin_module', os.path.join(staging, 'META-INF', 'app.morphe_patches.kotlin_module'))
shutil.copy2('$DEPS_DIR/dex/classes.dex', os.path.join(staging, 'classes.dex'))

os.makedirs(os.path.join(staging, 'extensions'), exist_ok=True)
shutil.copy2('$BUILD_DIR/dex/instagram/classes.dex', os.path.join(staging, 'extensions', 'instagram.mpe'))
shutil.copy2('$BUILD_DIR/dex/shared/classes.dex', os.path.join(staging, 'extensions', 'shared.mpe'))

res_dir = '$ROOT_DIR/patches/src/main/resources'
for root, dirs, files in os.walk(res_dir):
    for f in files:
        src_path = os.path.join(root, f)
        rel = os.path.relpath(src_path, res_dir)
        dest_path = os.path.join(staging, rel)
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        shutil.copy2(src_path, dest_path)

manifest = '''Manifest-Version: 1.0
Name: Instagram Plus
Description: Instagram Plus patches for the Instagram app, built for Morphe. Hides ads, cleans tracking keys off the links you share, keeps usage events on your phone, and keeps a patched build from locking you out.
Version: 0.0.18
Timestamp: 1793484620000
Source: https://github.com/Srijal-SS/Instagram-Plus
Author: Srijal-SS
Contact: https://github.com/Srijal-SS/Instagram-Plus/issues
Website: https://github.com/Srijal-SS/Instagram-Plus
License: GNU General Public License v3.0, with additional GPL section 7 requirements
Patcher-Version: 2.1.0

'''
with open(os.path.join(staging, 'META-INF', 'MANIFEST.MF'), 'w', newline='\n') as f:
    f.write(manifest)

out_mpp = '$ROOT_DIR/patches-0.0.18.mpp'
with zipfile.ZipFile(out_mpp, 'w', compression=zipfile.ZIP_DEFLATED) as z:
    manifest_path = os.path.join(staging, 'META-INF', 'MANIFEST.MF')
    z.write(manifest_path, 'META-INF/MANIFEST.MF')
    for root, dirs, files in os.walk(staging):
        dirs.sort()
        files.sort()
        for f in files:
            p = os.path.join(root, f)
            arcname = os.path.relpath(p, staging)
            if arcname == 'META-INF/MANIFEST.MF':
                continue
            z.write(p, arcname)

for d in ['$ROOT_DIR/patches/build/release', '$ROOT_DIR/patches/build/libs']:
    os.makedirs(d, exist_ok=True)
    shutil.copy2(out_mpp, os.path.join(d, 'patches-0.0.18.mpp'))

print('Successfully built patches-0.0.18.mpp (' + str(os.path.getsize(out_mpp)) + ' bytes)!')
"


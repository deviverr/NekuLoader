#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
export COPYFILE_DISABLE=1

BUILD_DIR="build"
CLASSES_DIR="$BUILD_DIR/classes"
JAR_FILE="$BUILD_DIR/NekuLoader-3.1.0.jar"
MANIFEST_FILE="$BUILD_DIR/MANIFEST.MF"
LIB_DIR="lib"

FLATLAF_VERSION="3.7.2"
JSON_VERSION="20260522"
JLAYER_VERSION="1.0.1"

download_if_missing() {
  local url="$1"
  local dest="$2"

  if [ ! -f "$dest" ]; then
    echo "Downloading $(basename "$dest")"
    curl -fL "$url" -o "$dest"
  fi
}

rm -rf "$BUILD_DIR"
find . -name '._*' -type f -delete
mkdir -p "$CLASSES_DIR" "$LIB_DIR"

download_if_missing \
  "https://repo1.maven.org/maven2/com/formdev/flatlaf/$FLATLAF_VERSION/flatlaf-$FLATLAF_VERSION.jar" \
  "$LIB_DIR/flatlaf-$FLATLAF_VERSION.jar"
download_if_missing \
  "https://repo1.maven.org/maven2/org/json/json/$JSON_VERSION/json-$JSON_VERSION.jar" \
  "$LIB_DIR/json-$JSON_VERSION.jar"
download_if_missing \
  "https://repo1.maven.org/maven2/javazoom/jlayer/$JLAYER_VERSION/jlayer-$JLAYER_VERSION.jar" \
  "$LIB_DIR/jlayer-$JLAYER_VERSION.jar"

find "$LIB_DIR" -name 'flatlaf-*.jar' ! -name "flatlaf-$FLATLAF_VERSION.jar" -delete
find "$LIB_DIR" -name 'json-*.jar' ! -name "json-$JSON_VERSION.jar" -delete
find "$LIB_DIR" -name 'jlayer-*.jar' ! -name "jlayer-$JLAYER_VERSION.jar" -delete

COMPILE_CP="$LIB_DIR/flatlaf-$FLATLAF_VERSION.jar:$LIB_DIR/json-$JSON_VERSION.jar:$LIB_DIR/jlayer-$JLAYER_VERSION.jar"

find src/main/java -type f -name '*.java' ! -name '._*' -print0 \
  | xargs -0 javac -encoding UTF-8 -cp "$COMPILE_CP" -d "$CLASSES_DIR"

if [ -d src/main/resources ]; then
  cp -R src/main/resources/. "$CLASSES_DIR/"
fi

find "$CLASSES_DIR" -name '._*' -delete

cat > "$MANIFEST_FILE" <<EOF
Manifest-Version: 1.0
Main-Class: com.musicdownloader.MusicDownloaderApp
Class-Path: ../lib/flatlaf-$FLATLAF_VERSION.jar ../lib/json-$JSON_VERSION.jar ../lib/jlayer-$JLAYER_VERSION.jar

EOF

jar cfm "$JAR_FILE" "$MANIFEST_FILE" -C "$CLASSES_DIR" .
find . -name '._*' -type f -delete

echo "Built $JAR_FILE"

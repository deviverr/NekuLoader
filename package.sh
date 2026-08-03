#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
export COPYFILE_DISABLE=1

APP_NAME="NekuLoader"
APP_VERSION="3.1.0"
MAIN_JAR="NekuLoader-3.1.0.jar"
MAIN_CLASS="com.musicdownloader.MusicDownloaderApp"
PACKAGE_TYPE="${1:-app-image}"
INPUT_DIR="build/package-input"
DIST_DIR="dist"

find_jpackage() {
  if [ -n "${JPACKAGE:-}" ] && [ -x "$JPACKAGE" ]; then
    printf '%s\n' "$JPACKAGE"
    return
  fi
  if [ -x "/opt/homebrew/opt/openjdk@21/bin/jpackage" ]; then
    printf '%s\n' "/opt/homebrew/opt/openjdk@21/bin/jpackage"
    return
  fi
  if command -v jpackage >/dev/null 2>&1 && jpackage --version >/dev/null 2>&1; then
    command -v jpackage
    return
  fi
  echo "A full JDK 21 with jpackage is required." >&2
  exit 1
}

case "$PACKAGE_TYPE" in
  app-image) ;;
  dmg)
    [ "$(uname -s)" = "Darwin" ] || { echo "DMG packages must be built on macOS." >&2; exit 1; }
    ;;
  exe|msi)
    [ "${OS:-}" = "Windows_NT" ] || { echo "Windows packages must be built on Windows." >&2; exit 1; }
    ;;
  deb|rpm)
    [ "$(uname -s)" = "Linux" ] || { echo "Linux packages must be built on Linux." >&2; exit 1; }
    ;;
  *)
    echo "Usage: ./package.sh [app-image|dmg|exe|msi|deb|rpm]" >&2
    exit 2
    ;;
esac

JPACKAGE_BIN="$(find_jpackage)"

./build.sh
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR" "$DIST_DIR"
rm -f "$DIST_DIR/$APP_NAME-$APP_VERSION.$PACKAGE_TYPE"
cp "build/$MAIN_JAR" "$INPUT_DIR/"
cp lib/*.jar "$INPUT_DIR/"
find "$INPUT_DIR" -name '._*' -type f -delete

ARGS=(
  --type "$PACKAGE_TYPE"
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --vendor "deviver.art"
  --description "Desktop music download manager and local library player"
  --input "$INPUT_DIR"
  --main-jar "$MAIN_JAR"
  --main-class "$MAIN_CLASS"
  --dest "$DIST_DIR"
  --java-options "-Dfile.encoding=UTF-8"
)

if [ "$(uname -s)" = "Darwin" ]; then
  ARGS+=(--mac-package-identifier "art.deviver.nekuloader")
  if [ -f "installer/icon.icns" ]; then
    ARGS+=(--icon "installer/icon.icns")
  fi
elif [ "${OS:-}" = "Windows_NT" ]; then
  ARGS+=(--win-menu --win-shortcut --win-dir-chooser)
  if [ -f "installer/icon.ico" ]; then
    ARGS+=(--icon "installer/icon.ico")
  fi
fi

"$JPACKAGE_BIN" "${ARGS[@]}"
echo "Created $PACKAGE_TYPE package in $DIST_DIR"

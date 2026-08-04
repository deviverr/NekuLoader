#!/usr/bin/env bash
set -euo pipefail

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required. Install Python 3 first."
  exit 1
fi

python3 -m pip install --upgrade yt-dlp spotdl

if command -v brew >/dev/null 2>&1; then
  brew install ffmpeg deno
else
  echo "Homebrew not found. Install ffmpeg manually: https://ffmpeg.org/"
  echo "Install Deno or Node.js too; current yt-dlp releases use a JavaScript runtime for YouTube extraction."
fi

echo "Optional Android/iOS tools:"
echo "  Android: adb from Android Platform Tools"
echo "  iOS: brew install libimobiledevice"

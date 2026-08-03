#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -d build/classes ]; then
  ./build.sh
fi

java -cp "build/classes:lib/*" com.nekuloader.packager.PackagerApp

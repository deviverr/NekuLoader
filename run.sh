#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f build/NekuLoader-3.1.0.jar ]; then
  ./build.sh
fi

java -jar build/NekuLoader-3.1.0.jar

#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "${FAKE_ADB_LOG:?}"

if [ "${1:-}" = "version" ]; then
  echo "Android Debug Bridge version 1.0.41"
  exit 0
fi

if [ "${1:-}" = "devices" ]; then
  echo "List of devices attached"
  printf 'test-device\tdevice\n'
  exit 0
fi

if [ "${1:-}" = "-s" ] && [ "${3:-}" = "shell" ] && [ "${4:-}" = "getprop" ]; then
  echo "Neku Test Phone"
  exit 0
fi

if [ "${1:-}" = "-s" ] && [ "${3:-}" = "push" ]; then
  echo "1 file pushed"
  exit 0
fi

exit 0

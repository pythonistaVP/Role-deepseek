#!/usr/bin/env bash
# Role DeepSeek — сборка APK одной командой (macOS / Linux)
# Двойной клик или:  ./build_apk.sh   (--release / --install тоже работают)
set -e
cd "$(dirname "$0")"

if command -v python3 >/dev/null 2>&1; then
  PY=python3
elif command -v python >/dev/null 2>&1; then
  PY=python
else
  echo "Python не найден. Установите Python 3 с https://www.python.org/downloads/"
  exit 1
fi

"$PY" build_apk.py "$@"

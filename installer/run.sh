#!/usr/bin/env bash
# Downloads the latest Micro Hub checkout and opens the Linux setup window.
set -euo pipefail

if ! command -v python3 >/dev/null 2>&1; then
  echo "Install python3, then run this again." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "Install curl, then run this again." >&2
  exit 1
fi

parent="${TMPDIR:-/tmp}/micro-hub"
zip="$parent/micro-hub.zip"
uri='https://github.com/Opra-Dev-Team/Micro-Hub/archive/refs/heads/dev.zip'

mkdir -p "$parent"
find "$parent" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
mkdir -p "$parent"
dest="$parent/$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
mkdir -p "$dest"

curl -fsSL "$uri" -o "$zip"
python3 - "$zip" "$dest" << 'PY'
import sys
import zipfile
from pathlib import Path

zip_path, dest = sys.argv[1], Path(sys.argv[2])
with zipfile.ZipFile(zip_path) as archive:
    for info in archive.infolist():
        parts = Path(info.filename).parts
        if not info.filename or info.filename.startswith("/") or ".." in parts:
            raise SystemExit(f"Unsafe path in zip: {info.filename}")
    archive.extractall(dest)
PY

root=""
for dir in "$dest"/*/; do
  if [[ -d "$dir" ]]; then
    root="${dir%/}"
    break
  fi
done
if [[ -z "$root" ]]; then
  echo "Could not unpack Micro Hub." >&2
  exit 1
fi

setup="$root/installer/micro-hub-setup.py"
if [[ ! -f "$setup" ]]; then
  echo "Missing setup file: $setup" >&2
  exit 1
fi

# shellcheck disable=SC1091
source "$root/installer/ensure-tk.sh"
exec python3 "$setup"

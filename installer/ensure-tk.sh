#!/usr/bin/env bash
# Shared by the Linux launchers. Installs python3-tk when the import is missing.

ensure_micro_hub_tk() {
  if ! command -v python3 >/dev/null 2>&1; then
    echo "Install python3, then run this again." >&2
    exit 1
  fi

  if python3 -c 'import tkinter' >/dev/null 2>&1; then
    return 0
  fi

  echo "python3-tk is required for the Micro Hub setup window." >&2

  if command -v apt-get >/dev/null 2>&1; then
    sudo DEBIAN_FRONTEND=noninteractive apt-get update
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y python3-tk
  elif command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y python3-tkinter
  elif command -v pacman >/dev/null 2>&1; then
    sudo pacman -Sy --noconfirm tk
  else
    echo "Install python3-tk, then run this again." >&2
    exit 1
  fi

  python3 -c 'import tkinter'
}

ensure_micro_hub_tk

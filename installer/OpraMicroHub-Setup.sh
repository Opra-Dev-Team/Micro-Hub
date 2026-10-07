#!/usr/bin/env bash
# Opens the setup window from a local checkout.
set -euo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"
cd "$dir"
# shellcheck disable=SC1091
source "$dir/ensure-tk.sh"
exec python3 "$dir/micro-hub-setup.py"

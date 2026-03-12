#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_SRC="$ROOT_DIR/../MasterDnsVPN"
PY_SRC="${MASTERDNSVPN_SOURCE_DIR:-$DEFAULT_SRC}"
PY_DST="$ROOT_DIR/app/src/main/python"

if [[ ! -d "$PY_SRC" ]]; then
  echo "MasterDnsVPN source directory not found: $PY_SRC" >&2
  echo "Set MASTERDNSVPN_SOURCE_DIR=/path/to/MasterDnsVPN before running this script." >&2
  exit 1
fi

mkdir -p "$PY_DST/dns_utils"
cp "$PY_SRC/client.py" "$PY_DST/client.py"
cp -R "$PY_SRC/dns_utils/." "$PY_DST/dns_utils/"
find "$PY_DST" -type d -name "__pycache__" -exec rm -r {} + || true
find "$PY_DST" -type f -name "*.pyc" -delete || true

echo "Synced MasterDnsVPN Python core from $PY_SRC into $PY_DST"

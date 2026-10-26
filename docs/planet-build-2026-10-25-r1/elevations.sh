#!/bin/bash
set -euo pipefail
BUCKET=s3://outmap-planet-pilot-442073793062-20261025
PREFIX=planet-20261025-r1
mkdir -p /opt/planet/elevations
cd /opt/planet/elevations
exec > >(tee -a elevations.log) 2>&1
checkpoint() {
  if [ -f elevations.jsonl.partial ]; then
    python3 - <<'PY'
from pathlib import Path
p=Path('elevations.jsonl.partial'); data=p.read_bytes(); end=data.rfind(b'\n')
if end>=0: Path('checkpoint.partial').write_bytes(data[:end+1])
PY
    [ ! -f checkpoint.partial ] || aws s3 cp checkpoint.partial "$BUCKET/$PREFIX/checkpoints/elevations.partial" --only-show-errors || true
  fi
  aws s3 cp elevations.log "$BUCKET/$PREFIX/logs/elevations.log" --only-show-errors || true
}
finish() {
  code=$?
  trap - EXIT
  kill "$UPLOADER" 2>/dev/null || true
  checkpoint
  printf '{"exitCode":%s,"finishedAt":"%s"}\n' "$code" "$(date -u +%FT%TZ)" > completion.json
  aws s3 cp completion.json "$BUCKET/$PREFIX/status/elevations.json" --only-show-errors || true
  shutdown -h now
  exit "$code"
}
(while sleep 60; do checkpoint; done) &
UPLOADER=$!
trap finish EXIT
aws s3 cp "$BUCKET/prepared/global-peaks.geojsonseq" global-peaks.geojsonseq --only-show-errors
sha256sum global-peaks.geojsonseq > input.sha256
aws s3 cp "$BUCKET/$PREFIX/bundle/backfill-peak-elevations.mts" backfill-peak-elevations.mts --only-show-errors
node backfill-peak-elevations.mts --input=global-peaks.geojsonseq --snapshot=elevations.jsonl --batch-size=250
sha256sum elevations.jsonl > elevations.sha256
aws s3 cp elevations.jsonl "$BUCKET/$PREFIX/prepared/elevations.jsonl" --only-show-errors
aws s3 cp elevations.sha256 "$BUCKET/$PREFIX/prepared/elevations.sha256" --only-show-errors
aws s3 cp input.sha256 "$BUCKET/$PREFIX/prepared/elevation-input.sha256" --only-show-errors

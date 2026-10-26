#!/bin/bash
set -euo pipefail
BUCKET=s3://outmap-planet-pilot-442073793062-20261025
PREFIX=planet-20261025-r1
cd /opt/planet
exec > >(tee -a prepare-trails.log) 2>&1
finish() {
  code=$?
  trap - EXIT
  kill "$UPLOADER" 2>/dev/null || true
  aws s3 cp prepare-trails.log "$BUCKET/$PREFIX/logs/prepare-trails.log" --only-show-errors || true
  [ ! -f prepare-time.txt ] || aws s3 cp prepare-time.txt "$BUCKET/$PREFIX/logs/prepare-time.txt" --only-show-errors || true
  printf '{"exitCode":%s,"finishedAt":"%s"}\n' "$code" "$(date -u +%FT%TZ)" > prepare-completion.json
  aws s3 cp prepare-completion.json "$BUCKET/$PREFIX/status/trails-v2.json" --only-show-errors || true
  shutdown -h now
  exit "$code"
}
(while sleep 30; do aws s3 cp prepare-trails.log "$BUCKET/$PREFIX/logs/prepare-trails.log" --only-show-errors || true; done) &
UPLOADER=$!
trap finish EXIT
[ "$(awk '/MemTotal/ {print $2}' /proc/meminfo)" -gt 500000000 ]
DISK=$(lsblk -dn -o NAME,MODEL | awk '/Amazon EC2 NVMe Instance Storage/ {print "/dev/"$1;exit}')
[ -n "$DISK" ]
[ "$(lsblk -dn -o MOUNTPOINT "$DISK")" = "" ]
mkfs.ext4 -F "$DISK"
mkdir -p /mnt/planet
mount "$DISK" /mnt/planet
lsblk -o NAME,MODEL,SIZE,MOUNTPOINT
free -h
aws configure set default.s3.max_concurrent_requests 20
printf '%s staging-start\n' "$(date -u +%FT%TZ)"
aws s3 cp "$BUCKET/prepared/sources/planet-261019.osm.pbf" /mnt/planet/planet.osm.pbf --only-show-errors
[ "$(stat -c %s /mnt/planet/planet.osm.pbf)" = 95507771401 ]
printf '%s global-preparation-start\n' "$(date -u +%FT%TZ)"
/usr/bin/time -v -o prepare-time.txt timeout 3h java -Xmx320g -cp outmap.jar org.openmaptiles.tools.PrepareTrailSegments /mnt/planet/planet.osm.pbf /mnt/planet/trail-segments.geojsonseq 5bbb8c9cae945af88e6af773fed33a3e planet
aws s3 cp /mnt/planet/trail-segments.geojsonseq "$BUCKET/$PREFIX/prepared/trail-segments.geojsonseq" --only-show-errors
aws s3 cp /mnt/planet/trail-segments.geojsonseq.json "$BUCKET/$PREFIX/prepared/trail-segments.geojsonseq.json" --only-show-errors
printf '%s global-preparation-complete\n' "$(date -u +%FT%TZ)"

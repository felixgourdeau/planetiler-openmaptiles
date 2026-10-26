#!/bin/bash
set -euo pipefail
BUCKET=s3://outmap-planet-pilot-442073793062-20261025
cd /opt/pilot
exec > >(tee -a build-r3.log) 2>&1
finish() {
  code=$?
  trap - EXIT
  kill "$LOG_UPLOADER" 2>/dev/null || true
  aws s3 cp build-r3.log "$BUCKET/r3/build-r3.log" --only-show-errors || true
  shutdown -h now
  exit "$code"
}
(while sleep 30; do aws s3 cp build-r3.log "$BUCKET/r3/build-r3.log" --only-show-errors || true; done) &
LOG_UPLOADER=$!
trap finish EXIT
printf '%s staging-start\n' "$(date -u +%FT%TZ)"
DISK=$(lsblk -dn -o NAME,MODEL | awk '/Amazon EC2 NVMe Instance Storage/ {print "/dev/"$1;exit}')
[ -n "$DISK" ]
[ "$(lsblk -dn -o MOUNTPOINT "$DISK")" = "" ]
mkfs.ext4 -F "$DISK"
mkdir -p /mnt/pilot
mount "$DISK" /mnt/pilot
mkdir -p /mnt/pilot/sources /mnt/pilot/tmp
aws configure set default.s3.max_concurrent_requests 20
aws s3 sync "$BUCKET/prepared/sources" /mnt/pilot/sources --only-show-errors
aws s3 cp "$BUCKET/prepared/corridor-elevations.jsonl" corridor-elevations.jsonl --only-show-errors
aws s3 cp "$BUCKET/r3/prepared/trail-segments.geojsonseq" trail-segments.geojsonseq --only-show-errors
aws s3 cp "$BUCKET/r3/prepared/trail-segments.geojsonseq.json" trail-segments.geojsonseq.json --only-show-errors
printf '5bbb8c9cae945af88e6af773fed33a3e  /mnt/pilot/sources/planet-261019.osm.pbf\n' | md5sum -c -
aws s3 cp "$BUCKET/r3/bundle/auxiliary-inputs.sha256" auxiliary-inputs.sha256 --only-show-errors
(cd /mnt/pilot/sources && sha256sum -c /opt/pilot/auxiliary-inputs.sha256)
printf '%s generation-start\n' "$(date -u +%FT%TZ)"
/usr/bin/time -v java -Xmx48g -jar outmap.jar \
 --osm-path=/mnt/pilot/sources/planet-261019.osm.pbf --download-dir=/mnt/pilot/sources \
 --output=/mnt/pilot/sea-to-sky-20261025-r3.pmtiles --bounds=-123.75,49.25,-122.25,50.65 \
 --minzoom=0 --maxzoom=14 --threads=64 --storage=mmap --tmpdir=/mnt/pilot/tmp \
 --peak-elevation-snapshot=/opt/pilot/corridor-elevations.jsonl \
 --trail-segment-snapshot=/opt/pilot/trail-segments.geojsonseq \
 --trail-snapshot-planet-md5=5bbb8c9cae945af88e6af773fed33a3e --download=false
printf '%s generation-complete\n' "$(date -u +%FT%TZ)"
sha256sum /mnt/pilot/sea-to-sky-20261025-r3.pmtiles > archive.sha256
stat --printf='%s\n' /mnt/pilot/sea-to-sky-20261025-r3.pmtiles > archive-bytes.txt
aws s3 cp /mnt/pilot/sea-to-sky-20261025-r3.pmtiles "$BUCKET/r3/sea-to-sky-20261025-r3.pmtiles" --only-show-errors
aws s3 cp archive.sha256 "$BUCKET/r3/archive.sha256" --only-show-errors
aws s3 cp archive-bytes.txt "$BUCKET/r3/archive-bytes.txt" --only-show-errors
printf '{"state":"complete"}\n' > build-complete.json
aws s3 cp build-complete.json "$BUCKET/r3/build-complete.json" --only-show-errors
printf '%s upload-complete\n' "$(date -u +%FT%TZ)"

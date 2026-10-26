#!/bin/bash
set -euo pipefail
BUCKET=s3://outmap-planet-pilot-442073793062-20261025
PREFIX=planet-20261025-r1
ARCHIVE=planet-20261025-r1.pmtiles
cd /opt/planet
exec > >(tee -a build-world.log) 2>&1
upload_logs() {
  aws s3 sync /opt/planet "$BUCKET/$PREFIX/logs/build" --exclude '*' --include '*.log' --include '*-time.txt' --include '*-validation.json' --include 'archive.sha256' --include 'archive-bytes.txt' --include 'upload-head.json' --include 'input-checksums.txt' --only-show-errors || true
}
finish() {
  code=$?
  trap - EXIT
  kill "$UPLOADER" 2>/dev/null || true
  upload_logs
  printf '{"exitCode":%s,"finishedAt":"%s","archive":"%s"}\n' "$code" "$(date -u +%FT%TZ)" "$BUCKET/$PREFIX/artifacts/$ARCHIVE" > build-completion.json
  aws s3 cp build-completion.json "$BUCKET/$PREFIX/status/build.json" --only-show-errors || true
  shutdown -h now
  exit "$code"
}
(while sleep 60; do upload_logs; done) &
UPLOADER=$!
trap finish EXIT
[ "$(awk '/MemTotal/ {print $2}' /proc/meminfo)" -gt 250000000 ]
DISK=$(lsblk -dn -o NAME,MODEL | awk '/Amazon EC2 NVMe Instance Storage/ {print "/dev/"$1;exit}')
[ -n "$DISK" ]
[ "$(lsblk -dn -o MOUNTPOINT "$DISK")" = "" ]
mkfs.ext4 -F "$DISK"
mkdir -p /mnt/planet
mount "$DISK" /mnt/planet
mkdir -p /mnt/planet/sources /mnt/planet/tmp /mnt/planet/snapshots
lsblk -o NAME,MODEL,SIZE,MOUNTPOINT
free -h
aws configure set default.s3.max_concurrent_requests 20
printf '%s staging-start\n' "$(date -u +%FT%TZ)"
aws s3 sync "$BUCKET/prepared/sources" /mnt/planet/sources --only-show-errors
aws s3 sync "$BUCKET/$PREFIX/prepared" /mnt/planet/snapshots --only-show-errors
(cd /mnt/planet/sources && sha256sum -c /opt/planet/auxiliary-inputs.sha256)
(cd /mnt/planet/snapshots && sha256sum -c elevations.sha256)
python3 - <<'PY'
import json,pathlib,hashlib
p=pathlib.Path('/mnt/planet');m=json.loads((p/'snapshots/trail-segments.geojsonseq.json').read_text())
assert m['state']=='complete' and m['scope']=='planet' and m['planetMd5']=='5bbb8c9cae945af88e6af773fed33a3e'
def sha(path):
 d=hashlib.sha256()
 with path.open('rb') as f:
  while chunk:=f.read(8*1024*1024):d.update(chunk)
 return d.hexdigest()
source=p/'sources/planet-261019.osm.pbf'
assert source.stat().st_size==m['parentPlanetBytes']==95507771401
assert sha(source)==m['parentPlanetSha256']
assert sha(p/'snapshots/trail-segments.geojsonseq')==m['sha256']
pathlib.Path('/opt/planet/input-checksums.txt').write_text(json.dumps(m,sort_keys=True)+'\n')
PY
printf '%s generation-start\n' "$(date -u +%FT%TZ)"
/usr/bin/time -v -o build-time.txt timeout 4h java -Xmx48g -Djava.io.tmpdir=/mnt/planet/tmp -jar outmap.jar \
 --osm-path=/mnt/planet/sources/planet-261019.osm.pbf --download-dir=/mnt/planet/sources \
 --output="/mnt/planet/$ARCHIVE" --bounds=-180,-85.0511287798066,180,85.0511287798066 \
 --minzoom=0 --maxzoom=14 --threads=64 --storage=mmap --tmpdir=/mnt/planet/tmp \
 --nodemap-type=array --nodemap-storage=mmap \
 --peak-elevation-snapshot=/mnt/planet/snapshots/elevations.jsonl \
 --trail-segment-snapshot=/mnt/planet/snapshots/trail-segments.geojsonseq \
 --trail-snapshot-planet-md5=5bbb8c9cae945af88e6af773fed33a3e --download=false
printf '%s generation-complete\n' "$(date -u +%FT%TZ)"
(cd /mnt/planet && sha256sum "$ARCHIVE") > archive.sha256
stat -c %s "/mnt/planet/$ARCHIVE" > archive-bytes.txt
if aws s3api head-object --bucket outmap-planet-pilot-442073793062-20261025 --key "$PREFIX/artifacts/$ARCHIVE" >/dev/null 2>&1; then
  echo 'Refusing to overwrite an existing archive'; exit 1
fi
SHA=$(awk '{print $1}' archive.sha256)
aws s3 cp "/mnt/planet/$ARCHIVE" "$BUCKET/$PREFIX/artifacts/$ARCHIVE" --checksum-algorithm SHA256 --metadata "sha256=$SHA" --only-show-errors
aws s3api head-object --bucket outmap-planet-pilot-442073793062-20261025 --key "$PREFIX/artifacts/$ARCHIVE" --checksum-mode ENABLED > upload-head.json
python3 - <<'PY'
import json,pathlib
h=json.loads(pathlib.Path('upload-head.json').read_text())
assert h['ContentLength']==int(pathlib.Path('archive-bytes.txt').read_text())
assert h['Metadata']['sha256']==pathlib.Path('archive.sha256').read_text().split()[0]
assert h.get('ChecksumSHA256')
PY
printf '%s archive-saved-validation-start\n' "$(date -u +%FT%TZ)"
/usr/bin/time -v -o validation-time.txt timeout 30m java -Xmx4g -cp .:outmap.jar ValidateWorld "/mnt/planet/$ARCHIVE" archive-validation.json
printf '%s archive-validation-complete\n' "$(date -u +%FT%TZ)"

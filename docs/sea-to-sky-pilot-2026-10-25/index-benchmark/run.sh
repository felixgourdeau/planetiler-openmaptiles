#!/bin/bash
set -euo pipefail
BUCKET=s3://outmap-planet-pilot-442073793062-20261025
PREFIX=index-benchmark-20261026
mkdir -p /opt/index-benchmark
cd /opt/index-benchmark
exec > >(tee -a benchmark.log) 2>&1
finish() {
  code=$?
  trap - EXIT
  kill "${LOG_UPLOADER:-0}" 2>/dev/null || true
  printf '{"exitCode":%s,"finishedAt":"%s"}\n' "$code" "$(date -u +%FT%TZ)" > completion.json
  aws s3 sync /opt/index-benchmark "$BUCKET/$PREFIX/results" --exclude '*' --include '*.log' --include '*.json' --include '*.txt' --only-show-errors || true
  shutdown -h now
  exit "$code"
}
(while sleep 30; do aws s3 cp benchmark.log "$BUCKET/$PREFIX/results/benchmark.log" --only-show-errors || true; done) &
LOG_UPLOADER=$!
trap finish EXIT
printf '%s staging-start\n' "$(date -u +%FT%TZ)"
free -b > memory-before.txt
[ "$(awk '/MemTotal/ {print $2}' /proc/meminfo)" -gt 250000000 ]
DISK=$(lsblk -dn -o NAME,MODEL | awk '/Amazon EC2 NVMe Instance Storage/ {print "/dev/"$1;exit}')
[ -n "$DISK" ]
[ "$(lsblk -dn -o MOUNTPOINT "$DISK")" = "" ]
mkfs.ext4 -F "$DISK"
mkdir -p /mnt/index-benchmark
mount "$DISK" /mnt/index-benchmark
lsblk -o NAME,MODEL,SIZE,MOUNTPOINT > disks.txt
aws configure set default.s3.max_concurrent_requests 20
aws s3 cp "$BUCKET/prepared/sources/planet-261019.osm.pbf" /mnt/index-benchmark/planet.osm.pbf --only-show-errors
printf '5bbb8c9cae945af88e6af773fed33a3e  /mnt/index-benchmark/planet.osm.pbf\n' | md5sum -c -
for TYPE in sparsearray array; do
  WORK=/mnt/index-benchmark/$TYPE
  mkdir "$WORK"
  # Explicitly cold OS page cache for both runs; source and scratch use the same NVMe.
  sync
  echo 3 > /proc/sys/vm/drop_caches
  printf '%s %s-start\n' "$(date -u +%FT%TZ)" "$TYPE"
  /usr/bin/time -v -o "$TYPE-time.txt" java -Xmx48g -cp .:outmap.jar Pass1Benchmark /mnt/index-benchmark/planet.osm.pbf "$TYPE" "$WORK" "$TYPE-result.json" 2>&1 | tee "$TYPE.log"
  du -h "$WORK" > "$TYPE-disk.txt"
  free -b > "$TYPE-memory.txt"
  aws s3 sync /opt/index-benchmark "$BUCKET/$PREFIX/results" --exclude '*' --include '*.log' --include '*.json' --include '*.txt' --only-show-errors
  # Only disposable benchmark indexes in this fresh instance's known scratch directory.
  rm -rf -- "$WORK"
  printf '%s %s-complete\n' "$(date -u +%FT%TZ)" "$TYPE"
done

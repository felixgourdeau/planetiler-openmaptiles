#!/bin/bash
set -euo pipefail
cd /opt/pilot
exec > >(tee -a prepare-r3.log) 2>&1
finish() {
  code=$?
  trap - EXIT
  kill "$LOG_UPLOADER" 2>/dev/null || true
  aws s3 cp prepare-r3.log "$BUCKET/r3/prepare-r3.log" --only-show-errors || true
  shutdown -h now
  exit "$code"
}
BUCKET=s3://outmap-planet-pilot-442073793062-20261025
(while sleep 30; do aws s3 cp prepare-r3.log "$BUCKET/r3/prepare-r3.log" --only-show-errors || true; done) &
LOG_UPLOADER=$!
trap finish EXIT
printf '%s preparation-start\n' "$(date -u +%FT%TZ)"
aws s3 cp "$BUCKET/prepared/sources/planet-261019.osm.pbf" planet.osm.pbf --only-show-errors
printf '5bbb8c9cae945af88e6af773fed33a3e  planet.osm.pbf\n' | md5sum -c -
printf '%s extraction-start\n' "$(date -u +%FT%TZ)"
osmium extract --bbox=-124.25,48.75,-121.75,51.15 --strategy=complete_ways --set-bounds planet.osm.pbf -o corridor-complete.osm.pbf
printf '%s graph-start\n' "$(date -u +%FT%TZ)"
/usr/bin/time -v java -Xmx10g -cp outmap.jar org.openmaptiles.tools.PrepareTrailSegments corridor-complete.osm.pbf trail-segments.geojsonseq 5bbb8c9cae945af88e6af773fed33a3e -124.25,48.75,-121.75,51.15
aws s3 cp trail-segments.geojsonseq "$BUCKET/r3/prepared/trail-segments.geojsonseq" --only-show-errors
aws s3 cp trail-segments.geojsonseq.json "$BUCKET/r3/prepared/trail-segments.geojsonseq.json" --only-show-errors
aws s3 cp corridor-complete.osm.pbf "$BUCKET/r3/prepared/corridor-complete.osm.pbf" --only-show-errors
printf '%s preparation-complete\n' "$(date -u +%FT%TZ)"
printf '{"state":"complete"}\n' > prepare-complete.json
aws s3 cp prepare-complete.json "$BUCKET/r3/prepared/prepare-complete.json" --only-show-errors

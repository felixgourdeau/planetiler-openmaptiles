# Peak and saddle elevation gap-filling

Policy: retain valid OSM elevations and fill only missing/invalid heights from a
completed local snapshot. This is opt-in through `--peak-elevation-snapshot`.
It also covers volcano nodes already handled by the mountain-peak layer.
There are no per-feature network requests during tile generation, no new npm or
Java dependencies, and no edits to generated mappings or current clients.

## Prepare a snapshot

Use the same OSM input intended for generation. The commands below use the
existing `osmium` CLI and Node 22.20+ (native TypeScript support). Use new output
names for a new input or a fresh service snapshot; do not overwrite completed
artifacts. The directory must already exist.

```sh
mkdir -p data/peak-elevations/20261025-r1
osmium tags-filter /path/to/region.osm.pbf n/natural=peak,saddle,volcano -R \
  -o data/peak-elevations/20261025-r1/nodes.osm.pbf
osmium export data/peak-elevations/20261025-r1/nodes.osm.pbf \
  --add-unique-id=type_id --geometry-types=point -f geojsonseq \
  -o data/peak-elevations/20261025-r1/nodes.geojsonseq
node scripts/backfill-peak-elevations.mts \
  --input=data/peak-elevations/20261025-r1/nodes.geojsonseq \
  --snapshot=data/peak-elevations/20261025-r1/elevations.jsonl
```

The script uses the exact AWS `/elevations` endpoint currently used by Outmap web.
It skips plain numeric OSM heights already in the existing accepted range
(`abs(metres) < 10000`). Other text, including units, is conservatively sampled:
Planetiler's existing `Parse.meters` parser remains authoritative, and any valid
OSM height it parses ALWAYS takes precedence. Thus an unusual valid unit string
can cause a redundant lookup but cannot be replaced by the returned DEM height.

Requests are serial, 250 points by default, configurable from 1–1000 with
`--batch-size`. A one-point batch duplicates the coordinate only in the outgoing
LineString. There is a 30-second request timeout and up to three attempts with
backoff. Invalid response shape/count/value or exhausted request failures stop
the run; they are not silently recorded as no-data. Explicit null results remain
no-data. Latitudes outside Web Mercator coverage are recorded as unresolved
without sending them to the service; they are never clamped to a different point.

## Resume and provenance

Each completed batch is appended and synced to `<snapshot>.partial`. Rerun the
same command with the identical exported input to resume. Completed results,
including explicit no-data, are not requested again. A new snapshot path is
required to retry no-data against a newer source.

The snapshot records endpoint, input export SHA-256, format version, fill-gaps
policy, OSM node ID, coordinates, feature kind, original `ele` tag, retrieval
timestamp, result and status. A final footer checksums the header/results and
records the count. Only after completion is the file renamed to the requested
snapshot path. The CLI prints its full-file checksum; the generator logs the
same checksum and input/endpoint provenance. Retain the input export, snapshot,
CLI output and generator log alongside the immutable planet release evidence.
The checksum identifies the exported candidate input, not the entire OSM PBF.

An exclusive `.lock` prevents two instances using the same destination. A hard
kill may leave it behind: confirm no process is active before removing the lock.
Malformed/truncated journal data is rejected rather than guessed or overwritten;
preserve it for inspection and use a new destination. A crash after writing the
completion footer but before rename also requires inspecting the completed
partial artifact rather than appending more results. No automatic file deletion
or overwrite is used to recover these exceptional cases.

## Generate tiles

Build the current profile using the normal repository build procedure, then add:

```sh
java -Xmx24g -jar target/planetiler-openmaptiles-3.15.1-SNAPSHOT-with-deps.jar \
  --osm-path=/path/to/region.osm.pbf \
  --output=/path/to/new-revision.pmtiles \
  --peak-elevation-snapshot=data/peak-elevations/20261025-r1/elevations.jsonl
```

The loader rejects incomplete snapshots, wrong policy/version, duplicate IDs,
invalid coordinates/heights and checksum/count mismatches. Fallback lookup also
requires the node ID, coordinates at OSM precision, kind and original elevation
tag to match the current source. A moved or retagged node therefore cannot
silently reuse an older sample. Unmatched or no-data points remain unknown.

Existing `ele` and `ele_ft` fields and rounding are retained. No zero is fabricated
for no-data. A sampled zero is a legitimate numeric result. Added elevations can
affect label ranking, and formerly excluded unnamed peaks may now be emitted if
they acquire usable elevation. No new client code is needed to read these fields.

The snapshot is loaded into memory once; measure memory and service workload
before a planet-wide run. The endpoint does not return a DEM revision, resolution
or vertical datum per result. This is sampled fallback information, not a claim
of surveyed summit accuracy or complete global coverage.

## Regional acceptance, 2026-10-25

- Compiled Java with offline Maven `compile`, tests and formatting disabled.
- Used the existing real Whistler extract: 56 exported peak/saddle nodes, three
  missing elevations; only those three were queried.
- Resumed a checkpoint containing two real results: only the remaining result
  was requested, and all three values matched the first run.
- Generated baseline/enriched `mountain_peak` archives with identical inputs.
- Decoded all z14 tiles: 54 distinct peak/saddle IDs in both archives; all 51
  existing `ele` values remained unchanged. The only attribute changes were
  `ele`/`ele_ft` added to the three missing saddles.
- Enriched PMTiles passed `pmtiles verify`; no data errors were reported in its
  build log. The archive covers z7–14 for this isolated layer.

| Saddle | New `ele` (metres) | New `ele_ft` |
| --- | ---: | ---: |
| Wedge-Parkhurst Col | 2257 | 7404 |
| Unnamed node 3498316398 | 2620 | 8597 |
| Singing Pass | 1718 | 5635 |

Retained evidence: [live snapshot](peak-elevation-backfill-2026-10-25/whistler-elevations.jsonl).
Temporary archives, decoded attributes and logs:
`/private/tmp/outmap-peak-backfill-20261025/`.
The regional run used freshly compiled `target/classes` before the existing
dependency JAR; embedded JAR build/version metadata is not profile provenance.

No unit tests, test harnesses or repository typechecks were introduced or run.
Physical-device rendering, full-basemap/offline acceptance, global performance,
and live no-data/invalid-value coverage remain unverified. No production archive
or client style was changed. Follow the immutable release procedure before
publication.

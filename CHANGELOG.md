# Outmap basemap changelog and reproduction guide

Recorded 2026-10-26. This document consolidates the work captured in commit
`ad188fc` (`Expand Outmap basemap features and record build workflows`), relative
to `f1f7f2c`. It covers this repository and links the retained evidence. It does
not certify the current state of other repositories, deployed clients, or cloud
resources. No build, deployment, or cloud lookup was performed to write it.

## 1. What was completed

| Stage | Recorded result |
| --- | --- |
| Source checkpoint | 281 changed files committed and pushed to `origin/minzoom-tweaks`; local branch is `felixgourdeau/minzoom-tweaks`. |
| Regional coverage checks | Monaco, Whistler and a diagnostic San Francisco extract were compared; the expanded detail exceeded the original 10% tile-growth target and was retained with that tradeoff. |
| Sea to Sky r3 | Prepared trail snapshot, generated archive, decoded all 9,303 tiles and rendered four locations with the existing style. |
| Full planet | Generated and saved `planet-20261025-r1.pmtiles`; directory validation and stratified content sampling passed. |
| R2 transfer | Later receipt records completed import into R2 `tiles` under `planet-20261025-r1/artifacts/planet-20261025-r1.pmtiles`. |
| Production replacement | A receipt created during this documentation pass records the new archive at `planet-250825.pmtiles`, with matching size and sampled ranges. Served-tile validation, cache purge and migration-credential cleanup are not established by that receipt. |
| New style helpers | Committed here; their existence does not establish web/mobile integration or a released app. |

Earlier feature READMEs describe their state at implementation time. In particular,
the initial trail document's planet-scale warning predates the successful snapshot
pipeline, and the publication README's “migration running” status predates its
completed migration receipt. Use the dated receipts below for those later stages;
do not treat earlier pending notes as a reversal of a completed stage.

## 2. Source changes and behavior

Paths below are relative to this repository. Java paths share
`src/main/java/org/openmaptiles/`.

| Area | Changes and important constraints | Implementation / evidence |
| --- | --- | --- |
| Outdoor trail/road attributes | Preserve raw values, recognized normalized values, units, conditional access, difficulty, hazards, barriers, lifecycle and route memberships. Missing tags remain absent; no live access evaluation or routing engine was added. Direction-sensitive ways avoid inappropriate merging. | `util/OutdoorAttributes.java`, `layers/Transportation.java`, `layers/TransportationName.java`; [contract](docs/trail-attributes.md) |
| Expanded OSM geometry | Add selected pistes, via ferrata, barriers, paddling, natural and man-made geometry in the same archive's `outdoor` layer. Fine additions start at z14. | `addons/OutdoorFeatures.java`, `util/CoverageAttributes.java`; [coverage matrix and measurements](docs/osm-coverage-2026-10-25/README.md) |
| POIs | Broaden outdoor/emergency/recreation/natural/historic/shop/amenity/sport coverage, sparse contact/amenity details, obstacle markers and marine metadata. Preserve existing dispatch priority and classification where already admitted. | `layers/Poi.java`; [inventory](docs/outdoor-poi-inventory.md), [POI audit](docs/poi-audit-2026-10-25/README.md), [zoom plan](docs/poi-zoom-plan.md) |
| Parking priority | Promote only explicitly public/permissive trailhead parking; restrictive, unknown or conditional access prevents promotion. Proximity, name or surface does not establish access. | `layers/Poi.java`; [decision](docs/basemap-followup-2026-10-25.md) |
| Low-zoom pruning | At z12/13 retain POI ranks 1–12 plus transit/outdoor-priority exceptions; do not change POI minzooms. Keep z14 detail except approved exclusions. | `layers/Poi.java`; [r3 scope and validation](docs/sea-to-sky-pilot-2026-10-25/r3/README.md) |
| Kerbs and sidewalks | Exclude explicit kerbs and mapped sidewalks before layer processing. Ordinary roads with `sidewalk=yes/both/separate` and crossings remain. | `util/ExcludedMapFeatures.java`, `OpenMapTilesProfile.java` |
| Short bridges | Retain crossing attributes from z10 for bridge features, so short crossings retain the data existing styles need. This is not a new bridge style or proof of device rendering. | `layers/Transportation.java` |
| Landcover and water | Add shingle, mud, reef/shoal, wetland subtypes, explicit landcover aliases and unknown-value fallback; preserve surface/tidal metadata and mapping precedence. Keep Natural Earth inputs. | `layers/Landcover.java`, `layers/Water.java`, generated mappings/schema; [contract](docs/landcover-coverage.md) |
| Marine | Add marine POI metadata and coastal overlays; supply icon/pattern assets. No bathymetry, inferred depth or complete nautical symbol system. Later z14 coverage includes generic seamarks, moorings and berths beyond the initial marine list. | `styles/marine.ts`, `styles/marine-images/`, `scripts/export-marine-images.mts`; [marine guide](docs/marine-features.md), [expanded coverage](docs/osm-coverage-2026-10-25/README.md) |
| Buildings, parks, waterways | Retain building footprints/parts and extrusion heights, add sparse roof/building metadata, protected-area attributes, paddling fields and dam/weir line admission. No shaped-roof meshes. | `layers/Building.java`, `layers/Park.java`, `layers/Waterway.java`, `generated/Tables.java`; [coverage guide](docs/osm-coverage-2026-10-25/README.md) |
| Polygon labels | Add `polygon_label` points and optional interior label lines for named natural/park polygons. Bounded geometric heuristic; existing label layers remain. | `addons/PolygonLabel.java`, `addons/PolygonLabelGeometry.java`; [algorithm and styling](docs/polygon-labels.md), [review](docs/polygon-label-review-2026-10-25.md) |
| Trail distances | Add z14 `trail_segment_label` points with integer `length_m`, `bearing`, `loop`. Measure junction-to-junction horizontal source distance before clipping. Reject short/uncertain chains. IDs are archive-local. | `addons/TrailSegmentLabels.java`; [semantics and style helper](docs/trail-segment-labels.md) |
| Prepared trail snapshots | Add compact graph preparation outside tile generation and checksummed snapshot ingestion. Regional extraction retains a topology buffer; global mode verifies the parent PBF and enforces memory/capacity gates. Legacy in-build graph remains when no snapshot is supplied. | `tools/PrepareTrailSegments.java`, `addons/TrailSegmentSnapshot.java`, `OpenMapTilesMain.java`; [regional run](docs/sea-to-sky-pilot-2026-10-25/r3/README.md), [global run](docs/planet-build-2026-10-25-r1/README.md) |
| Elevation gaps | Preserve valid OSM peak/saddle/volcano heights; fill only missing/invalid values from a completed, checksummed snapshot. Keep no-data explicit. No per-feature network calls during generation. | `scripts/backfill-peak-elevations.mts`, `util/PeakElevationSnapshot.java`, `layers/MountainPeak.java`; [workflow](docs/peak-elevation-backfill.md) |
| Iceland pilot | Optional reviewed official-name snapshot and `iceland_name` layer. Three approved pilot names; unmatched candidates are not automatically imported. Preserve attribution and recompare against each target OSM snapshot. The full-world command did not enable this option. | `scripts/prepare-iceland-names.mts`, `addons/IcelandNames.java`, `styles/iceland-names.ts`; [review and commands](docs/basemap-followup-2026-10-25.md) |
| Name compaction | After merging/ranking, remove only exact redundant aliases/translations with compatibility exceptions for Latin/non-Latin/bilingual expressions. Preserve distinct translations. | `util/OmtLanguageUtils.java`, `OpenMapTilesProfile.java`; [measurement and rule](docs/sea-to-sky-pilot-2026-10-25/name-compaction/README.md) |
| Node index | Default to `array`/`mmap`, preserving explicit overrides. Full-PBF first-pass benchmark was 2m32s versus 14m28s for sparsearray. | `OpenMapTilesMain.java`; [benchmark](docs/sea-to-sky-pilot-2026-10-25/index-benchmark/README.md) |
| Publishing | Add immutable revision preflight/upload helper; keep old client/offline revisions available. | `scripts/publish-planet.mts`, [release procedure](docs/planet-releases.md), `COMMAND.md` |
| Repository build policy | Remove unit-test sources/dependencies and explicit test-runner plugins; skip test compilation/execution. CI's Regenerate job now checks generated-source hashes around the build instead of regenerating. | `AGENTS.md`, `pom.xml`, `submodule.pom.xml`, `.github/workflows/maven.yml` |

Style helpers also include `styles/landcover.ts`, `styles/outdoor-details.ts`
(including building extrusion), and `styles/trail-segment-labels.ts`. They require
explicit client integration, placement, fonts/images, units and renderer acceptance.
The glacier label visibility edits recorded in the [follow-up](docs/basemap-followup-2026-10-25.md)
belong to separate web/mobile repositories and cannot be recreated by this Git
commit alone. No claim of a client deployment follows from this checkpoint.

## 3. Decisions deliberately not implemented

- No wholesale removal of raw/normalized attributes, alphabetical encoder-key
  ordering, stronger-gzip rollout, or POI contact/rank removal. Those were measured
  alternatives, not changes included in the world build. See the
  [attribute/encoding audit](docs/sea-to-sky-pilot-2026-10-25/attribute-audit/README.md).
- No removal of climbing POIs: measured savings did not establish matching content
  coverage or offline compatibility. See the [climbing audit](docs/sea-to-sky-pilot-2026-10-25/climbing-audit/README.md).
- No separate detail service, shaped building roofs, broad automatic official-name
  import, or automatic offline-pack replacement.
- No upstream generated-code regeneration. Preserve the custom `Tables.java` and
  `OpenMapTilesSchema.java`; a future regeneration needs an agreed preservation plan.

## 4. Reproduction prerequisites and source provenance

Use Java 21, the repository Maven wrapper, Node 22.20+ for native TypeScript
scripts, and `osmium` for preparation. The recorded remote hosts used ARM64 Linux,
AWS CLI, Python 3, GNU time and timeout; publishing uses an already configured
`rclone` remote. Credentials and account configuration are external to Git.

Inspect `AGENTS.md` before working: do not add/run unit tests or repository
typechecks. This documentation task did not install tools or dependencies.

To inspect the exact committed checkpoint in a separate checkout:

```sh
git clone https://github.com/felixgourdeau/planetiler-openmaptiles.git outmap-reproduction
cd outmap-reproduction
git checkout --detach ad188fc
```

This captures source and records, not the large input/output objects. The world
build predates this commit: its frozen JAR hash is the artifact identity. Do not
assume a fresh Maven package from this checkpoint is byte-identical to that JAR,
or use the embedded Planetiler dependency githash as the custom profile revision.

Normal source packaging (not executed for this documentation):

```sh
./mvnw -DskipTests package
```

Historical offline packaging encountered uncached Maven plugin artifacts. The
coverage run used compilation followed by `jar:jar assembly:single` to assemble
with already available dependencies. Do not silently skip a failed compile or
claim that copying an old JAR builds the current source. Record Java/Maven/tool
versions, Git revision, dirty diff if any, JAR SHA-256 and all flags for every run.

### Frozen full-world inputs and output

Private artifact root recorded in the run:
`s3://outmap-planet-pilot-442073793062-20261025/`.
Access and continued retention must be verified before attempting reproduction.

| Artifact | Recorded identity |
| --- | --- |
| OSM PBF | `prepared/sources/planet-261019.osm.pbf`; 95,507,771,401 bytes |
| PBF MD5 | `5bbb8c9cae945af88e6af773fed33a3e` |
| PBF SHA-256 | `7ffb9d25cf3829bd735f349f8a5c6a393a9b4d8155c90611547c9749640b1fbb` |
| World JAR | `planet-20261025-r1/bundle/v2/outmap.jar`; SHA-256 `186f7e5b95f44107df65673938ee1bec0549a752da35826cc6b43acf572d67cd` |
| Elevation snapshot | `planet-20261025-r1/prepared/elevations.jsonl`; SHA-256 `db2fd4b0b0aa8a8dfabe07381a0f3e191e440e477ae1c48a5b395b64f9c8abd5` |
| Global trail snapshot | `planet-20261025-r1/prepared/trail-segments.geojsonseq` plus `.json` manifest; snapshot SHA-256 `27f7955995a75021d81ddd4324eee6d71483214e68048d1b4027dd45af6084dc` |
| Final archive | `planet-20261025-r1/artifacts/planet-20261025-r1.pmtiles`; 104,924,743,209 bytes |
| Archive SHA-256 | `c105a182bb9b921aafbce12ecfe40f2724d2f1c547786a7b44a0083c90e3a0d9` |

Auxiliary sources are in `prepared/sources/`. Preserve the cached sources rather
than downloading “latest” for an exact historical replay:

| File | SHA-256 |
| --- | --- |
| `lake_centerline.shp.zip` | `56dd891eb6d23315c8176ef4f23f9ae6e152386403d7c7ab0aa54537c8ab16b1` |
| `natural_earth_vector.sqlite.zip` | `bd9420e7303f6abd4df5245be57107eec3ed4fa0246d31fa63bbc6ec95aaeb56` |
| `water-polygons-split-3857.zip` | `c4eb05a515683d20352a45310d45b5c28b575d30eeb837f2d954b674a2e1bcfd` |

Sources: [parent/snapshot manifest](docs/planet-build-2026-10-25-r1/build-results/input-checksums.txt),
[auxiliary checksums](docs/sea-to-sky-pilot-2026-10-25/r3/auxiliary-inputs.sha256),
[world results](docs/planet-build-2026-10-25-r1/README.md).
The main runner also references `wikidata_names.json`; retain and inventory any
translation cache used. The three-file auxiliary checksum list does not establish
its presence or identity. Re-querying external translation/elevation/name services
is not an exact replay. Some pilot raw evidence lived only under `/private/tmp`;
Git preserves selected outputs, not every downloaded raw response.

## 5. Rebuild sequence

### A. Restore or prepare elevation input

For the historical replay, restore the completed elevation snapshot and verify
its SHA-256. For a new PBF, use the export/backfill commands in
[peak-elevation-backfill.md](docs/peak-elevation-backfill.md). The global export had
1,202,166 nodes and 357,665 preliminary gaps; the completed backfill has 357,665
records, which must not be interpreted as 357,665 successful non-null heights.

Use serial batches of 250, retain the export and journal, and resume only against
identical input. The loader requires a complete footer/checksum and matching node
identity, coordinates, kind and original elevation tag. A new service query may
produce different results; the saved snapshot is the reproducibility boundary.

### B. Restore or prepare trail snapshot

For historical replay, restore both the snapshot and companion manifest. For
fresh global preparation, the recorded invocation was:

```sh
java -Xmx320g -cp /path/to/outmap.jar \
  org.openmaptiles.tools.PrepareTrailSegments \
  /path/to/planet-261019.osm.pbf /new/run/trail-segments.geojsonseq \
  5bbb8c9cae945af88e6af773fed33a3e planet
```

Use the actual PBF digest when changing inputs. Preparation requires `osmium` on
PATH and verifies the parent input. The successful host was r7gd.16xlarge with
512 GiB RAM, local NVMe and a 320-GiB heap. Initial 192-GiB-heap preparation failed
its guard before graph allocation: estimated 192,167,258,400 bytes exceeded the
70%-of-heap budget. The successful capacity settings include map load factor 0.90
and a 950-million-reference guard; do not bypass these gates.

The global result had 76,606,390 segments and 24,826,306 rejected chains. Rejections
include short/uncertain chains and are not a count of missing valid trails.
Preparation took 85m10s excluding staging/upload. For regional output, use the
buffered extraction sequence in [r3 prepare.sh](docs/sea-to-sky-pilot-2026-10-25/r3/prepare.sh)
and keep output bounds strictly inside preparation bounds. Do not use an
unbuffered extract to infer full segment lengths across its edge.

### C. Verify inputs, then generate

The [recorded world script](docs/planet-build-2026-10-25-r1/build-world.sh) is the
exact operational reference. It verifies auxiliary hashes, the elevation hash,
parent PBF size/SHA-256 against the trail manifest, and the trail snapshot hash
before generation. The runner checks manifest policy/scope/checksum, but that does
not replace the script's independent check of the actual PBF bytes.

Equivalent generation command after staging those verified files:

```sh
java -Xmx48g -Djava.io.tmpdir=/new/run/tmp -jar /path/to/outmap.jar \
  --osm-path=/new/run/sources/planet-261019.osm.pbf \
  --download-dir=/new/run/sources \
  --output=/new/run/planet-YYYYMMDD-rN.pmtiles \
  --bounds=-180,-85.0511287798066,180,85.0511287798066 \
  --minzoom=0 --maxzoom=14 --threads=64 \
  --storage=mmap --tmpdir=/new/run/tmp \
  --nodemap-type=array --nodemap-storage=mmap \
  --peak-elevation-snapshot=/new/run/snapshots/elevations.jsonl \
  --trail-segment-snapshot=/new/run/snapshots/trail-segments.geojsonseq \
  --trail-snapshot-planet-md5=5bbb8c9cae945af88e6af773fed33a3e \
  --download=false
```

Replace placeholder paths/revision, create directories, and use a new destination.
The measured host was m7gd.16xlarge, 256 GiB RAM, 64 workers; PBF, intermediate
features, node index, snapshots and archive were on local instance-store NVMe.
Peak process RSS was 130.18 GiB despite a 48-GiB Java heap. Disk capacity must cover
all staged inputs, intermediates and output together; the 105-GB output size alone
is not a scratch-space requirement. This documentation does not measure a new
minimum disk budget.

Do not run historical bootstrap scripts unchanged on an existing machine: they
format a selected instance-store disk and shut the host down on completion.
They also hard-code historical bucket/prefix names, status keys and time limits.
For a new remote run, adapt all names consistently and review resource policy.

### D. Save, validate and clean up

1. Hash the completed file and save its byte count, JAR/input hashes, flags and logs.
2. Save it to a fresh private object key before lengthy diagnostics; a saved file
   is not automatically production-approved. The run uploaded with S3 SHA-256
   checksumming and `sha256` metadata, then verified HEAD size/metadata.
3. Inspect PMTiles directories and decode geographically distributed samples at
   every zoom. [ValidateWorld.java.txt](docs/planet-build-2026-10-25-r1/ValidateWorld.java.txt)
   is the retained manual archive inspector; [bootstrap-build.sh](docs/planet-build-2026-10-25-r1/bootstrap-build.sh)
   identifies its frozen class/hash bundle. This is not a unit-test harness.
4. Check expected layers, z14 trail fields, feature counts and representative
   geometries; compare tile-size distributions and current-style rendering.
5. Collect evidence before terminating compute. Verify terminated instances,
   deleted root EBS disks, no orphan multipart uploads/checkpoints, and removal
   of temporary IAM role/profile/policies. Retain inputs, snapshots, bundle,
   output, checksums, logs and failed-attempt records intentionally.

The recorded monitor gates generation on `status/elevations.json` and
`status/trails-v2.json`, retaining failed `status/trails.json`. It persists the
launch request/client token to avoid duplicate launches and does not rebuild on
failure. Read [monitor-build.mts](docs/planet-build-2026-10-25-r1/monitor-build.mts)
before adapting it. Historical limits were 3h trail processing/210m host shutdown,
4h world generation/5h host shutdown, IMDSv2, no public inbound SSH, scoped IAM and
delete-on-termination root disks. IAM resources were deleted after the run and
must not be assumed to exist for replay.

## 6. Validation results, costs and their limits

| Run | Recorded result |
| --- | --- |
| Expanded coverage | Whistler all-tile z14 p95 +22.29%; San Francisco +18.97%; matched populated Monaco +15.44%. No lost/reclassified existing POIs in paired samples. San Francisco input had missing-node warnings. |
| Sea to Sky r3 | 56,526,685 bytes, SHA-256 `4fcef684098936abcfeb19aba010ba1e7aa0c0694e27931fb137ac8ac95d0170`; 10.98% smaller than r2, generation 36m01s; all 9,303 tiles decoded. |
| r3 trail/elevation | All 40,658 in-bounds snapshot IDs emitted at z14 with matching fields; all 4,096 mountain-layer IDs/attribute maps matched r2. |
| r3 visual checks | Squamish, Whistler, Pemberton and Howe Sound previews passed with unchanged style hashes; new custom layers intentionally remained unstyled. |
| Name compaction audit | Estimated regional payload saving 3.19% against equally re-encoded baseline; no distinct-name loss or checked current-style name-expression differences. Not a rebuilt r3 archive. |
| Full planet | 104,924,743,209 bytes; generation 3h09m38s; first launch through validation 5h36m including failed prep/retry. |
| World archive validation | 276,920,415 addressed tile coordinates checked; 4,255 samples, 783,402 decoded features, 4,287 trail distances; sampled checks passed. |
| World estimated cost | About US$8.3 compute; US$9–10 allowance including short-lived root storage/requests. Historical estimate, not invoice or current price; excludes endpoint billing, ongoing S3 storage and future egress. |

Full-world stages were OSM pass 1 2m, pass 2 78m17s, trail import 13m34s,
sorting 5m32s and archive 88m25s. Recorded cleanup found all four run instances
terminated and their root disks absent. See [final run report](docs/planet-build-2026-10-25-r1/README.md),
[archive validation](docs/planet-build-2026-10-25-r1/build-results/archive-validation.json)
and [cleanup receipt](docs/planet-build-2026-10-25-r1/cleanup.json).

Sampling is not exhaustive worldwide feature validation. S3 multipart composite
checksums are not flat-file SHA-256 values. The full archive was not independently
downloaded and rehashed locally. Physical iOS/Android, offline refresh/airplane-mode,
new helper rendering, rare feature families and exhaustive polygon-label placement
remain separate acceptance work. No tests/typechecks were run for this document.

## 7. Publishing and rollback

The reusable default remains [immutable revision publishing](docs/planet-releases.md):

```sh
node scripts/publish-planet.mts \
  --archive=/path/to/planet-YYYYMMDD-rN.pmtiles \
  --revision=planet-YYYYMMDD-rN
# After reviewing preflight and acceptance, the upload invocation adds --publish.
```

Preflight is read-only; publishing refuses occupied revision/manifest keys and
uses rclone immutable upload. Verify public TileJSON and representative MVTs before
changing consumers. The helper does not promote clients or delete old archives.
Keep old revisions for rollback and saved offline packs; changing an archive or
URL does not itself refresh offline downloads. See the linked runbook for web,
mobile and legacy consumer paths and explicit Refresh semantics.

### Separately authorized historical replacement

On October 26 the user requested replacing the old production key after backing
it up. This was an explicit exception for that operation, not a new overwrite
default. Evidence in [publication/](docs/planet-build-2026-10-25-r1/publication/):

- Backup `tiles/backups/planet-250825-before-20261026.pmtiles`: 89,749,023,592 bytes;
  size and three sampled 1-MiB ranges matched the old object. See
  [backup receipt](docs/planet-build-2026-10-25-r1/publication/backup-verification.json).
- Direct Super Slurper import job
  `53daf2e70f7d45d1b2504c5f462463714bde36725712a66481bf33ea9d0821d4`
  completed at `2026-10-26T13:37:46Z`, with overwrite false, from the exact source
  prefix into R2 `tiles`. See [status](docs/planet-build-2026-10-25-r1/publication/migration-status.json).
- Imported key `planet-20261025-r1/artifacts/planet-20261025-r1.pmtiles` matched size,
  header/root, metadata region and sampled data ranges. Super Slurper omitted
  custom SHA metadata; the source SHA is provenance, not an independent R2
  full-file rehash. See [import verification](docs/planet-build-2026-10-25-r1/publication/import-verification.json).
- The initial local relay was interrupted and its multipart upload removed; the
  completed backup was preserved. The direct migration used an expiring scoped
  AWS credential with separately approved bucket listing. Credentials were kept
  outside Git. See [publication notes/policy](docs/planet-build-2026-10-25-r1/publication/README.md).

A concurrent local receipt,
`docs/planet-build-2026-10-25-r1/publication/production-verification.json`, appeared
while this changelog was being committed. It records production key
`planet-250825.pmtiles`, LastModified `2026-10-26T13:44:28Z`, size
104,924,743,209 bytes, `outmap-dataset-revision=planet-20261025-r1`, and SHA metadata
`c105a182bb9b921aafbce12ecfe40f2724d2f1c547786a7b44a0083c90e3a0d9`. Its sampled
range hashes match the import receipt. The receipt was untracked when inspected
and is not included in this documentation-only commit; this paragraph preserves
the observed result. Its copied scope text says metadata was omitted during
Super Slurper import, but the production HEAD explicitly contains the restored
SHA metadata. Neither metadata nor sampled ranges are a full-file rehash.

Before resuming publication, inspect actual production/backup/import state and
migration-user cleanup rather than trusting an old “running” note or repeating
the replacement. Served-tile checks, targeted cache purge and deletion of the
temporary user/key still need their own receipts. For an authorized same-key rollback,
the verified old backup is the recovery source; do not remove it. Same-key updates
can leave online caches stale for their lifetime and do not replace offline packs.

## 8. What to retain for the next reproduction

Retain the source commit, frozen source/JAR bundle, tool versions, exact PBF and
auxiliary/translation inputs, completed elevation/trail snapshots and manifests,
full commands/environment, archive/checksums, manual validation outputs, style
hashes/screenshots, publication/backup receipts and cleanup evidence. Re-check
private object availability before starting; Git alone cannot reproduce the
external data or recover expired temporary files. A new input date or fresh
external-service response is a new build, even if the source code is unchanged.

This changelog consolidates the recorded process; it does not promise byte-for-byte
archive determinism. Parallel processing, map-key serialization, tool versions
and external data can affect bytes. Compare semantic content and provenance as
well as hashes, and assign a fresh revision to every new output.

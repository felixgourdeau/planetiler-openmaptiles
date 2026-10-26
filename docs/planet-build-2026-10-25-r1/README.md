# Full-planet build — started October 25, 2026 (America/Toronto)

Status: COMPLETE. Generation, upload and manual archive validation succeeded. All four run instances terminated, all four root EBS volumes are absent, local instance-store scratch is destroyed, and temporary IAM role/profile and checkpoint are removed. The archive remains private; production clients are unchanged.

## Scope and frozen inputs

- Full `planet-261019.osm.pbf`, 95,507,771,401 bytes, MD5 `5bbb8c9cae945af88e6af773fed33a3e`.
- Output: `planet-20261025-r1.pmtiles`, global Web Mercator bounds, z0–14.
- Keep all existing custom layers, including `trail_segment_label` and climbing POIs. Keep current styles. Apply the already-approved identical-name compaction and prior kerb/sidewalk exclusions and conservative low-zoom POI pruning. Do not apply the later audited raw/normalized removal or attribute ordering proposals.
- Source JAR SHA-256 `186f7e5b95f44107df65673938ee1bec0549a752da35826cc6b43acf572d67cd` (bundle/v2). Frozen source and scripts retained in the dedicated private S3 run prefix.
- Array/mmap node index, 64 workers, 48-GiB build heap on m7gd.16xlarge (256 GiB RAM). PBF, node indexes, intermediate features, snapshots and generated archive on local instance-store NVMe.
- Cached auxiliary inputs use the same SHA-256 values as r3, checked before generation.

## Approved global trail preparation

User explicitly approved adding and validating global snapshot support before the build. `PrepareTrailSegments` now accepts `planet` scope, verifies the input PBF against the supplied MD5 while recording its SHA-256 and size, and uses osmium to retain all highway ways and their referenced nodes. Non-eligible highways remain available as junction boundaries. The existing compact graph algorithm and distance semantics are unchanged.

A pre-allocation scan conservatively estimates graph memory from eligible way-node references and edge counts. It fails before graph allocation if the estimate exceeds 70% of the heap or array-capacity bounds. The initial attempt used a 192-GiB heap on 256-GiB RAM; the authorized retry uses a 320-GiB heap on 512-GiB RAM. No regional extraction boundary invalidation is applied to a verified global source; the existing Web Mercator latitude/no-data guards remain.

Global manifests include scope and parent PBF size/hash. `OpenMapTilesMain` validates those fields and global coverage, while preserving existing regional snapshot validation. The build independently verifies the staged PBF SHA-256 against the global manifest and checks snapshot hashes before starting.

Manual check on the existing Monaco extract: regional-wide-bound and global-mode preparation each produced 584 features with identical ordered parsed JSON features, IDs, coordinates, distances and loop flags. Serialized hashes differ because map key order differs across JVMs. This exercises the global code path; it does not claim Monaco is a full planet or certify planet-scale resource usage. See `global-mode-manual-validation.json`.

## Elevations

The existing global export contains 1,202,166 peak/saddle/volcano nodes, with 357,665 preliminary gaps. The unchanged resumable backfill runs serial batches of 250 on m7g.xlarge Spot. It preserves valid source heights, records outside-coverage/no-data explicitly, checkpoints locally and saves recoverable journal prefixes to S3. The build requires the completed checksummed snapshot, not a partial journal.

## Lifecycle and acceptance

- Preparation instances terminate on success/failure; deadlines are 4 hours for elevation and 3.5 hours for the authorized trail retry.
- World generation has a 4-hour process timeout and a 5-hour instance shutdown deadline. Shutdown behavior is terminate; root EBS disks use DeleteOnTermination.
- Scoped temporary IAM role, no public inbound SSH, IMDSv2 required. No account credentials are copied to the instances.
- Launch request has a persisted idempotency token. The monitor does not rebuild on failure.
- Save the completed archive with a full-file SHA-256 manifest and S3 multipart SHA-256 verification before archive acceptance, so a diagnostic failure does not destroy the output. A saved archive is not automatically production-approved.
- Check every PMTiles directory coordinate and addressed count, then decode geographically stratified MVT samples at all zooms, including trail-distance values/minzoom and expected layers. This is not exhaustive decoding of every global feature or device rendering certification.
- Verify termination and disk cleanup after collecting results. Remove this run's staging/checkpoint objects and incomplete multipart uploads after retaining the output/provenance evidence. No production archives or client URLs are overwritten.

Compilation and JAR assembly succeeded with existing offline Maven dependencies, without unit tests/typechecks or generated mapping regeneration. No production publishing is included in this run.


## Trail preparation attempt 1 and pending retry

Attempt 1 (m7gd.16xlarge, instance i-05b3cbd8b2a507c88) terminated after its capacity guard reported 65,314,600 eligible ways, 938,953,650 node references, 873,639,050 upper-bound edges, estimated peak 192,167,258,400 bytes versus heap budget 144,310,901,140 bytes. No graph was allocated. Its failed status remains at `status/trails.json`; preserve it.

Prepared v2 changes: map load factor 0.90 accommodates the measured reference upper bound within HPPC's maximum table size; retain a conservative 950-million-reference guard and the same 70%-of-heap memory estimate. Proposed preparation host r7gd.16xlarge (512 GiB), 320-GiB heap, 3-hour process timeout and 210-minute instance shutdown deadline. Explicit user approval of this increased size/cost was received after the automatic approval review rejection. The retry is authorized; preserve its 3.5-hour deadline and $2.50/hour ceiling. Tile generation remains m7gd.16xlarge (256 GiB), 48-GiB heap.

The proposed retry writes `status/trails-v2.json` and uses versioned `bundle/v2` objects, preserving the initial bundle and failed status. No deletion of the failed gate is needed. The monitor now waits on the separate retry status, and has been resumed after explicit approval and launch of retry instance i-021c382f7a516dadd. V2 source JAR SHA-256: `186f7e5b95f44107df65673938ee1bec0549a752da35826cc6b43acf572d67cd`. The same 584 local segment features remain identical after the capacity-setting change.

The user approved the temporary 15-minute follow-up. Automation ID: `finish-planet-build-and-cleanup`. It must be removed after final validation and resource cleanup. The separate explicit answer approving the 512-GiB preparation retry has also been received.

Active retry: `i-021c382f7a516dadd` (r7gd.16xlarge, us-east-1d). Elevations: `i-00856b5e06f3c8ae1` (m7g.xlarge). Initial preparation `i-05b3cbd8b2a507c88` is terminated. The locked monitor is running and awaits `status/elevations.json` and `status/trails-v2.json`; it ignores the retained failed `status/trails.json`.

## Follow-up at 2026-10-26T04:29Z

Elevation preparation exited 0 with 357,665 records; snapshot SHA-256 `db2fd4b0b0aa8a8dfabe07381a0f3e191e440e477ae1c48a5b395b64f9c8abd5`. EC2 confirms `i-00856b5e06f3c8ae1` terminated; a volume-ID filter returned no volume for `vol-0e787f20fa3a8ca6a`. Trail retry remains running; capacity estimate 192,167,258,400 bytes is below its 240,518,168,576-byte heap budget. The monitor remains active and waits for `status/trails-v2.json`.

## World generation started — 2026-10-26T05:03Z

Global trail preparation completed with exit code 0, 76,606,390 segments and 24,826,306 rejected chains under the existing policy. Preparation took 5,110.27 seconds (85.17 minutes), excluding staging/upload. Snapshot SHA-256: `27f7955995a75021d81ddd4324eee6d71483214e68048d1b4027dd45af6084dc`. Both snapshot gates succeeded and the existing monitor launched one world build. At 05:13Z generation was processing OSM pass 2.

EC2 confirmed both preparation instances terminated; volume-ID filters returned no disks for `vol-09b96c9b7a53f5612` and `vol-0e787f20fa3a8ca6a`. Build root disk `vol-03d64d65a887e1394` has DeleteOnTermination enabled. The build, final archive validation and final cleanup remain pending.

## Final result and acceptance

- Archive: `s3://outmap-planet-pilot-442073793062-20261025/planet-20261025-r1/artifacts/planet-20261025-r1.pmtiles` (private; no public URL or production promotion).
- Size: **104,924,743,209 bytes** (104.92 GB / 97.72 GiB).
- Full-file SHA-256: `c105a182bb9b921aafbce12ecfe40f2724d2f1c547786a7b44a0083c90e3a0d9`.
- S3 HEAD size and SHA metadata match the producer manifest. S3 also returned a multipart composite SHA-256; it is not the flat file hash. The full archive was not independently downloaded and rehashed locally.
- PMTiles v3, gzip MVT, world Mercator bounds, z0–14. All **276,920,415 addressed tile coordinates** checked for directory ordering/counts. **4,255 samples**, zero missing sampled coordinates, **783,402 decoded features**, including **4,287 trail distances**. Required sampled layers and trail minzoom/distance checks passed. See `build-results/archive-validation.json`.
- This is sampled content validation, not exhaustive feature comparison or browser/mobile rendering certification. No client/style deployment occurred. Embedded Planetiler buildtime/githash describe the dependency; use this run's frozen source/JAR hashes as build provenance.

## Measured time and estimated cost

- End-to-end from first preparation launch to successful validation: **5h 36m** (02:47:40–08:23:35 UTC), including the failed initial trail attempt and retry.
- Successful global trail preparation: **85m 10s**, plus staging/upload.
- Tile generation: **3h 09m 38s**; upload/checksum/validation finished about 5 minutes later. Build host lifetime approximately **3h 21m** including provisioning.
- Generation stages: OSM pass 1 **2m**, OSM pass 2 **78m 17s**, trail snapshot import **13m 34s**, sorting **5m 32s**, archive **88m 25s**. Pass 2 and archive are the principal remaining bottlenecks.
- Peak generation RSS **130.18 GiB**, no swap. Index, intermediates and output stayed on local NVMe.
- Observed Spot pricing: m7gd.16xlarge us-east-1a **$1.2787/h**, elevation m7g.xlarge **$0.0858/h**, retry r7gd.16xlarge us-east-1d **$2.1202–2.1415/h**. Approximate compute total **$8.3 USD**, including failed prep; allow **$9–10** for this run's compute, short-lived root storage and S3 requests. This is an estimate, not a settled AWS invoice; excludes elevation endpoint billing, ongoing retained S3 storage and future downloads/egress.

## Final cleanup

Verified no active/stopped run instances, no remaining root disks for all four instances, no run multipart uploads, and no checkpoint objects. Removed temporary role/profile `outmap-planet-build-20261025-r1` and both attached policies. The local monitor exited successfully and no replacement build was launched.

Retained deliberately: final archive, completed elevation/trail snapshots (release inputs), frozen source/JAR/scripts, checksums, logs and failed-attempt provenance. Existing cached full PBF/auxiliary sources and earlier pilot archives remain untouched. These retained S3 objects continue to incur storage charges; temporary compute, EBS and NVMe are gone. There is no separate disposable staging prefix beyond the retained provenance bundle.

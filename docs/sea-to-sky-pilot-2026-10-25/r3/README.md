# Sea to Sky r3: snapshot lengths and conservative pruning

Completed: full-PBF Spot generation, archive/content checks and four-location rendering checks passed. All three r3 instances are terminated; temporary IAM role/profile removed.

## Scope

The generator reads the same full 95,507,771,401-byte planet-261019.osm.pbf as r2, verified against MD5 5bbb8c9cae945af88e6af773fed33a3e. Output bounds remain -123.75,49.25,-122.25,50.65 and zooms 0–14. No production archive or style is changed.

- Prepared junction-to-junction length labels replace the global in-build graph for this run. Existing eligibility (paths, footways, steps, tracks with access/type exclusions) remains.
- z12/13 POIs retain ranks 1–12, transit and outdoor-priority exceptions. No POI minzoom changes. z14 retains all data except the explicitly approved removals.
- Kerbs and explicitly mapped sidewalks are excluded before layer processing. Ordinary roads tagged sidewalk=yes/both/separate are retained, as are crossings.
- Current web and mobile style files retain their recorded SHA-256 hashes. New custom layers are still unstyled, by the user's choice.

## Estimate made before regeneration

See [prebuild estimate](PREBUILD-ESTIMATE.md): 10.5% addressed gzip payload reduction, largest tile approximately 65% smaller. This excludes sidewalk savings and added length-label bytes; it is not a measured r3 archive size.

## Measured preparation

The successful retry ran on m7g.4xlarge Spot in us-east-1d. Full source download/checksum/extraction/snapshot/upload took 16m55s from script start to completion. Buffered complete-way extraction took 8m13s. The compact graph stage took 15.58s wall time with maximum RSS 902,660 KiB (~0.86 GiB), processing 1,457,135 nodes and 1,449,828 unique edges.

The saved snapshot has 95,768 labels, including 40,658 anchors inside output bounds. Rejected chains include short chains (<25m) and uncertain boundary/invalid chains; the count is not a count of missing eligible trails. Snapshot SHA-256 verified after download. See [manifest](trail-segments-manifest.json) and [counts](snapshot-counts.json).

The first m7g.xlarge Spot preparation was interrupted by AWS during checksum verification. Its local work was lost; no output was published. The retry completed and automatically terminated. Both preparation instances have no attached volumes remaining.

## Implementation and validation boundaries

The new preparer uses primitive indexed nodes, packed deduplicated edges, compressed adjacency and one traversal per edge. A two-pass buffered complete-way extract supplies coordinates and excluded-highway junction markers. Chains touching uncertain outer topology are omitted rather than shortened. Snapshot checks bind the output to its checksum, policy and parent planet MD5; the runner separately verifies the PBF.

The tile reader emits the existing trail_segment_label schema at z14 and does not register an OSM graph handler. The legacy path remains available when no snapshot is supplied; non-way/non-highway filtering now precedes its synchronized processing.

Compilation and diff checks passed. No unit tests, repository typechecks or mapping regeneration were run. A real Monaco archive passed archive verification and contained the snapshot layer; comparison against the original implementation found eleven nearest-anchor length differences of one metre, all at the same quantized position. The preparer uses original OSM coordinates whereas the legacy graph uses a quantized coordinate cache. This is a manual archive inspection, not a unit-test harness.

This regional compact graph does not prove worldwide graph memory scalability or full-world preparation time. Worldwide partition reconciliation/disk-backed preparation remains unbenchmarked. It must not be quoted as a measured full-world result.

## Results

| Measure | Previous r2 | New r3 | Change |
|---|---:|---:|---:|
| Archive bytes | 63,500,379 | 56,526,685 | −10.98% |
| Addressed gzip payload | 63,462,093 | 56,495,300 | −10.98% |
| z12 payload | 10,469,926 | 6,783,174 | −35.21% |
| z13 payload | 15,680,907 | 13,023,610 | −16.95% |
| z14 payload, including new lengths | 30,491,615 | 30,228,743 | −0.86% |
| Former worst tile, 12/647/1402 | 794,278 | 239,266 | −69.88% |
| Largest tile anywhere | 794,278 | 360,772 | −54.58% |
| Generation wall time | 47m26s | 36m01s | −24.1% |
| Second OSM pass | 29m16s | 18m00s | −38.5% |
| Trail snapshot ingestion | disabled | ~1s | 40,658 labels |

The savings estimate was 10.5%; measured net savings are 11.0%, including sidewalks and the new labels. The new worst tile is 14/2588/5607. Of its 695,487 uncompressed bytes, POIs account for 443,614 (63.8%), transportation 94,932 (13.6%), buildings 73,372 (10.5%), and house numbers 32,503 (4.7%). This z14 detail was intentionally retained. Layer byte shares are raw protobuf sizes, not additive gzip attribution.

Matched interior tiles are still 25.1% larger than the cached production baseline, down from 34.5% for r2. By zoom, r3 is +5.1% at z12, +11.1% at z13 and +35.8% at z14. The older production planet and added data differ; this is a delivery-size comparison, not a same-input equivalence benchmark. Production max z12 tile is 463 KB versus r3's 243 KB. See [production comparison](production-benchmark.json) and [tile bulk](tile-bulk.json).

## Validation

- Uploaded/local SHA-256 match: `4fcef684098936abcfeb19aba010ba1e7aa0c0694e27931fb137ac8ac95d0170`.
- PMTiles verification passed; all 9,303 tiles decoded, with all previous tile coordinates retained.
- No missing currently visible z12/13 POIs and no newly hidden retained POIs under the inspected current-style rank/transit rules. No minzoom changes; no recommendation to lower them in this pass.
- All 40,658 in-bounds snapshot IDs emitted at z14 only, with matching length, bearing and loop values.
- No internal pruning marker, kerb class or excluded-source ID remains in the checked tile features.
- z14 retains all previous layer IDs except approved exclusions and four transportation merge-representative IDs. All four old merged geometries are covered by explicitly removed source-way geometry within 0.5 tile pixels, with zero unexplained length. Their own source links are <0.3 pixels at z14. See [reconciliation](merged-id-reconciliation.json); merged feature IDs are not one-to-one OSM coverage counts.
- All 4,096 z14 mountain-layer IDs and their attribute maps exactly match r2, including elevation values.
- Squamish, Whistler, Pemberton and Howe Sound rendered with the existing style and zero reported map errors. Style SHA-256 hashes remain unchanged. This is desktop preview validation, not a physical-device/offline or exhaustive pixel-equivalence certification.

See [archive validation](validation.json), [elevation parity](elevation-parity.json) and saved preview PNGs in this directory. Previously documented invalid-source-geometry limitations remain; this does not certify every source feature worldwide.

## Timing, cost and cleanup

Successful preparation plus build/staging/upload stages took **59m17s**. Generation itself took **36m01s**, and maximum process RSS was ~146 GiB on the 256 GiB m7gd.16xlarge. Successful build-instance lifetime through upload was ~43m06s. Including the interrupted first preparation and the retry/handoff gaps, first launch to final upload was **75m38s**. Spot interruption made wall-clock completion longer than the successful-stage sum.

Historical rates: preparation retry $0.2836/hour; build $1.539/hour. Estimated compute is **$1.20**, conservatively including the interrupted attempt. Allow approximately **$1.25–1.40 total for this run** including temporary disks, IPv4 and small transfer/request costs, before tax. This is a rate-times-runtime estimate, not an AWS billing receipt. Retained private S3 inputs/artifacts continue to cost roughly $2–3/month and are separate. See [cost estimate](cost-estimate.json).

All r3 instances terminated, delete-on-termination disks have no remaining instance attachments, and the temporary IAM role/profile were deleted. See [cleanup](cleanup.json). The archive remains private at `s3://outmap-planet-pilot-442073793062-20261025/r3/sea-to-sky-20261025-r3.pmtiles`; no public URL or production publication was created.

For a whole-planet output, the earlier 2–6 hour large-compute envelope would cost **$3.08–9.23** at this measured Spot rate, before global preparation, staging and storage. This pilot does not establish a new full-world runtime or a total including global trail preparation. Do not extrapolate the 15.6-second regional graph directly to the world.

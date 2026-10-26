# Sea to Sky AWS pilot — 2026-10-25

Latest: [r3 Spot rerun — trail snapshot, conservative pruning, kerb/sidewalk removal](r3/README.md). Archive 56.53 MB, generation 36m01s, all pilot compute terminated. The original r2 measurements below remain as historical baseline.
Completed successfully using the **full 95.5 GB planet PBF**, with regional output bounds and z0–14. Only experimental trail-distance labels were disabled, with explicit user approval. No production tile source was changed.

See the follow-up [production comparison and trail-distance review](production-comparison/README.md) for measured size differences, zoom checks and unresolved coverage limits.

## Results

- Archive: `s3://outmap-planet-pilot-442073793062-20261025/results/sea-to-sky-20261025-r2.pmtiles` (private).
- Local archive: `/private/tmp/outmap-sea-to-sky-20261025/sea-to-sky-20261025-r2.pmtiles`.
- Archive size: **63,500,379 bytes**, SHA256 `f49bfee99f1b80b2e22ceb6d3717c869f140bf1e88bade353f5d8a379ef5408a`.
- Local preview: http://127.0.0.1:8094/ while the local preview and tile server processes remain running.
- Exact output bounds: west −123.75, south 49.25, east −122.25, north 50.65.
- Input: `s3://osm-pds/2026/planet-261019.osm.pbf`, MD5 `5bbb8c9cae945af88e6af773fed33a3e`; downloaded and checksum verified before starting large compute. No regional extract substituted.

## Measured time and estimated cost

USD, us-east-1 Linux On-Demand; estimates from runtimes and rates, not a settled AWS invoice.

| Stage | Measured time | Instance | Estimated compute |
|---|---:|---|---:|
| Preparation: planet, checksum, auxiliary inputs, global peak scan, corridor elevation snapshot | 22m37s | m7g.xlarge, $0.1632/hour | $0.062 |
| Copy prepared inputs to local NVMe | 4m44s | c7gd.16xlarge | $0.23 |
| Java generation, including process startup | 47m26s | c7gd.16xlarge, $2.903/hour | $2.29 |
| Successful build stage including upload | 52m13s | c7gd.16xlarge | $2.53 (includes the preceding two rows) |

A clean preparation plus successful build is **74m50s**, excluding boot/resize coordination and local QA. Budget **75–85 minutes and $3–4** for a fresh pilot, or **55–60 minutes and about $3** when reusing prepared inputs. This session also included an aborted 28-minute build and a failed bootstrap; allow **$4–5 total AWS run cost**, including those attempts, brief EBS provision, requests and public IPv4. Retained storage is additional.

The successful instance ran approximately 17:06:51–18:00:05 UTC. The generator's own stage summary is 47m22s: first OSM pass 17m13s, second pass 29m16s. It read 10,949,467,954 nodes, 1,225,912,321 ways and 14,957,553 relations. See `build-r2.log` and `build-status.json`.

About **97.4 GB** remains in the private bucket: full planet, auxiliary inputs, frozen JAR/source bundle, global peak exports, snapshot, logs and output. Budget **$2–3/month** for this retained input/output set, before future versions or downloads. [AWS S3 pricing](https://aws.amazon.com/s3/pricing/).

## Whole-planet planning estimate — not a measured world build

The pilot measures full-planet ingestion, but only regional feature output. It does **not** measure global tile generation, sorting or archive size. Do not scale 63.5 MB or the regional rendering time by geographic area.

For a first world z0–14 run, provisionally budget **2–6 hours of large-instance work**, plus **1–3 hours of cheap preparation**, for approximately **3–9 hours end-to-end and $10–30 per run**. These are engineering planning ranges, not demonstrated bounds. They assume the same trail-distance exclusion, no additional datasets, no paid elevation API charges, no repeated failed runs, and no delivery/hosting traffic. The user's current exclusion approval applies only to this pilot; a world run needs its own decision about that layer.

The proposed next benchmark machine is **m7gd.16xlarge**, 64 vCPU / 256 GiB / 2 × 1.9 TB NVMe, **$3.4171/hour** from the AWS pricing API (`next-run-memory-option.json`). Its compute component at 2–6 hours is $6.83–20.50; remaining allowance covers staging, cheap prep and temporary storage. It has not been launched or benchmarked. The current 128 GiB run warned that ~111 GB of mapped node/geometry data exceeded ~81 GB available cache, and way processing was initially I/O-bound. More RAM is a justified next experiment; it is not a proven speedup.

Upstream's published historical world example took 3h21m on 16 CPUs/128 GB with a smaller 64.7 GB PBF and produced 99 GB MBTiles. That is context, not a forecast for this custom profile or PMTiles. Upstream recommends disk capacity of at least 10× the input PBF size. Allow at least ~1 TB scratch and measure actual global output before setting a storage budget. [Planetiler world guide](https://github.com/onthegomap/planetiler/blob/main/PLANET.md).

For a provisional 100–200 GB world archive, incremental S3 retention would be roughly $2–5/month; that output size is an assumption, not a result. Internet delivery costs are excluded and depend on traffic.

## Predownload and elevation workflow

1. Use small compute to freeze the dated planet input, verify its checksum, download auxiliary sources and build/store the exact JAR and source bundle.
2. Scan the full PBF once for peak/saddle/volcano nodes. Save the global export so later elevation preparation can resume without another planet scan.
3. Complete the elevation snapshot on small compute. Freeze its input hash and checksum before launching large compute.
4. Start large compute only after the inputs are ready; copy them to local NVMe, run with `--download=false`, upload output/status/logs, then stop automatically.
5. Verify output and delete disposable compute, disk and role. Retain reusable inputs in private S3.

The measured corridor preparation found 1,217 candidate natural features in buffered bounds and **61 elevation gaps**. All 61 were sampled successfully in one batch, about **1.9 seconds** for the backfill process. Thus serial batches of 250 are not a meaningful pilot bottleneck.

The global export contains **1,202,166** peak/saddle/volcano nodes and **357,665 preliminary candidates** without plain numeric elevations, potentially **1,431 batches**. This is an upper-level candidate count, not exact API demand: unit-bearing elevations may parse successfully, and coverage constraints still apply. At an assumed 2–5 seconds per full batch, serial calls alone would take **48–119 minutes**; a 61-point batch does not establish full-batch latency. Global backfill was not run. Endpoint capacity, rate limits, no-data frequency and marginal endpoint cost remain unmeasured. Prepare it ahead using the existing resumable script before considering concurrency changes.

## Validation

- SHA256 matches the uploaded status record.
- `pmtiles verify` passed; metadata bounds and z0–14 match the requested output.
- Decoded **all 9,303 addressed tiles** successfully, beyond archive-structure verification.
- z14 includes 18 populated layers, including `outdoor` and `polygon_label`; `trail_segment_label` is absent as approved. Every other layer configuration stayed enabled; this is not a paired feature-parity comparison.
- **60 emitted snapshot points** have correct rounded metre/foot values; **1,022 existing numeric OSM elevations** match original rounded metre values; no mismatches. The 61st snapshot point, `n5760189162`, lies east of output bounds at longitude −122.2280064 and is correctly absent.
- Browser checks: Squamish 3,088, Whistler 2,161, Pemberton 1,799, Howe Sound 9,987 rendered features; zero reported renderer errors at each checked view. Screenshots are stored beside this report.
- Largest compressed tile: **794,278 bytes**. z14 median 1,593 bytes; p95 12,336; p99 75,252; max 389,724. Several z12 tiles exceed 1 MB uncompressed, largest warning ~2 MB. Mobile performance remains unverified.
- Global boundary warnings occurred during ingestion; no build failure. Logs retain them for investigation.
- Preview uses the existing light vector style. Terrain, hillshade and contour services were excluded from this local preview to inspect this archive; this does not alter production styles. New feature categories without existing styling were validated by decoding, not complete visual acceptance.
- No device/offline acceptance or full-world output check performed. Unit tests and repository typechecks were not run, per repository instructions. Build packaging completed with tests and formatting changes disabled; no upstream regeneration was run.

## Initial blocker and approved exclusion

The first run sampled 54 of 62 worker threads blocked on synchronized `TrailSegmentLabels.processAllOsm`. Its global trail graph also has a 2-million-node default cap. A cap failure was inferred from source/global workload, not observed as a thrown error: the run was stopped first.

The user approved `--trail-segment-labels=false` for this pilot only. The same frozen JAR was rerun; no source feature was removed. Second-pass nodes improved from ~3.1 million/sec to ~35 million/sec. The default layer remains enabled in application code and still needs an approved global-scale implementation before a world build with distance labels.

## Reproducibility and cleanup

`run-plan.json`, `run-pilot.mts`, input checksums, `bundle-manifest.json`, logs and archive/elevation analysis capture this run. The frozen source includes the checkout's existing changes; Git HEAD alone is insufficient to reproduce it. The PMTiles `planetiler:githash` and `planetiler:buildtime` fields describe the underlying Planetiler dependency, not this custom build. Use the frozen JAR SHA256 `5410bb2f64c598611a817d7d49fba66b7aaecc026a3e12f15437c295dee9a591` and source bundle.

Instance `i-0e8a6e44234352f3c` was automatically stopped on completion, then terminated after validation. Root volume `vol-0f94c42cbd859db6b` was verified absent. Temporary IAM role and instance profile `outmap-planet-pilot-20261025` were removed. The private bucket remains for reproducibility; no production publication occurred.

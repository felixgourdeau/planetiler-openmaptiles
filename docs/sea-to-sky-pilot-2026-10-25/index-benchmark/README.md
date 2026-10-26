# Isolated full-planet node-index benchmark

User approved running only the full-planet first OSM pass on Spot, without pilot/tile regeneration. Completed successfully. Both runs used the same instance and unchanged r3 JAR. The instance is terminated and its temporary IAM role/profile are deleted.

## Fixed inputs and configuration

- Full planet PBF: `planet-261019.osm.pbf`, 95,507,771,401 bytes, MD5 `5bbb8c9cae945af88e6af773fed33a3e`.
- Existing r3 JAR, unchanged, SHA-256 `8da74801cee001a588690c9d4fc99eba076abff930515a93d6314446d9cfe4c4`; Planetiler 0.9.1.
- `m7gd.16xlarge`, 64 vCPUs, 256 GiB RAM, 48-GiB Java heap; input, node map and polygon scratch on local instance-store NVMe. Root EBS holds only bootstrap/JAR/logs.
- Sequential fresh JVMs: sparsearray then array. Both use mmap and madvise; OS page cache explicitly cleared before each pass. Cold-cache timings only, not warm-cache results.
- Same existing Outmap profile and relation preprocessing, with r3 bounds and snapshot-mode layer registration. No snapshot features are read because pass2/feature processing is never invoked. Translations use en/de; they do not participate in pass1.
- Runner directly invokes `OsmReader.pass1`; no tile encoder/archive writer is constructed, no pass2 is invoked, no generation output path exists.
- Each result includes timing, map-size estimates, data-error counters and a deterministic 100,000-ID node-lookup fingerprint. This is a consistency sample, not full index equality, full profile concurrency certification, or rendered-output equivalence.

## Safety and lifecycle

One-time Spot, price ceiling $1.65/hour. Temporary role reads only the cached PBF/JAR/benchmark bundle and writes only benchmark result prefix, plus Systems Manager access. Existing security group has no public inbound rules; no SSH key. IMDSv2 required. Shutdown behavior is terminate, root EBS DeleteOnTermination, and a 60-minute shutdown timer bounds costs. Runner uploads logs every 30 seconds and shuts down after either success or failure. Local monitoring verifies termination and removes temporary IAM resources afterward.

Evidence: runner source in `Pass1Benchmark.java.txt`, `run.sh`, `bootstrap.sh`, and `instance.json`. The Java file is a manual performance diagnostic compiled against the existing JAR, not a unit test or repository test harness. No dependencies added, repository typechecks/tests run, or production configuration changed.

## Results

| Metric | sparsearray | array |
|---|---:|---:|
| Full first pass | 868.40 s (14m28s) | 152.20 s (2m32s) |
| Node phase | 11m59s | 1m42s |
| Whole diagnostic JVM, including startup/sample lookup/close | 14m37.51s | 2m39.52s |
| Index file bytes (decimal GB) | 99.33 GB | 114.49 GB |
| Peak process RSS | 36.95 GiB | 98.45 GiB |
| Sample lookups / found | 100,000 / 68,727 | 100,000 / 68,727 |
| Sample lookup SHA-256 | identical | identical |
| Reported data errors | 0 | 0 |
| Swap operations | 0 | 0 |

**5.71x faster for the full first pass; 82.47% less elapsed time; 11m56s saved.** The array index needs an additional 15.15 GB of index file space. RSS includes resident mappings and is not the complete machine/page-cache footprint; the 256-GiB machine is the measured configuration, not evidence that a smaller instance is equally fast.

Both runs processed exactly 10,949,467,954 nodes, 1,225,912,321 ways and 14,957,553 relations. Their deterministic sampled lookup fingerprints and estimated reader-memory totals match. No preprocessing errors were found in either log. Source review found synchronized/shared concurrent structures in the profile's relevant preprocessing handlers. None of these checks substitutes for pass2, all-index equality, complete relation-state equivalence or rendered-output comparison; no claim of end-to-end tile equivalence is made.

Recommendation for the next authorized build: explicitly select `--nodemap-type=array --nodemap-storage=mmap`, retain 256 GiB RAM, the measured 48-GiB heap, and local NVMe for all input/intermediate files. No production/default generation flags have been changed in this task. Avoid scaling this 5.71x stage improvement to the entire planet pipeline: pass2, sorting and archive encoding still remain. The measured fixed saving is about 12 minutes relative to the same-machine sparsearray first pass.

## Lifecycle and cost

Instance `i-03eff3f4e503145ed`, us-east-1a, launched 2026-10-26 02:01:44 UTC. Runner completed 02:26:21 UTC with exit code 0; termination was verified before IAM cleanup. Spot price was $1.2787/hour. Estimated compute is $0.53–0.55; approximately **$0.55–0.60 total transient cost** including small root-disk/public-IP/request overheads, excluding existing retained S3 inputs, tax, and ongoing evidence storage. This is a usage estimate, not a reconciled invoice.

`cleanup.json` confirms terminated state, deleted temporary IAM role/profile, and zero remaining attached volumes. Root EBS was configured DeleteOnTermination. Benchmark logs/results remain in the dedicated private S3 prefix and this report. The pilot PMTiles hash remains `4fcef684098936abcfeb19aba010ba1e7aa0c0694e27931fb137ac8ac95d0170`; no tiles or pilot were regenerated.

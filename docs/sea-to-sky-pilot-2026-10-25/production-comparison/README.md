# Production comparison and trail-distance review

Read-only audit on 2026-10-25. No compute was launched, no application code changed, no permissions changed and no archive published. The pilot remains private: 63,500,379 bytes (63.50 MB / 60.56 MiB). S3 public access is blocked. Its S3 URI is in the parent report; it is not a public download URL.

## TL;DR

The pipeline produced a valid, renderable regional archive from the full planet input, with completed elevation preparation. It is not yet ready for an unconditional production replacement. It adds useful content and attributes but is larger, two new layers have no production styling, the trail-length layer is excluded, and some differences from the old archive remain unexplained.

## Method

The live style at `https://outmap.pro/style/outmap-v7-light.json` and the inspected web/mobile source files use `https://planet.tiles.outmap.pro/planet-250825/{z}/{x}/{y}.mvt`, maxzoom 14. R2 stat confirms `planet-250825.pmtiles` is **89,749,023,592 bytes**, last modified 2025-09-03. This is a world archive, so its total size cannot be compared directly with the corridor pilot.

Fetched all **9,303** production tile coordinates addressed by the pilot; all returned HTTP 200. Decoded every production and pilot tile. Production HTTP responses used Brotli; for a fair content-size comparison, decompressed both sets and recompressed with the same Java gzip implementation. These are normalized compressed tile bytes, not observed HTTP transfer bytes or a repacked archive size. Fetch timings are not a controlled latency benchmark.

For the main size comparison, retain only XYZ tiles entirely inside the output bounds. This removes clipping bias from boundary tiles; source buffers and OSM changes still affect results. The same coordinates were used on both sides. Low-zoom tiles extend far beyond the region, so their category differences are not treated as proof of regional omissions.

## Matched interior tile sizes

Decimal MB/kB; sums include every addressed tile, without PMTiles deduplication or directory/metadata overhead.

| Zoom | Matched interior tiles | Production gzip sum | Pilot gzip sum | Change | Production p95 | Pilot p95 |
|---|---:|---:|---:|---:|---:|---:|
| 9 | 4 | 0.193 MB | 0.205 MB | +5.9% | 56.8 kB | 62.7 kB |
| 10 | 20 | 0.614 MB | 0.825 MB | +34.3% | 53.9 kB | 87.7 kB |
| 11 | 96 | 1.625 MB | 2.259 MB | +39.0% | 48.9 kB | 77.9 kB |
| 12 | 408 | 4.901 MB | 6.940 MB | +41.6% | 40.8 kB | 57.7 kB |
| 13 | 1,632 | 9.966 MB | 12.704 MB | +27.5% | 15.9 kB | 22.5 kB |
| 14 | 6,664 | 20.337 MB | 27.674 MB | +36.1% | 7.8 kB | 11.3 kB |
| Total | 8,824 | 37.637 MB | 50.606 MB | +34.5% | — | — |

The largest pilot tile, `12/647/1402`, is 794,278 gzip bytes versus production's normalized 458,212 (+73.3%). It intersects the southern output boundary and is not in the interior table. The generator identifies POIs as its dominant payload (~1.7 MB uncompressed). Across all matched z12 tiles, 255,967 of 284,527 pilot POI feature occurrences have rank >=20. The current generic style does not display those until display zoom 17, when it uses z14 source tiles. This supports evaluating the existing low-zoom POI-pruning proposal, while retaining transit/outdoor exceptions and complete z14 data. No pruning was applied by this audit.

## Content

Unique feature-ID counts over all matched z14 tiles (includes buffer features). Geometry merging can change IDs/feature counts, so these are not necessarily counts of distinct real-world objects. Production and candidate use different source dates and generator versions; differences cannot all be attributed to code changes.

| Layer | Production IDs | Pilot IDs | Shared IDs |
|---|---:|---:|---:|
| POI | 45,206 | 114,224 | 43,884 |
| Transportation | 30,055 | 64,500 | 29,210 |
| Mountain peak, including ridges/cliffs | 3,468 | 4,096 | 3,443 |
| Building | 2,955 | 11,047 | 2,922 |
| Landcover | 24,668 | 29,300 | 24,303 |
| Outdoor | 0 | 2,791 | 0 |
| Polygon label | 0 | 2,007 | 0 |

All 16 populated production layers in this tile set exist in the pilot, plus `outdoor` and `polygon_label`. New content includes marine subtypes, barriers, piste geometry, via ferrata, outdoor hazards and supplemental attraction categories. The pilot also carries `sac_scale`, `trail_visibility`, POI websites and opening hours, absent from the corresponding production fields in this sample. Counts and attribute inventories are in `benchmark-summary.json`.

### Differences requiring care

The focused interior z14 ID audit checked POIs, mountain features, water names and aerodrome labels against all pilot z14 IDs. **1,308** production IDs were absent: 1,223 POIs, 61 water names and 24 mountain features. **403** have a same-name candidate under another ID; that is a reconciliation lead, not proof that every pair is the same object. **905** remain unresolved, many unnamed. These are not 905 confirmed regressions.

Concrete replacement examples verified in the new archive:

- Hollyburn Mountain: old node 486416553, new node 14279314027; new full-planet peak export and archive both contain the new node, elevation 1326 m.
- Mount Dickens: old node 746014449, new node 14232165689; new export and archive both contain the new node, elevation 1288 m.
- Same-name replacements also exist for Cal Cheak Central Camp, SORCA Shelter, Chek Canyon Camping Area and Viewpoint Beach Campground.

A same-input old/new generator run, or targeted reconciliation against the frozen PBF, is needed to distinguish source edits from generator omissions. This audit did not scan the full PBF again or start compute to resolve all of them.

## Zoom and pipeline checks

Observed archive availability matches these frozen source rules:

| Content | First observed tile zoom | Rule / client implication |
|---|---:|---|
| Peaks and volcanoes | 7 | Matches generator; style starts at 7 with rank filtering |
| Ridges and cliffs | 9 | Matches generator; their line rendering is a separate style concern |
| Saddles | 11 | Matches generator; current saddle style intentionally starts at display z14 |
| Polygon labels | 5 | Size-dependent admission, minimum z5; no current production styling |
| Ordinary POIs in this region | 12 | Matches common rule; large colleges/universities may start at 10, but none passed that early size threshold in this archive |
| Buildings | 13 | Present through z14 |
| Camp pitches | 14 | Matches generator; retained for overzoom |
| Supplemental outdoor geometry | 14 | Matches generator; absent below 14 |
| House numbers | 14 | Present in data; current inspected style does not consume this layer |
| Trail-distance labels | Absent | Explicit approved pilot exclusion |

Both current vector sources cap at z14. Display zooms 15–22 reuse z14 tiles, so rank-based POIs intended for display z16/17 must remain in z14 data. Tile availability and visible labels are different: style filters, icons and collision rules control actual display.

The live web light style and inspected mobile style do not reference `outdoor`, `polygon_label` or `trail_segment_label`. Changing the archive URL alone will not show these layers. Thus an assertion that all new features are visible at the right zoom would be false.

Pipeline evidence: checksum-verified full PBF; auxiliary downloads completed before large compute; frozen elevation snapshot loaded with 61 usable records and logged SHA256; `--download=false` used during generation; exit status 0; uploaded archive hash matches local bytes; archive verification and all-tile decoding passed; prior manual render checks passed in four locations. The standalone decoder is an archive inspection, not a unit test. No unit tests or typechecks were run.

The generator logs contain invalid-geometry counters, including 388 generic multipolygons empty after repair, 43 polygon-label geometry failures and 33 park-area geometry failures. These counters cover processing, are not necessarily unique lost regional objects, and have not been spatially reconciled. Complete source-feature coverage, pixel-level correctness, physical-device performance and offline acceptance are not certified.

## Why trail lengths blocked the run

Frozen source and current `TrailSegmentLabels.java` match. The eligibility filter selects `highway=path|footway|steps|track`; excludes sidewalks/crossings, indoor/area features, prohibited foot access and disused/construction features. It does **not** calculate distances for ordinary roads. However, tracks and footways are broader than hiking trails, and there is no hiking-route relation filter.

Two independent scale problems exist:

1. `processAllOsm` is synchronized at method entry, so every OSM feature takes the shared lock before even checking whether it is a way or highway. The sampled thread dump had 54 of 62 workers blocked. With the whole layer disabled, second-pass node processing rose from ~3.1M/s to ~35M/s.
2. `preprocessOsmWay` collects eligible nodes from the entire planet into Java objects; default limits are 2 million nodes and edges. Regional output bounds do not restrict that preprocessing graph. The anticipated cap error was not observed thrown because the first attempt was stopped before finish.

Excluded highways are inspected only to mark junctions where a hiking segment should end. That is useful topology work; it does not require emitting road lengths. The current calculated length is horizontal geodesic length, not elevation-adjusted hiking distance.

### Preparation options — proposal only

**Recommended: a frozen hiking-segment snapshot before large compute.** Select hiking-eligible ways from the same dated full planet PBF; retain their complete node geometry and intersecting-road junction markers; compute lengths before clipping/simplification; save midpoint/anchor, bearing, length, loop status, input hash and filtering-policy version. The tile build continues to read the full planet PBF and simply reads prepared labels. For the world, use disk-backed graph processing or partitioning with explicit cross-partition topology reconciliation. A regional prefilter must retain whole connected segments or mark incomplete chains; clipping at a bbox and labelling the shortened length would be incorrect.

A proposed hiking policy is: eligible `highway=path` unless access/type tags disqualify it; add footway/steps/track only with hiking evidence such as membership of a hiking route or explicit hiking tags. Preserve access exclusions. A stricter route-members-only policy is simpler but misses unmarked trails. This choice affects public coverage and needs approval before implementation.

**Smaller alternative:** prefilter non-way/non-highway features before locking, narrow eligibility, and bound graph collection for regional work. This removes unnecessary contention with less code, but does not by itself solve world-scale graph storage or safe regional segment boundaries. Raising the cap alone is not a robust fix.

Affected systems/files would include `TrailSegmentLabels.java`, `ExtraLayers.java`, a new snapshot preparation/reader contract and `run-pilot.mts`. No such architecture, feature-policy or dependency change was made here. Neither option has a measured preprocessing runtime yet.

## World time and cost

Retain **3–9 hours / $10–30** as a low-confidence planning envelope for the current profile **without trail-distance processing**: 1–3 hours of inexpensive input/elevation preparation and 2–6 hours of large compute. The proposed m7gd.16xlarge rate, verified earlier this day through AWS pricing, is $3.4171/hour; 2–6 hours costs $6.83–20.50 compute, with remaining allowance for staging, preparation and temporary storage. This is not a quote or a measured world run. The pilot exclusion approval does not authorize excluding the layer in a future production world build.

The measured full-planet passes take ~46.5 minutes, but regional sorting/archive writing take only seconds. World output has much more feature processing and encoding, so 47 minutes is not a world-build estimate. The regional +34.5% byte increase is also not a valid global scaling factor. Upstream's historical smaller-world example and storage guidance are context only: https://github.com/onthegomap/planetiler/blob/main/PLANET.md.

A prepared hiking-distance snapshot could move graph work off large compute and make reruns cheap, but its first global preparation duration/cost remains unknown and is **excluded from the estimate**. Benchmark that stage before quoting a total including lengths. Recurring storage, delivery traffic, endpoint charges and retries are excluded. The existing ~97.4 GB private retained pilot set costs approximately $2–3/month; no pilot EC2 instance or disk remains.

# Climbing POI removal: size-only audit

No climbing records were removed from source or the archive. All 9,303 r3 tiles were decoded, filtered in memory, re-encoded and gzipped against an equally re-encoded baseline. No replacement archive was produced.

| Candidate | Distinct feature IDs | Tile/zoom occurrences | Gzip saving now | Gzip saving after approved name compaction |
|---|---:|---:|---:|---:|
| POI class or subclass exactly `climbing` | 592 | 3,819 | 142,983 B (0.253%) | 112,995 B (0.207%) |
| Above plus climbing_adventure and sport containing climbing | 606 | 3,861 | 146,073 B (0.259%) | 115,560 B (0.211%) |

Baselines are 56,489,837 B before name compaction and 54,687,157 B afterward. These are addressed gzip-payload sums, not rebuilt archive sizes; archive indexes/deduplication may alter the final delta. Do not extrapolate this climbing-rich regional sample to a worldwide saving. Counts of IDs are serialized POI identities, not validated unique physical places. The broad scenario additionally removes 8 climbing-adventure and 34 sports-centre occurrences; do not assume these facilities are present in the content layer.

The current web v7 style has climbing area, line, route-label, route-endpoint and location layers using source `content`, source-layer `fb_items`. This confirms that content is already the dedicated climbing display path. It does not establish complete OSM overlap, regional/global coverage, matching minzooms, or availability in every offline pack/client version. No live content archive coverage join or native/offline audit was performed.

Recommendation: use the content layer as the canonical climbing experience, but do not remove all base-map climbing purely to save about 0.2% after name compaction. First validate coverage of these 592 candidate IDs, including gyms, boulders and crags, and offline availability. Retain natural cliffs/rocks, approach trails, access infrastructure, peaks and via ferrata; these are not included in this POI-only removal measurement. Deduplicating a verified overlap could improve presentation independently of size, but no removal or style change is implemented here.

Separately authorized: `OpenMapTilesMain` now defaults node-map type/storage to array/mmap using Arguments.withDefault, preserving explicit overrides. The production release runbook records these flags, the measured 256-GiB host/48-GiB heap, and local NVMe placement. Existing JARs require the flags explicitly until rebuilt. Temporary Java compilation and diff checks passed; two existing upstream API deprecation warnings remain. No unit tests, repository typechecks, JAR rebuild, AWS launch or pilot regeneration occurred in this follow-up.

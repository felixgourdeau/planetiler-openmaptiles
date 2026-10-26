# Remaining attribute redundancy and POI size audit

Read-only archive investigation on r3; no generator, style or PMTiles changes. The separate first-pass index benchmark does not generate tiles. The current trail distance layer, POI availability/minzooms and z14 detail remain intact.

## Method

Decoded all 9,303 existing tiles. Each candidate is copied in memory, re-encoded and gzipped with the existing Planetiler encoder; no replacement archive is written. All scenarios start from the previously approved name compaction, whose re-encoded gzip baseline is 54,687,157 bytes. This controls for encoder dictionary-order differences. Results are sums of addressed tile payloads, not a rebuilt archive with fresh content deduplication/indexes. Feature occurrences repeat across zooms and tile buffers; counts below are not unique real-world objects. Independent scenarios are not additive.

## Measurements

| Candidate | Additional gzip saving | Percent of name-compacted payload | Recommendation |
|---|---:|---:|---|
| Remove `_raw` when identical `_normalized` remains, all layers | 768,462 B | 1.405% | Best individual redundancy candidate; preserve differing originals |
| Remove `_raw`/`_normalized` when identical unsuffixed field remains | 404,834 B | 0.740% | Also useful; requires consumer fallback contract |
| Combine the preceding rules | **961,328 B** | **1.758%** | Recommended next design decision, not implemented |
| POI-only `_raw` equal to `_normalized` | 78,404 B | 0.143% | Small subset of the first scenario |
| Remove POI subclass when equal to class | 66,197 B | 0.121% | Reject: existing style filters directly read subclass |
| Remove POI sport when equal to class/subclass | 7,621 B | 0.014% | Not worth changing the property contract alone |
| Remove POI rank | 910,434 B | 1.665% | Reject: existing style filters/ordering read rank |
| Remove POI website/phone/mobile/email/fax/opening hours | 789,371 B | 1.443% | Diagnostic only; reject information loss under current requirements |
| Remove exact duplicate POIs within a tile | 0 B | 0% | No such duplicates found |

The combined alias scenario leaves 53,725,829 bytes of payload. Together with the already-approved name compaction, the estimated saving against the original re-encoded baseline is 2,764,008 bytes (4.89%). Applying that payload delta to the current 56,526,685-byte archive gives approximately **53,762,677 bytes**, subject to fresh archive encoding/deduplication differences. This regional estimate must not be extrapolated directly to the planet.

The combined rule removes `_raw` only when it exactly equals a retained `_normalized`; it then removes suffix fields only when they exactly equal an unsuffixed value. Different raw values and different types remain. This preserves the values but changes which property keys exist. A consumer of `surface_raw`, for example, would need fallback to `surface_normalized` when the raw key is absent. Equal values across unrelated keys, such as `foot=yes` and `bicycle=yes`, are **not redundant semantics** and must not be removed.

## Compatibility and POIs

There are 326,306 serialized POI occurrences. All three current web/light/mobile style files retain their previous hashes. Static property extraction shows their POI layers depend on class, subclass, rank and the existing name fields. None of their POI layers directly references the raw/normalized fields. A text search of both apps' `src` trees also found no direct raw/normalized or outdoor obstacle/geometry key references. This is evidence for current rendering compatibility, not certification of dynamic clients, generic feature inspectors or future consumers. No style rendering or native/offline checks were rerun.

There are 164,613 POI occurrences with class equal to subclass, but removing these saves only 66 kB: MVT dictionaries already share the repeated strings. Keep both fields. Rank contributes more bytes but is operationally necessary. Contact aliases are already canonicalized by CoverageAttributes: `contact:website`/`website` and similar inputs are not blindly emitted twice.

No records repeat with the same layer, ID, geometry and attributes within a tile. The earlier Bulletheads North duplicate-inspector finding is therefore consistent with the archive-wide check. This does not prove there are no OSM node/area representations of the same physical place; proximity/name-based merging would require source-identity review and can erase legitimate nearby amenities.

The most common POI categories include parking, bus stops, piers, swimming pools, benches, gates and individual parking spaces. These counts alone do not justify removal. Dense-city simplification by dropping, clustering or deferring these objects would change feature availability/minzooms and is outside the current keep-z14/keep-visible-POIs requirement.

## Recommended next steps

1. Keep the already-approved name compaction.
2. Consider the combined exact alias rule only after approving its property-presence/fallback contract. Do not remove raw values that differ from normalized values.
3. Retain class, subclass, rank, real translations and the existing POI minzooms.
4. Keep contact/amenity details and z14 geometry. Moving detail into a separate service could reduce tiles but changes architecture/offline behavior and is not proposed for implementation here.
5. Continue the separate low-zoom transportation metadata investigation before sacrificing POI information; its earlier heavy-z10 sample showed a larger local benefit, but no archive-wide result is claimed.

Manual archive inspection and temporary Java compilation only. No unit tests, test framework, repository typecheck, full build, production upload or upstream regeneration.

## Dense-tile and semantic checks

A second scan also found no within-tile POIs with identical geometry and attributes while ignoring ID (rank remains part of the attributes). It does not rule out semantically duplicate places with different tags, ranks or geometries.

In the largest current tile, `14/2588/5607` (360,772 compressed bytes), diagnostic removal of the whole restaurant category saves 21,754 B; cafes 10,285 B; bicycle parking 9,916 B; benches 7,973 B; fast food 7,912 B. These independent ablations include geometry and all attributes and are not additive. There is no single obviously disposable category accounting for the remaining tile bulk. Removing them would violate the retained-feature requirement.

An important trap: 9,500 occurrences have `class=yes/subclass=yes`, but inspected examples are mapped **fords**, identified by `outdoor_obstacle=ford` and `ford_raw=yes`. Do not treat those classification values as disposable placeholders. A future classification/style audit could make them clearer, but this investigation does not change their visibility or tags. Detailed category ablations and examples are in `poi-detail.json`.

## Lossless encoding alternatives

An additional all-tile in-memory measurement preserves all name-compacted fields/values, IDs, geometry, layer/feature order, and only sorts attribute keys alphabetically before encoding. Gzip payload falls from 54,687,157 to **54,123,212 bytes**, saving **563,945 B (1.031%)** without removing another field. This is the safest next size optimization to consider, ahead of changing the alias contract. It is measured with the existing Java VectorTile encoder; the production insertion point still needs review because an intervening encoder map-copy could discard ordering. No production encoder was modified.

Increasing gzip from level 6 to 9 without key sorting produces 54,672,746 bytes: only **14,411 B (0.026%)** saved. Sorted keys at level 9 produce 54,102,620 bytes. Stronger gzip is therefore a low priority. Local compression timings in `encoding.json` exclude decoding and map sorting, are subject to JVM warmup/order effects, and must not be used as planet-generation speed estimates. These results are independent of the raw/normalized alias scenarios and must not be added without measuring the combined variant.

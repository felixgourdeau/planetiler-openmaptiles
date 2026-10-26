# Duplicate name compaction and Gaia distance packaging

Source changes only. No PMTiles or generator JAR rebuild, AWS job, deployment or style edit was performed. The r3 archive remains SHA-256 `4fcef684098936abcfeb19aba010ba1e7aa0c0694e27931fb137ac8ac95d0170`.

## Implemented rule

After each layer's final merging/ranking, remove name aliases and language-name fields only when their value exactly equals a nonempty `name`. Keep `name`, every distinct translation/international name, and `name:nonlatin`. Keep `name:latin` when a non-Latin field exists or `name_en` differs from `name` (including an empty English string). These exceptions preserve the current bilingual and Latin-first style expressions.

Compaction is deliberately after layer processing: removing aliases in the source-name helper would affect Place's Natural Earth fallback and could change labels or merging. No source names, ranks, geometry, IDs, zooms, or trail-label behavior change.

Affected code: `OpenMapTilesProfile.postProcessLayerFeatures` and `OmtLanguageUtils.removeRedundantNames`. Existing custom generated mappings were not regenerated.

## Compressed-size estimate without regeneration

Every one of the existing r3 archive's 9,303 tiles was decoded, copied in memory, compacted using the compiled new helper, re-encoded, and gzipped. The baseline was also re-encoded with the same encoder to isolate savings from encoding-order differences. No replacement archive was written.

| Measure | Bytes |
|---|---:|
| Original addressed gzip payload | 56,495,300 |
| Re-encoded baseline gzip payload | 56,489,837 |
| Compacted gzip payload | 54,687,157 |
| Estimated gzip saving | **1,802,680 (3.19%)** |
| Approximate archive after applying that saving | **54,724,005 (~54.72 MB)** |

The current archive is 56.53 MB. The estimated archive size assumes similar PMTiles deduplication/index overhead; the actual next build may differ slightly. This is a regional measurement, not a global extrapolation. Repeated MVT values share dictionary entries, so removing aliases mainly saves tag references and some dictionary overhead rather than another complete copy of each string.

The audit processed 1,120,259 feature occurrences and changed 203,479. It found zero distinct-value losses and zero changes to the current styles' name-selection expressions (base-name, English fallback, Latin-first fallback and bilingual components). Web light/full and mobile style hashes remain unchanged. Source compilation to a temporary directory and `git diff --check` passed. No unit tests, typechecks, full build or new-archive rendering checks were run. See [audit data](name-audit.json).

## Bulletheads North duplicate check

Decoded the exact pictured tile, `14/2587/5579`, directly from r3. There is **one** matching feature, in `poi`, ID `123057047351`, geometry `POINT (61.25 56.1875)` in tile-pixel coordinates. Searching both the ID and name yielded that same single record. Its five name properties are identical; compaction leaves `name: Bulletheads North`.

The repeated inspector rows do not correspond to duplicate serialized records in this tile. The specific viewer's cause (for example multiple rendered style layers or repeated query results) was not inspected, so no particular UI mechanism is asserted.

## Gaia GPS: verified style and decoded tile

Evidence was read from the publicly accessible source catalog, v6 styles, and one actual vector tile in the same region, without authentication or modification:

- [Public source catalog](https://www.gaiagps.com/mapsources/v2/gaiagps-ng-2019/)
- [Gaia Topo metric style](https://static.gaiagps.com/GaiaTopoGL/v6/gaiatopo-meters.json)
- [Gaia Topo imperial style](https://static.gaiagps.com/GaiaTopoGL/v6/gaiatopo-feet.json)
- [Sample vector tile 12/646/1394](https://vectortiles.gaiagps.com/osm4/12/646/1394.pbf)
- [Decoded evidence](gaia-tile-summary.json)

**Gaia does attach display distances to line geometry.** Its `trail` source layer mixes 1,175 LINE features and 343 POINT features in this sample. Of the line features, 320 carry `kilometers`, `miles`, and `mileage_minzoom`. Example: a trail line has `kilometers: "0.4"`, `miles: "0.2"`, `mileage_minzoom: 15`, and an `osm_id`. The distance values are preformatted strings, not an exact numeric metre field in the sampled schema.

Its metric mileage symbol layers read `{kilometers}`; imperial layers read `{miles}`. Both use `symbol-placement: line` and `symbol-spacing: 250`, with collision handling. Trail distance labels start at style z12 and track labels at z13, further gated by each feature's `mileage_minzoom`. The `gaiaosm` source caps at tile z12 and is overzoomed for later display zooms.

The same `trail` source layer also contains POINT features of type `trail_mileage` or `track_mileage`, styled as circles. Sample points have `miles` and `minzoom`, but no `kilometers`; they are not the line-placed text features.

This confirms packaging and styling in the inspected version/sample, not Gaia's private preprocessing algorithm, complete global schema, or topology/cross-tile guarantees. Gaia's [official description](https://blog.gaiagps.com/all-new-gaia-topo/) says the displayed values describe non-urban trail/4x4 segments between junctions and are shown to a tenth of a mile or kilometer. Our separate `trail_segment_label` layer remains unchanged as requested.

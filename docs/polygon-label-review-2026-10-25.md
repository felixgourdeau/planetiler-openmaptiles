# Polygon label regional review — 2026-10-25

Compiled current working-tree Java sources with offline Maven `compile`, tests and formatting disabled. Initial `package` failed because an uncached Surefire plugin could not resolve offline; no test framework was installed and no tests were run. Used `target/classes` before the existing dependency JAR on the classpath. The JAR supplies dependencies, not the profile implementation; embedded version/build metadata is therefore not provenance for the current profile sources.

Built polygon_label ONLY from existing September 2025 Whistler and Adirondacks OSM extracts. No production styles, generated mappings or live archives changed. No elevation enrichment implemented.

Initial default-minzoom archives failed `pmtiles verify`: header z0 differed from the first nonempty tile (Whistler z7, Adirondacks z6). Rebuilt with explicit minzoom=7 and minzoom=6 respectively; both then passed `pmtiles verify`. This is an isolated-layer zoom-range issue, not evidence that the full basemap fails.

| Archive | Bytes | SHA-256 |
| --- | ---: | --- |
| [whistler-z7.pmtiles](/private/tmp/outmap-polygon-review-20261025/whistler-z7.pmtiles) | 79890 | `e3cb4b584ab34ead7da408694585134a1bd696b0e997d23783752c9b77bde1a1` |
| [adirondacks-z6.pmtiles](/private/tmp/outmap-polygon-review-20261025/adirondacks-z6.pmtiles) | 1443705 | `b606a074eb231950e25db94e02bb91c316c11a058ac1fa63829b016d1b0060d0` |

Whistler: 245 addressed tiles. Adirondacks: 6,124 addressed tiles. Both maxzoom=14. Build logs reported no data errors. Logs and intermediate archives are in `/private/tmp/outmap-polygon-review-20261025/`.

Not performed: individual tile geometry decoding, screenshot/rendering acceptance, translated names, collision/seam/pitch review, full-basemap integration, Rainier coverage, or device/offline acceptance. Passing structure checks does not establish visual quality.

## Live elevation sample

POSTed four coordinates from the Whistler extract to the exact AWS `/elevations` endpoint used by the web client. Values below are rounded to one decimal; this comparison does not establish ground truth.

| Peak | OSM metres | Endpoint metres | Difference |
| --- | ---: | ---: | ---: |
| Whirlwind Peak | 2428 | 2425.9 | -2.1 |
| Refuse Pinnacle | 2470 | 2453.9 | -16.1 |
| Overlord Mountain | 2625 | 2607.3 | -17.7 |
| Fissile Peak | 2439 | 2422.2 | -16.8 |

The web client accepts 2–1000 coordinates in a GeoJSON LineString FeatureCollection and expects one finite number per coordinate. Local terrain-proxy source supports up to 5000 coordinates, uses z12 DEM tiles and bilinear interpolation, and can return null for no-data; production implementation parity, coverage and vertical datum are not established by this sample. Do not copy the web helper’s all-or-nothing validation into a backfill that needs to preserve per-point missing data.

Recommended enrichment policy (pending user decision): preserve valid OSM elevations, batch missing/invalid peak and saddle coordinates into a resumable versioned snapshot, record source/coordinates/retrieval time, and read that immutable snapshot during generation. Avoid live HTTP inside per-feature Planetiler callbacks. No-data remains unknown; do not substitute zero or clamp unsupported latitudes. Current clients retain numeric `ele`/`ele_ft` conventions.

# Outdoor POI priorities and crowded-tile audit — 2026-10-25

## Implemented behavior

Requested outdoor types now use sort score **0 when named, 1 when unnamed**, ahead of all ordinary categories (best ordinary class score 20). This covers viewpoints/attractions, campsites/caravan sites/camp pitches, outdoor/sports shops, alpine/wilderness huts/chalets/hostels, shelters, drinking water/water points/toilets, waterfalls/stones, trailheads/cave entrances/bicycle-repair stations, sports-tagged features and common sporting facilities. Benches use 10/11. Other category scores are unchanged. A sort score is not the emitted grid rank: crowded outdoor categories still compete with each other and labels still collide.

Camp pitches explicitly enter the high-priority group by subtype, retaining `class=campsite`, `subclass=camp_pitch`, and z14 availability. The unused class-score entry 111 was removed. Other existing POI minzooms remain unchanged. Stones/waterfalls now use common ranking/grid setup, keeping their previous output classes.

Missing standalone point/polygon matches are added through a supplemental handler in `Poi.java`; it only handles features not already matched by the corresponding generated POI table. Existing multi-tag dispatch wins, avoiding duplicate POI emission. This outdoor-priority change does not edit `Tables.java` or `OpenMapTilesSchema.java`; other work in this shared checkout may modify them.

| New subtype | Existing icon class | Tile minzoom |
| --- | --- | ---: |
| trailhead | information | 12 |
| wilderness_hut | lodging | 12 |
| cave_entrance | attraction | 12 |
| bench | information | 12 |
| water_point | water | 12 |
| bicycle_repair_station | bicycle | 12 |
| sport=hiking/walking/trail_running | information | 12 |

The exact subtype is retained. `water_point` uses a neutral water icon rather than implying drinking-water quality. Reused classes exist in the current shared web/mobile sprite catalog `ofm_f384`. No client styles or source URLs changed. This does not fix pre-existing missing dedicated icons for other sports.

## Production sample method

Fetched and decoded 20 current `planet-250825` tiles: five city locations at z11–14. Exact coordinates/URLs and response sizes are in [production-samples.json](production-samples.json). Initial Python HTTP requests returned 403; ordinary curl requests to the same URLs succeeded. No server or permission changes were made. These are actual production tiles, not newly generated data with the new priorities.

Compared two encodings of each decoded tile using the same Planetiler MVT encoder and Java gzip implementation: unfiltered baseline versus a retention simulation. Thus savings measure comparable compressed payloads, not the original HTTP transfer encoding or a whole-planet reduction. All non-POI layers, retained feature attributes/ranks and geometries are kept.

The simulation only filters z12–13. It keeps ranks <=12, all identifiable ultra-priority outdoor types regardless of rank, all bus-stop icon features, and airport/rail transit exceptions. Missing/unusable ranks are kept. z11 and z14 are untouched. Protected types include mapped sports subtypes plus broad golf/stadium/swimming classes. Production tiles lack some source OSM tags; the simulation cannot identify every feature prioritized solely by a source `sport` tag. These estimates are not a proven final production filter. Any implementation must carry the priority through generation rather than reconstructing it from lossy output tags.

**No density cap was implemented.** Current styles still show ordinary rank bands at z12/16/17; z14 remains the complete data source for overzooming.

## Measured compressed sizes

Sizes are KiB (1024 bytes); before/after use consistent re-encoding.

| Location | Zoom | Before KiB | Simulated KiB | Saving | POIs | Protected outdoor POIs | Removed POIs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| paris | 11 | 285.8 | 285.8 | 0.0% | 0 | 0 | 0 |
| paris | 12 | 2303.4 | 374.7 | 83.7% | 91,539 | 3,573 | 85,022 |
| paris | 13 | 557.6 | 188.3 | 66.2% | 19,460 | 851 | 17,839 |
| paris | 14 | 312.1 | 312.1 | 0.0% | 6,520 | 291 | 0 |
| tokyo | 11 | 196.1 | 196.1 | 0.0% | 0 | 0 | 0 |
| tokyo | 12 | 2265.1 | 407.0 | 82.0% | 58,520 | 2,914 | 52,892 |
| tokyo | 13 | 640.1 | 188.7 | 70.5% | 14,130 | 663 | 12,619 |
| tokyo | 14 | 391.1 | 391.1 | 0.0% | 6,053 | 223 | 0 |
| taipei | 11 | 203.9 | 203.9 | 0.0% | 0 | 0 | 0 |
| taipei | 12 | 2032.5 | 439.3 | 78.4% | 51,230 | 4,439 | 42,984 |
| taipei | 13 | 1117.2 | 276.1 | 75.3% | 25,477 | 1,819 | 22,167 |
| taipei | 14 | 619.4 | 619.4 | 0.0% | 8,582 | 613 | 0 |
| london | 11 | 232.0 | 232.0 | 0.0% | 0 | 0 | 0 |
| london | 12 | 1429.6 | 299.6 | 79.0% | 56,805 | 2,008 | 52,538 |
| london | 13 | 578.9 | 170.0 | 70.6% | 18,531 | 558 | 17,358 |
| london | 14 | 199.7 | 199.7 | 0.0% | 4,091 | 228 | 0 |
| montreal | 11 | 236.7 | 236.7 | 0.0% | 0 | 0 | 0 |
| montreal | 12 | 719.7 | 288.8 | 59.9% | 25,457 | 1,545 | 20,932 |
| montreal | 13 | 385.3 | 163.6 | 57.5% | 11,658 | 561 | 10,148 |
| montreal | 14 | 252.6 | 252.6 | 0.0% | 4,415 | 157 | 0 |

At z12, POIs account for 80.7–92.7% of raw tile bytes in these samples. Leading categories include shops, restaurants, bicycle parking, waste baskets, gates, offices and parking. Gzipped layer bytes are not additive across layers; raw layer proportions are reported separately. These five selected locations deliberately include historical worst cases and are not a representative global traffic sample.

## Actual baseline/candidate generation

Compiled the updated Java sources with offline Maven compile, tests and formatting disabled. Saved pre-edit compiled classes and generated baseline/candidate POI-only archives from identical inputs. Monaco uses the downloaded current Geofabrik extract; Whistler uses the existing September 2025 extract. Decoded all 258 tiles across the four archives. No synthetic data or unit tests.

| Region | Distinct z14 baseline POIs | Updated POIs | Existing IDs lost | Existing class changes | Added features |
| --- | ---: | ---: | ---: | ---: | --- |
| Monaco | 1886 | 2135 | 0 | 0 | 248 benches, 1 cave entrance |
| Whistler | 1235 | 1287 | 0 | 0 | 43 benches, 5 trailheads, 3 bicycle-repair stations, 1 wilderness hut |

Candidate POI compressed-byte sums (tile payloads, not entire archive size):

| Region | Zoom | Candidate KiB | Retention simulation saving |
| --- | ---: | ---: | ---: |
| monaco-updated | 12 | 53.3 | 83.5% |
| monaco-updated | 13 | 168.2 | 84.1% |
| monaco-updated | 14 | 120.2 | 0.0% |
| whistler-updated | 12 | 101.5 | 62.9% |
| whistler-updated | 13 | 100.8 | 57.0% |
| whistler-updated | 14 | 89.5 | 0.0% |

Admission of the missing features increases unfiltered payloads; the priority changes are not themselves a size optimization. Samples confirm common-handler waterfalls/stones survive, with Wedgemount Falls, Wedge Creek Falls and Rainbow Falls at rank 1 in their sampled z14 cells. New bicycle-repair stations can still have ranks above six among crowded high-priority features.

The initial POI-only archives advertised minzoom=10 but had first data at z12, so `pmtiles verify` rejected that header. Updated-only archives were rebuilt with minzoom=12; verification then rejected the automatically chosen center zoom (Monaco 10, Whistler 11), which is below that minimum. The tile payloads were decoded successfully, but these isolated-layer archives have not passed archive-header acceptance. This does not establish any header issue in the full basemap.

## Remaining acceptance and next step

- Recommend retaining every ultra-priority outdoor POI, plus existing ranks 1–12 and existing transit exceptions at z12–13; no pruning at z14. Preserve ranks assigned before pruning.
- Review visual density after the new priorities on current web/iOS/Android styles before enabling a cap. Highest priority does not guarantee visibility when several outdoor labels compete.
- No device, offline or screenshot acceptance performed. Water-point admission and all sports variants were source-reviewed but not each represented by a named sample in these regional outputs.
- No production deployment, source URL change, upstream regeneration, unit tests or repository typechecks.
- Temporary source extracts, archives and logs are at `/private/tmp/outmap-poi-audit-20261025/`; structured measurements are retained here.

# Expanded OSM coverage — 2026-10-25

Local implementation of the approved single-archive option and lightweight building extrusion option. The provisional acceptance target is no more than 10% growth in sampled p95 compressed tile size at each zoom. Archive-wide growth is a separate metric and cannot substitute for that target. The user subsequently approved retaining all coverage with the measured growth, pending broader validation. The original 10% criterion was not met and is not reported as passed. No production archive or app was published.

## Coverage and tile contract

All newly added fine geometry and POI detail attributes start at **z14**, which existing clients overzoom. Existing feature availability, priorities, classifications and original fields are preserved. No z15-only fields, feature caps, upstream regeneration, new dependencies, or external detail service.

| Family | Implementation |
| --- | --- |
| Trail conditions | Existing `OutdoorAttributes` already retains surface/tracktype/smoothness/width/incline/informal/trailblazed and raw/normalized difficulty fields. Reused unchanged. |
| MTB | Existing uphill/IMBA fields retained; adaptive MTB scale/inclusive/technical fields and MTB description added at z14. |
| Routes | Existing road/hiking/foot/walking/bicycle/MTB/horse/ski and motorized memberships preserved; canoe/piste/via-ferrata memberships added. Symbol and piste details use `route_N_<OSM key>` at z14 in `transportation_name`. Non-highway route-member lines use `outdoor`. Relations with only a route type can still appear as non-highway route geometry. Nested relation expansion is not added. |
| Ski | Standalone piste ways and explicitly mapped areas use `outdoor/class=piste`. Closed Nordic circuits remain lines. Highway-tagged pistes retain transportation geometry; piste metadata is carried by existing outdoor attributes plus new name/ref/patrolled fields. Relation details are preserved separately from way tags, without pretending inherited grades are surveyed way grades. |
| Via ferrata | Dedicated `highway=via_ferrata` lines in `outdoor`, plus scale metadata. Existing paths keep their existing classification. |
| Springs/nature | Point/polygon POIs for springs, hot springs, geysers, sinkholes, arches, capes, peninsulas and valleys. Named/explicit landmark trees only. Valley/tree-row and other selected natural geometry in `outdoor`. Springs are not assumed potable. |
| Emergency | Phones, access/assembly points, mountain rescue, defibrillators, lifeguards, water rescue, rescue boxes, and highway emergency-access points admitted. Generic information icon fallback; exact subtype retained. |
| Recreation | Picnic tables, firepits, fitness facilities, fishing places, bird hides, bathing places and managed beaches admitted. Existing picnic-site/BBQ/bench mappings preserved. |
| Camps/huts/POI detail | Sparse hours, fee, access, wheelchair, operator, capacity, reservation, backcountry, shelter type, water/toilet/shower/hot-water/electricity/camping amenities, seasonality and references. Website/phone/mobile/email/fax resolve the plain key first, then `contact:*`; URL is a final website fallback. Missing values stay absent. |
| Paddling | Put-ins, egress, combined entry/exit points and rapids; portage/rapid geometry; whitewater grades/section names and canoe/boat access. River attributes stay in `waterway`; route membership can additionally appear in `outdoor`. |
| Barriers | Existing obstacle POIs/metadata preserved. Fence/wall/hedge/retaining-wall/city-wall/ditch geometry in `outdoor`, with material, height, width and access metadata. Closed fences remain lines unless explicitly mapped as areas. |
| Protected areas | `protect_class`, operator, designation, access/conditional access and original protection title retained on existing park polygons. |
| Man-made landmarks | Towers, survey points, mine entrances/shafts and wells admitted as POIs; avalanche protection, breakwater and groyne geometry in `outdoor`. No inference that a generic tower is an observation tower. |
| Historic | Previously unlisted non-placeholder historic values get a generic attraction POI, retaining exact subtype. |
| Marine | Other non-placeholder seamark types, berths and moorings admitted at z14; subtype-specific `seamark:*` attributes retained at z14. Existing seamark type/name aliases are not duplicated. Generic seamarks/moorings use the marina asset in the marine helper. This does not implement a complete nautical-chart symbol system. |
| Water engineering | Linear dams/weirs admitted by the existing waterway mapping. Existing dam polygons remain in landuse. |
| Buildings | Existing footprints, parts, height/base, colour and outline suppression retained. Sparse building type/use/part and roof shape/height/levels/angle/direction/orientation/colour/material fields added at z14. Existing level-derived/fallback extrusion heights are unchanged and are not surveyed heights. |
| General POIs | Previously unlisted shop/amenity/sport categories admitted at z14. Empty and placeholder `no/none/yes/vacant` values are excluded. Existing mapping wins for multi-tag objects; generic fallback retains exact subtype and `kind` (source key). Other sport tags survive as metadata. |
| Waterfalls/stones | Standalone polygon POIs added with an interior point; waterfall lines can yield a midpoint POI. Original point mappings and existing supplemental polygon placement preserved. |

`outdoor` contains selected new line/polygon geometry, **not a second archive**. Fields include `class`, `subclass`, names and sparse source-specific attributes. Equal-attribute geometries are packed into multi-geometries without dropping paths or deliberately simplifying coordinates. Feature IDs in that packed layer are not a one-to-one OSM lookup contract. All POIs remain in `poi`.

The generated-file change is a surgical addition of `dam`/`weir` to `OsmWaterwayLinestring.MAPPING`. Outmap's other generated mappings/schema customizations are preserved.

## Measured result and accepted tradeoff

| Region | Archive growth | All-tile z14 p95 growth | Matched populated z14 p95 growth | Existing POIs lost/class changes |
| --- | ---: | ---: | ---: | --- |
| Monaco | +7.54% | 0.00% | +15.44% | 0 / 0 |
| Whistler | +6.34% | +22.29% | +14.04% | 0 / 0 |
| San Francisco downtown (incomplete diagnostic extract) | +8.93% | +18.97% | +18.97% | 0 / 0 |

“Matched populated” uses the same tiles in both variants and requires a baseline payload of at least 1 KiB. Monaco's all-tile p95 is dominated by small coastal/context tiles and is not evidence that its populated tiles stayed within budget. Whistler's all-tile p95 goes from 16,186 to 19,794 bytes; San Francisco from 94,356 to 112,256 bytes. Maximum z14 tiles: Monaco 148,699 → 171,654 bytes; Whistler 75,442 → 86,877 bytes; San Francisco 94,356 → 112,256 bytes. Zooms below 14 stay below 1% p95 growth in these samples.

Distinct z14 POIs: Monaco 2,310 → 2,720; Whistler 1,530 → 1,754; San Francisco 2,067 → 2,227. All six baseline/candidate archives passed `pmtiles verify`; every tile was decoded. Examples include standalone pistes, linear barriers, tree rows, protected-area classes, new POIs, roof tags and contact fields. Rare feature families still require further real-data examples.

**Decision:** retain the complete implementation and the single archive, accepting these measured increases pending broader validation. Do not silently remove categories, cap z14 features or truncate metadata. The samples do not establish a global size bound.

## Client integration

`styles/outdoor-details.ts` exports `createOutdoorDetailLayers` and `createBuildingExtrusionLayer`. Both use the existing vector source. Add the outdoor geometry layers above landcover and below labels. Keep generic waterway styles from treating `dam`/`weir` as ordinary streams. Do not add duplicate extrusion stacks; preserve the existing 2D building layer for low zooms. The extrusion helper respects `hide_3d`, height/base and colour.

Roof metadata **does not generate shaped roofs or downloadable meshes**. Option A provides footprint/part extrusion. The inspected v7 web/mobile JSON styles do not currently contain fill-extrusion layers; helpers still need integration and web/native acceptance. No client repositories or source URLs changed in this task. Existing sprites provide generic fallback icons; dedicated icons for all new categories are not implemented.

## Verification method

- Java compilation and executable JAR assembly succeeded with Java 21; packaged classes match the measured candidate snapshot. Normal offline `package` resolution hit an uncached Surefire plugin despite tests being disabled; direct `jar:jar assembly:single` succeeded without entering the test lifecycle. Unit tests and repository typechecks not run, per AGENTS.md.
- Baseline source/classes saved before edits. The unrelated concurrent bridge minzoom change was copied into that saved baseline, so baseline and candidate agree on it. Shared working-tree changes are preserved.
- Identical local OSM extracts and auxiliary sources for paired all-layer archives at z0–14; same threads, heap and generation options. Monaco is the existing downloaded extract from the earlier audit; Whistler is an older local extract. San Francisco downtown is an older small example extract with missing-node geometry warnings in **both** variants, so it is diagnostic, not clean regional acceptance.
- Decode every tile; measure stored compressed tile bytes, percentiles, sums, maxima and archive bytes; check distinct z14 POI IDs and classification retention. These are regional samples, not planet-wide or traffic-weighted estimates.
- Small Monaco tiles include much empty/coastal context. In addition to all-tile p95, report matched z14 tiles whose baseline payload is at least 1 KiB, so near-empty tiles cannot conceal growth in populated tiles.
- Style helpers executed using Node 24 type stripping and their generated style validated with the already installed Mapbox style-spec validator (zero errors). This is runtime style-data validation, not a TypeScript typecheck or unit-test harness.
- Scoped Spotless formatting could not run offline because existing plugin transitive artifacts were missing from the local Maven cache. No dependencies were installed. Source and whitespace diff review performed.

Measurements are in `analysis.json`, `size-summary.json` and per-archive tile-size JSON. `InspectCoverage.java.txt` is the manual real-archive decoder used for the comparison, not a repository unit test.

Still required: complete regional inputs for broader city acceptance, named examples for rare categories not represented in these extracts, rendered map inspection, native/offline acceptance and planet-scale size validation beyond the accepted regional measurements. No pruning or metadata truncation was used to force a passing result.

## Tag references

- [OSM hiking](https://wiki.openstreetmap.org/wiki/Hiking)
- [OSM pistes](https://wiki.openstreetmap.org/wiki/Piste)
- [OSM via ferrata](https://wiki.openstreetmap.org/wiki/Tag:highway%3Dvia_ferrata)
- [OSM natural features](https://wiki.openstreetmap.org/wiki/Key:natural)
- [OSM leisure](https://wiki.openstreetmap.org/wiki/Key:leisure)
- [OSM whitewater](https://wiki.openstreetmap.org/wiki/Whitewater_sports)
- [OSM barriers](https://wiki.openstreetmap.org/wiki/Key:barrier)
- [OSM campsite attributes](https://wiki.openstreetmap.org/wiki/Tag:tourism%3Dcamp_site)
- [OSM protection classes](https://wiki.openstreetmap.org/wiki/Key:protect_class)
- [OSM structures](https://wiki.openstreetmap.org/wiki/Key:man_made)
- [OSM seamarks](https://wiki.openstreetmap.org/wiki/Key:seamark:type)
- [Mapbox building extrusion](https://docs.mapbox.com/mapbox-gl-js/example/3d-buildings/)

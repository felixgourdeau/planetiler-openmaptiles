# Basemap follow-up — 2026-10-25

Local implementation only. No planet build, upload, source-URL promotion, or mobile release.

## Parking decision

Promote `amenity=parking` only with explicit `trailhead=yes` or `highway=trailhead`, and
explicit `access=yes/public/permissive`. Any unrecognized/restricted value in access,
vehicle, motor_vehicle, motorcar or foot, or a conditional tag on those keys, excludes
promotion. This is a rendering priority rule, not an access permission decision.
Paid parking is not automatically excluded. Names, surface, rural position and proximity
to trails do not imply public access. Untagged useful parking remains a known gap.

Eligible parking gets the existing outdoor priority (named 0, unnamed 1); other parking
retains its class score. A sport tag cannot bypass the parking-specific rule. Existing
class, icon, minzoom and feature admission are preserved. No framed/minor icon redesign
was applied. Nikita was asked to align the priority and confirm his style branch:
[Slack coordination](https://outmapworkspace.slack.com/archives/C09JLLT718E/p1792941716569539).

## Greenland label visibility

Production tiles at the reported location contain `Thryms Glacier` in landcover at z11,
z12 and z14. The existing style starts glacier names at z12, fully opaque at z12.3.
Local web v7, light, ski-light and topo-no-dem variants, plus mobile v7, now start at z10
and reach full opacity at z10.3. Font, collision rules, geometry and sources are unchanged.
The separate OG style is untouched. Both main v7 styles passed the installed Mapbox
style validator; this does not establish rendering or mobile/offline acceptance.

Point-only glaciers still require a separate admission/deduplication design. The reported
OSM point is not needed to label the existing Thryms polygon. The new polygon-label
layer is not activated by this change.

## Iceland vector comparison and pilot

`scripts/prepare-iceland-names.mts` fetches the three official WFS name geometry types,
checks completeness, compares normalized names/aliases and nearby bounding boxes with
OSM, and writes immutable evidence into a new directory. It never auto-imports unmatched
names. An explicit UUID allowlist is required; OSM matches and nearby same-name official
duplicates cannot be approved. Each output retains official identity, source revision,
original geometry, Icelandic name, and CC-BY-4.0 attribution.

Pilot bbox: west -19.15, south 63.9, east -18.95, north 64.05 near Landmannalaugar.
Official response: 4 point, 46 line and 59 polygon features (109 total), revision
22 October 2026. OSM input: Geofabrik Iceland, timestamp 2026-10-24T20:20:51Z.
Overpass returned HTTP 406; a second public endpoint timed out. Used the downloaded
country PBF and the installed osmium CLI instead, retaining full named geometries.

Result: 40 official features match an OSM name; 69 require review. These counts are
features, not unique places or a national coverage estimate. Bounding-box overlap is
conservative and is not semantic equivalence. It may suppress distinct same-name places,
and variants can remain unmatched. No blanket conclusion that all 69 are missing.

Reviewed pilot inclusion: Jónsfoss, Tvílitafossar and Bólið. Their official point records
are marked existing; exact names were absent from the sampled production z14 tiles and
the nearby current OSM name comparison. Reviewed nearby named features: river
Jökulgilskvísl near Jónsfoss, none near Tvílitafossar, and river Tungnaá/road
Fjallabaksleið nyrðri near Bólið. Stútur is excluded (OSM node 5809030553).
This is a review for additive labels, not a ground-truth survey of the named features.

Retained review outputs: [comparison](iceland-pilot-2026-10-25/comparison.json),
[pilot GeoJSON](iceland-pilot-2026-10-25/approved.geojson), and
[manifest](iceland-pilot-2026-10-25/manifest.json). Full raw evidence remains in
`/private/tmp/iceland-pilot-20261025-reviewed/`; the country PBF and osmium exports are
in `/private/tmp/iceland-pilot.osm.pbf` and `/private/tmp/iceland-all-names.geojson`.

The opt-in `--iceland-names=/absolute/path/approved.geojson` build argument adds the
`iceland_name` layer and archive attribution. Default builds remain OSM-only. The pilot
uses point-on-surface labels at z12–14, retaining original geometries in the input snapshot.
`styles/iceland-names.ts` supplies a label layer; it is not installed into production styles.
Clients using explicit vector source attribution must add NÁTT credit themselves: archive
metadata alone does not update their attribution control. Recompare against the exact OSM
snapshot used for any future build to avoid introducing names OSM has since acquired.

Run with Node 22.20 or newer, no added packages:

```sh
node scripts/prepare-iceland-names.mts --bbox=-19.15,63.9,-18.95,64.05 --output=/new/comparison
# Offline/reproducible comparison, including fallback from failed Overpass:
osmium tags-filter iceland.osm.pbf 'nwr/name*' nwr/alt_name nwr/old_name nwr/loc_name nwr/int_name -o named.osm.pbf
osmium export named.osm.pbf --add-unique-id=type_id -o named.geojson
node scripts/prepare-iceland-names.mts --bbox=-19.15,63.9,-18.95,64.05 --output=/new/review --snapshots=/previous/comparison --osm-geojson=named.geojson
# After reviewing candidates, pass a JSON array of their UUIDs:
node scripts/prepare-iceland-names.mts --bbox=-19.15,63.9,-18.95,64.05 --output=/new/approved --snapshots=/new/review --approved=approved-ids.json
```

Source references: [NÁTT names](https://www.natt.is/is/midlun/kortagogn/ornefni),
[Iceland national report](https://unstats.un.org/unsd/ungegn/sessions/4th_session_2025/documents/GEGN.2_2025_90_CRP90_item4b.pdf),
[Geofabrik Iceland](https://download.geofabrik.de/europe/iceland.html).
NÁTT describes quarterly downloadable updates. A pilot snapshot is not a live update feed.

## Remaining acceptance

Source/diff review and actual vector comparison completed. No unit tests or repository
typechecks, per AGENTS.md. No Java compilation, pilot archive generation, web screenshots,
physical-device rendering or offline acceptance performed. Before release, decode the
pilot archive, verify NÁTT attribution in both clients, inspect label collisions and seams,
and compare parking in an outdoor region and dense city. Check glacier visibility in
Greenland at z10–14 and dense glacier areas before deploying the style changes.

## Source backlog

| Source | Next action | Publication gate |
| --- | --- | --- |
| CAMBA contributions | Confirm which existing partner routes are public and compare their coverage | Partner publication and Nordic styling acceptance; do not duplicate private routes into basemap |
| NSW topographic vector data | Compare transport, water, vegetation and power infrastructure around Mt Coramba against OSM | Verify actual feature gaps, per-dataset reuse terms, attribution, geometry and refresh ownership |
| Iceland NÁTT names | Review remaining 66 unmatched candidates after this three-label pilot | Semantic duplicate review, exact OSM revision comparison, archive/render/offline acceptance |

Extend this inventory with source version, owner, refresh cadence and validation locations
before adding more national sources. Existing immutable release process remains in
[planet-releases.md](planet-releases.md); no infrastructure rewrite is introduced.

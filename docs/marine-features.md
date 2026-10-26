# OSM marine features

Marine features use the existing `poi` and `landcover` layers. No external depth
dataset, bathymetry layer, or new dependency is introduced. New archives must be
generated to contain the added POIs and attributes.

## POI coverage

| OSM tags | `marine_kind` |
| --- | --- |
| `historic=wreck` or `seamark:type=wreck` | `wreck` |
| `man_made=lighthouse` | `lighthouse` |
| `leisure=slipway`, or `highway=service` + `service=slipway` | `slipway` |
| `seamark:type=buoy_cardinal/buoy_lateral/buoy_isolated_danger/buoy_safe_water/buoy_special_purpose/buoy_installation` | `buoy` |
| `seamark:type=beacon_cardinal/beacon_lateral/beacon_isolated_danger/beacon_safe_water/beacon_special_purpose/light_major/light_minor/daymark` | `beacon` |
| `man_made=buoy/beacon` or legacy `seamark=buoy/beacon` | `buoy` / `beacon` |
| `seamark:type=rock` | `marine_rock` |
| `natural=reef/shoal` | `reef` / `shoal` |
| `sport=surfing/swimming/scuba_diving/canoe/sailing/rowing/water_ski` | Original sport value |
| `leisure=marina/swimming_area` | `marina` / `swimming` |
| `waterway=dock` | `dock` |
| `man_made=pier` | `pier` |

POIs begin at z12, subject to existing collision/rank handling. Points retain
their location; newly admitted polygons get an interior point; lines get a
midpoint on the line. Existing polygon POIs retain their existing placement.
Existing pier transportation geometry, dock water polygons, and coastal fills
remain in their original layers.

The table is evaluated in order: one marine category per OSM object. Existing POI
dispatch wins when it already admits an object; its class/subclass is retained,
and marine metadata is added to that same POI. Newly admitted marine objects use
`class=harbor` as a legacy icon fallback and retain their selected raw tag value
in `subclass`. They are distinguished by `marine_kind` in the new styles. This
avoids emitting a second POI for a lighthouse also tagged as an attraction, or a
surf spot also tagged as a sports centre. Distinct OSM objects are not deduplicated.

`seamark_type` preserves the original `seamark:type`. When the normal name is
missing, `seamark:name`, then `ref`, supplies the label. Unnamed features still
receive icons. Unknown seamark types and compound sport values are not treated as
known categories. Mooring facilities are not assumed to be buoys. These are
general map icons, not a complete nautical chart symbol system.

## Coastal areas

Existing `natural=reef/shoal` polygons remain in `landcover` with matching classes.
Beaches, mud, and tidal flats retain their existing mappings and `tidal` values.
No shallow area is inferred from the coastline or water colour. A shoal means an
explicitly mapped shoal; no depth, clearance, tide level, or navigability is inferred.

`styles/marine.ts` supplies dotted shoals, reef crosses, and tidal dashes above
water fills. Tidal patterns match `subclass=tidalflat`, or `tidal=yes` on mud, sand,
or wetland. Beaches keep the existing sand fill. The patterns can be used over
the translucent reef/shoal fills from `styles/landcover.ts`.

## Client integration

The repository contains a reusable style helper and image assets; live web/mobile
styles in other repositories are not modified by this change.

1. Import `createMarineImages` and `createMarineLayers` from `styles/marine.ts`.
2. Register the returned RGBA images under their returned IDs, with `pixelRatio: 2`
   and `sdf: false`. Repeat registration on style reload. Images and layers must
   use the same prefix. Default icon color is for light maps; pass a light RGB
   color when generating images for dark maps.
3. Call `createMarineLayers` with the existing vector source ID and a font stack
   available in the host style's glyph source. Insert `overlays` above water
   fills, below roads and symbols. Insert `poi` with the other POI labels.
4. Exclude features with `marine_kind` from the generic POI symbol layers while
   the marine layer is enabled. `excludeMarinePois` accepts expression-style
   filters only; convert legacy property filters before using it. Restore the
   original filters if the marine layer is removed.
5. Native clients can register the PNGs in `styles/marine-images/`; the manifest
   gives image IDs, dimensions, and pixel ratios. These are individual images,
   not an atlas that can replace the existing basemap sprite. Preserve the
   existing sprite and register or pack these additional images.

Regenerate the default PNG assets with Node 22.20+ or Node 24:

```sh
node scripts/export-marine-images.mts styles/marine-images
```

The exporter uses built-in Node modules only. The style helper uses TypeScript,
ES modules, and no framework dependency. Image assets are original line drawings.

## Verification and rollout

Source/diff review completed; the pinned Planetiler 0.9.1 API was inspected for
the geometry methods used here. PNG export completed and a contact sheet of all
21 images was visually inspected. No unit tests, typechecks, or upstream
regeneration were run, per repository instructions.

Still required before release: generate a regional archive, inspect actual tile
attributes/geometry for point, line, polygon, multi-tag, and unnamed examples;
connect the helper/assets in each client; verify symbol collisions, dark mode,
water-overlay ordering, style reloads, and native image scaling. No archive build,
live map render, publication, or deployment was performed.

## Tag references inspected

- [OSM seamark types](https://wiki.openstreetmap.org/wiki/Key:seamark:type)
- [Historic wrecks](https://wiki.openstreetmap.org/wiki/Tag:historic%3Dwreck)
- [Mapped shoals](https://wiki.openstreetmap.org/wiki/Tag:natural%3Dshoal)
- [Slipways](https://wiki.openstreetmap.org/wiki/Tag:leisure%3Dslipway)
- [Buoys](https://wiki.openstreetmap.org/wiki/Tag:man_made%3Dbuoy)
- [Legacy seamark tags](https://wiki.openstreetmap.org/wiki/Key:seamark:*)

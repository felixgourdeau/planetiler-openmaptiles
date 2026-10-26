# Extended landcover coverage

Implemented locally on 2026-10-25. Requires an archive rebuild and client style integration before becoming visible in production.

## Tile contract

Existing landcover classes and accepted tags remain supported. Additions:

| Input | Layer | Class | Subclass |
| --- | --- | --- | --- |
| `natural=shingle` | landcover | rock | shingle |
| `natural=mud` | landcover | mud | mud |
| `natural=reef` / `shoal` | landcover | reef / shoal | original value |
| `wetland=*` | landcover | wetland | original nonblank value |
| `landcover=trees` | landcover | wood | trees |
| `landcover=grass/scrub/fell/shrubbery/flowerbed/hedge` | landcover | grass | original value |
| `landcover=gravel` | landcover | rock | gravel |
| `landcover=sand` | landcover | sand | sand |
| `landcover=bare_ground/mostly_rock/greenery/dry_swamp` | landcover | original value | original value |
| Other recognized `landcover=*` values | landcover | existing subclass-to-class mapping | original value |
| Unknown nonblank `landcover=*` | landcover | other | original value, including compound values |
| `landcover=water` fallback | water | water | none |

Landcover also retains `surface` and `tidal` when provided, including beach surface information. Existing beach classification remains `sand/beach`; consumers can refine styling using `surface` without losing the beach identity. Unknown values are retained without claiming a specific physical material.

This is polygon coverage, not acceptance of every `natural=*`, `landuse=*`, or `surface=*` tag as landcover. These keys also describe points, lines, landforms, land uses, and supplemental properties. Existing water, landuse, mountain, and POI layers retain their roles. Invalid geometry and existing zoom/size filtering still apply. OSM landcover begins at z7; generic water begins at z6.

## Precedence

1. A nonblank `wetland=*` refines `natural=wetland`. Standalone wetland tags are accepted when no legacy landcover mapping matches.
2. Otherwise preserve the exact original landcover expression's matching key order.
3. For features without a legacy match, handle shingle, mud, reef, and shoal explicitly, then use `landcover=*`.
4. Generic water is a fallback only when no legacy water or competing landcover/wetland classification applies. It skips Natural Earth lake matching. Existing water tags retain their classification.

A wetland subtype is preserved even if it is not in the known-values schema set. `SUBCLASS_VALUES` lists known values, not an exhaustive validation enum. New specific wetland constants include fen, dambo, string_bog, carr, pocosin, and varzea.

## Client integration

`styles/landcover.ts` exports `createLandcoverLayers(source)` without a dependency on a particular map SDK. Insert its `ground` layers in place of existing landcover fills, below water. Add its `water` layer alongside existing water fills. Insert `aquaticOverlays` above water but below roads and labels. Do not add a second complete ground stack over existing landcover fills.

The helper provides default colors for every emitted class. Existing client filters restricted to subclasses need updating for new values. Clients may use the preserved surface and tidal attributes for additional differentiation. Reef/shoal overlays are cartographic information, not navigation or tide predictions.

This repository does not contain the main web/mobile map styles. The helper is supplied here; it is not installed in those applications.

## Maintenance and verification

Changes to `Tables.java` and `OpenMapTilesSchema.java` deliberately preserve Outmap's custom generated files. Do not regenerate from upstream without preserving these extensions. No dependencies were added.

Source and diff review only: repository instructions prohibit unit tests and typechecks. No archive was generated, no deployment occurred, and rendering has not been checked. Before release, inspect rebuilt tiles for beach, scree, shingle, mud, wetland subtypes, explicit landcover aliases, unknown values, conflicting tags, reef/shoal overlays, and generic water; check tile-size impact and both client styles.

## Sources used for the mapping proposal

- [OSM natural features](https://wiki.openstreetmap.org/wiki/Key:natural)
- [OSM wetland types](https://wiki.openstreetmap.org/wiki/Key:wetland)
- [OSM landcover values](https://wiki.openstreetmap.org/wiki/Key:landcover)

OSM descriptions establish tag meanings; class assignments, fallback behavior, and colors are Outmap implementation choices.

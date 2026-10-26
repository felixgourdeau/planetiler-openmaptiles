# Polygon labels

`polygon_label` is an additive vector-tile layer generated from named OSM polygons.
It does not alter existing water, landcover, place, park, or mountain labels. No
basemap styles are enabled by this change. New archives must be generated to
contain the layer; existing archives are unchanged.

## Categories

Each feature has one `class`. The first matching category below wins, so a named
forest that also has a protection boundary is classified as `forest`.

| Class | OSM tags |
| --- | --- |
| `glacier` | `natural=glacier` |
| `bay` | `natural=bay` |
| `fjord` | `natural=fjord` |
| `strait` | `natural=strait` |
| `beach` | `natural=beach` |
| `wetland` | `natural=wetland` |
| `reservoir` | `water=reservoir` or `landuse=reservoir` |
| `lake` | `natural=water` with absent `water`, or `water=lake/pond/lagoon` |
| `island` | `place=island/islet` |
| `forest` | `natural=wood` or `landuse=forest` |
| `park` | `leisure=park/nature_reserve` or `boundary=national_park/protected_area` |

Only polygon geometry is eligible, and `area=no` is excluded. A name is required;
`name_int` is used if the normal name is absent. Standard name and translation
attributes are copied through the existing name helper. This layer does not add
new Wikidata download matching; available translations and OSM name tags are used.
Bays, straits, and other features mapped only as points cannot acquire a line from
this layer. Ridges, valleys, and ranges are not inferred from polygon shape or
terrain; existing mapped mountain lines remain in their existing layer.

## Geometry and zoom behavior

The layer contains `LineString` and `Point` features. `placement` is `line` or
`point`. Every eligible source retains a point from its initial visibility zoom
through the archive maximum zoom, even where a line is also emitted. Point
features have a boolean `line_available` attribute indicating whether this source
also emits a line at that tile zoom. Other source objects with duplicate names
are not deduplicated. Providing both geometries increases tile size.

- Work uses the full validated source polygon before tile clipping. For a
  multipolygon, only its largest connected polygon receives a label.
- Compact polygons (oriented bounding-box aspect ratio below 1.5) use a point.
- For elongated polygons, inset the shape by 4% of its bounding-box width. Sample
  31 cross sections along each of three directions around the long axis. Follow
  the widest interval at each section and split paths where connections would
  cross the exterior or a hole. No branches are emitted.
- Smooth candidate paths with three weighted averaging passes. Reject sharp turns
  above 25 degrees, self-intersections, paths outside the inset, and paths spanning
  less than 45% of the original long-axis length. Choose the longest accepted path.
- Polygons above 20,000 vertices skip line generation to bound expensive overlays.
  Invalid line operations fall back to a point; unreadable polygons are skipped
  with a data-error counter. Point labels use the largest polygon's interior point.
- Point visibility starts when the square root of polygon area reaches 48 pixels,
  no earlier than z5, capped at the archive maximum zoom so small features remain
  available when overzoomed.
- A line becomes available alongside the point when its length reaches the larger of 160 pixels or
  12 pixels per character of the default name, no earlier than the point's zoom.
  If that zoom exceeds the archive maximum, only the point is emitted.
- Generated paths are not subsequently simplified by the feature's tolerance
  settings. A 128-pixel tile buffer supplies geometry around tile edges.

This is a bounded label-path heuristic, not a glacier flowline or a full medial-axis
solver. Strongly curved, branched, very complex, or compact features can remain
points. Clearance is geometric; it does not guarantee that every font size, halo,
translation, or viewing angle fits inside the polygon. Zoom transitions are baked
into the tiles, not recomputed for the active basemap font. `line_available` means
a line was emitted, not that it is visible in every tile fragment or accepted by
the renderer. The automatic selection filters below do not retry a point when a
line is rejected by collision or text-fit rules. Overzoomed tiles retain their
stored availability flag.

## Activate one category in the basemap

For example, use two symbol layers targeting source-layer `polygon_label` and the
existing vector source. The following glacier example selects lines when available
and points otherwise. Initially leave both hidden, then enable them together.

```json
{
  "id": "polygon-label-glacier-line",
  "type": "symbol",
  "source": "YOUR_EXISTING_VECTOR_SOURCE",
  "source-layer": "polygon_label",
  "filter": ["all", ["==", ["get", "class"], "glacier"], ["==", ["get", "placement"], "line"]],
  "layout": {
    "visibility": "none",
    "symbol-placement": "line-center",
    "text-field": ["get", "name"],
    "text-size": 14,
    "text-max-angle": 25,
    "text-keep-upright": true
  }
}
```

```json
{
  "id": "polygon-label-glacier-point",
  "type": "symbol",
  "source": "YOUR_EXISTING_VECTOR_SOURCE",
  "source-layer": "polygon_label",
  "filter": ["all", ["==", ["get", "class"], "glacier"], ["==", ["get", "placement"], "point"], ["==", ["get", "line_available"], false]],
  "layout": {
    "visibility": "none",
    "symbol-placement": "point",
    "text-field": ["get", "name"],
    "text-size": 14,
    "text-max-width": 10
  }
}
```

For **parks that always use a point**, use only the point symbol layer, change its
ID to `polygon-label-park-point`, and replace its filter with:

```json
["all", ["==", ["get", "class"], "park"], ["==", ["get", "placement"], "point"]]
```

This deliberately ignores `line_available`, retaining the centered point even for
elongated parks. The same pattern works for forests, wetlands, or any other class.
An icon can be added to this point symbol layer using the basemap's existing sprite.
Do not enable a line layer for the same class alongside these unrestricted points.
To return to automatic placement, enable the line layer and restore the
`line_available == false` condition on its companion point layer. Both choices
work with the same newly generated archive; switching styles requires no rebuild.

Use the basemap's existing font, colors, halo, and language expression. Replace
`glacier` to activate another class independently. When enabling a class, suppress
its previous labels in the basemap to avoid duplicates; the generator preserves
those existing layers for compatibility. `line-center` applies to rendered line
fragments, not a globally unique anchor: tile seams and duplicate placement still
need visual acceptance. `symbol-placement: line` is an alternative if repeated
labels are desired for long features.

## Verification and remaining acceptance

Source/API review and diff checks only. No unit tests, test harnesses, or repository
typechecks were run, per repository instructions. No archive generation or map
rendering was performed for this change.

Before basemap activation, generate a regional archive and inspect:

- Rainier glaciers, including small compact glaciers and branching glacier bodies.
- Long lakes/reservoirs, lakes with islands, and round lakes.
- Curved bays/fjords/straits, narrow beaches and elongated islands.
- Named forests, wetlands, parks, and disconnected protected-area multipolygons.
- Point-only parks at every zoom, automatic point-to-line transitions, translated names, tile seams, map rotation,
  pitched views, and web/mobile collision behavior.
- Generation time, archive size, error counters, and dense tiles before a planet run.

Generation and visual acceptance are still required to establish placement quality
and performance on real data. The geometric checks are not rendering acceptance.

# Trail segment distances

The default-enabled `trail_segment_label` vector layer contains one point per connected
trail segment, measured before simplification and tile clipping. Existing
transportation layers and generated mappings are unchanged. New archive builds
include it without an additional flag. Use `--trail-segment-labels=false` to
disable it. This does not modify existing archives or client styles.

## Segment definition

Eligible ways are `highway=path|footway|steps|track`, excluding areas, indoor ways,
sidewalks/crossings, explicitly disused/abandoned/construction ways, and ways
prohibiting foot access. An explicit foot allowance can override generic
`access=no|private`; `foot=no|private` always excludes a way. These filters are
cartographic eligibility, not a guarantee of legal access.

The graph uses OSM node IDs, not coordinate proximity. Junctions, dead ends, and
connections to other highway ways terminate segments. An excluded way still
terminates adjacent eligible trails where they share a node. Bridges crossing
without a shared node do not create junctions. Changes to a way's name, surface,
or OSM ID alone do not split a segment. Duplicate edges with the same two OSM
node IDs count once. Isolated degree-two loops receive one circumference label;
a loop attached at a junction also receives one label.

Distances use Planetiler's spherical ground-distance calculation along the full
source geometry. They are horizontal distances without elevation adjustment.
Segments shorter than 25 metres are omitted. Invalid or incomplete way geometry
invalidates its nodes, suppressing adjacent segment labels instead of publishing
truncated distances. This cannot detect missing ways entirely absent from an OSM
extract; use a complete, buffered extract around the review area.

## Tile fields and placement

| Field | Type | Meaning |
| --- | --- | --- |
| `length_m` | Integer | Complete segment length rounded to metres |
| `bearing` | Integer | Clockwise angle from map east, normalized to -90–90 degrees |
| `loop` | Integer | 1 for a closed segment, otherwise 0 |

Points appear from zoom 14, at half the cumulative ground distance along each
segment. Bearing follows the source edge containing the midpoint. Point tile
buffer is zero to avoid intentionally copying labels into neighboring tiles.
Feature IDs and traversal order are deterministic for the same input graph;
IDs are archive-local and must not be used as persistent route identifiers.

## Client styling

`styles/trail-segment-labels.ts` exports dependency-free TypeScript helpers:

```typescript
import {
  createTrailDistanceText,
  createTrailLabelRotation,
  createTrailSegmentLabelLayer,
} from './trail-segment-labels.js';

const layer = createTrailSegmentLabelLayer({
  source: 'openmaptiles', // existing vector source containing the new archive
  units: 'metric', // use the app's existing unit preference
  fontStack: ['Noto Sans Regular'], // substitute a font served by the host style
  mapBearing: map.getBearing(),
});

map.addLayer(layer);
// On a units preference change; no archive reload or separate download needed:
map.setLayoutProperty(layer.id, 'text-field', createTrailDistanceText('imperial'));

// Keep point labels upright as the map rotates. Remove this listener on teardown.
const updateRotation = () => map.setLayoutProperty(
  layer.id, 'text-rotate', createTrailLabelRotation(map.getBearing()),
);
map.on('rotate', updateRotation);
// During map/component cleanup: map.off('rotate', updateRotation);
```

Metric labels use km to one decimal, with metres below 100 m. Imperial labels
use miles to one decimal, with feet below 0.1 mi. Short distances round to the
nearest 10 m/ft, avoiding misleading precision or zero-distance labels. Both
variants show explicit units, with collision suppression enabled. Choose layer
ordering in the host style so trail names have priority over distances. Pass
appropriate text/halo colours for dark styles.

The helper is not yet wired into Outmap web/mobile repositories. Native clients
must apply the equivalent symbol layout through their existing map API and
update it from the existing units preference. Confirm `number-format` support
and rotation/collision behaviour in each installed renderer. Older archives
without this source layer simply have no distance labels.

## Build limits and rollout

This implementation retains the regional trail graph in memory and serializes
graph mutations. Defaults cap it at 2,000,000 nodes and 2,000,000 edges via
`--trail-segment-max-nodes` and `--trail-segment-max-edges`. Exceeding either cap
fails at OSM completion before labels are emitted; a partial graph is never
used. Do not increase these limits for a planet build without measuring memory
and runtime. A planet-scale storage approach remains a separate decision.

No new dependencies, unit tests, typechecks, or upstream regeneration are
required or introduced. Although generation is enabled by default, the graph
limits still apply and planet-scale generation remains unvalidated. Before a
production build:

- Generate a fresh regional archive covering Mont Saint-Hilaire with a buffer.
- Inspect lengths against full input paths across way changes and tile edges.
- Check a branching junction, road connection, grade-separated crossing,
  attached loop, isolated loop, duplicate way, and incomplete geometry.
- Render metric/imperial styles at zooms 14–18, including rotation, pitch,
  dense contours, short trails, tile boundaries, and offline reloads.
- Confirm trail names win collisions; inspect antimeridian and world-wrap labels
  if those regions are included.
- Record peak memory, generation time, archive size, and per-tile label counts
  before considering broader coverage.

These archive and rendering checks have not yet been performed. Follow the
immutable planet release procedure for any eventual publication.

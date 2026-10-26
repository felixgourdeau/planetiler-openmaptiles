/** Additive styles for the single-archive outdoor detail layer and existing building geometry. */
type Expression = (string | number | boolean | Expression)[];
type StyleValue = string | number | boolean | Expression;

export interface OutdoorDetailLayer {
  id: string;
  type: 'line' | 'fill' | 'symbol' | 'fill-extrusion';
  source: string;
  'source-layer': string;
  minzoom: number;
  filter?: Expression;
  layout?: Record<string, StyleValue>;
  paint: Record<string, StyleValue>;
}

/** Place below labels, above terrain/landcover. Fine details are intentionally high-zoom. */
export const createOutdoorDetailLayers = (
  source: string,
  fontStack: string[],
  dark = false,
): OutdoorDetailLayer[] => {
  const foreground = dark ? '#d5d9de' : '#5b6268';
  return [
    {
      id: 'outmap-outdoor-areas', type: 'fill', source, 'source-layer': 'outdoor', minzoom: 14,
      filter: ['==', ['geometry-type'], 'Polygon'],
      paint: { 'fill-color': foreground, 'fill-opacity': 0.09 },
    },
    {
      id: 'outmap-outdoor-lines', type: 'line', source, 'source-layer': 'outdoor', minzoom: 14,
      paint: {
        'line-color': ['match', ['get', 'class'], 'piste', '#5388b0', 'whitewater', '#287da9',
          'via_ferrata', '#ad663d', foreground],
        'line-width': 1.3,
        'line-opacity': 0.8,
      },
    },
    {
      id: 'outmap-water-engineering', type: 'line', source, 'source-layer': 'waterway', minzoom: 14,
      filter: ['in', ['get', 'class'], ['literal', ['dam', 'weir']]],
      paint: { 'line-color': foreground, 'line-width': 2.2 },
    },
    {
      id: 'outmap-outdoor-names', type: 'symbol', source, 'source-layer': 'outdoor', minzoom: 14,
      filter: ['has', 'name'],
      layout: { 'symbol-placement': 'line', 'text-field': ['get', 'name'],
        'text-font': ['literal', fontStack], 'text-size': 11 },
      paint: { 'text-color': foreground, 'text-halo-color': dark ? '#25282b' : '#ffffff', 'text-halo-width': 1 },
    },
  ];
};

/** Uses existing height/base/colour fields; roof tags are metadata, not rendered roof meshes. */
export const createBuildingExtrusionLayer = (source: string, dark = false): OutdoorDetailLayer => ({
  id: 'outmap-buildings-3d',
  type: 'fill-extrusion',
  source,
  'source-layer': 'building',
  minzoom: 14,
  filter: ['all', ['!=', ['get', 'hide_3d'], true], ['!=', ['get', 'hide_3d'], 1]],
  paint: {
    'fill-extrusion-height': ['coalesce', ['get', 'render_height'], 0],
    'fill-extrusion-base': ['coalesce', ['get', 'render_min_height'], 0],
    'fill-extrusion-color': ['coalesce', ['get', 'colour'], dark ? '#666e77' : '#d4ccbd'],
    'fill-extrusion-opacity': 0.9,
  },
});

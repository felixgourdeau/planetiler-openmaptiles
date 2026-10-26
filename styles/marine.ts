/** Additive marine styling for the existing poi and landcover layers. */
type Expression = string | number | boolean | Expression[];
type Stroke = readonly [number, number, number, number];

export interface MarineImage {
  width: number;
  height: number;
  data: Uint8Array;
}

export interface MarineStyleLayer {
  id: string;
  type: 'symbol' | 'fill';
  source: string;
  'source-layer': 'poi' | 'landcover';
  minzoom: number;
  filter: Expression[];
  layout?: Record<string, Expression>;
  paint: Record<string, Expression>;
}

// Original 24px line drawings. Generated RGBA images need neither a remote sprite nor a new dependency.
const wave: Stroke[] = [[3, 19, 7, 17], [7, 17, 12, 19], [12, 19, 17, 17], [17, 17, 21, 19]];
const anchor: Stroke[] = [[12, 3, 12, 19], [8, 7, 16, 7], [5, 12, 5, 15], [5, 15, 12, 20],
  [12, 20, 19, 15], [19, 15, 19, 12], [9, 3, 15, 3]];
const pier: Stroke[] = [[4, 6, 20, 6], [7, 6, 7, 16], [12, 6, 12, 14], [17, 6, 17, 16], ...wave];
const swimming: Stroke[] = [[15, 8, 17, 8], [17, 8, 17, 10], [17, 10, 15, 10], [15, 10, 15, 8],
  [4, 13, 10, 9], [10, 9, 14, 14], [8, 14, 14, 14], ...wave];
const canoe: Stroke[] = [[3, 13, 6, 17], [6, 17, 18, 17], [18, 17, 21, 13], [3, 13, 21, 13],
  [9, 5, 17, 20], [15, 17, 18, 20]];

const iconStrokes: Readonly<Record<string, readonly Stroke[]>> = {
  wreck: [[4, 12, 7, 16], [7, 16, 12, 17], [14, 17, 18, 16], [18, 16, 21, 12],
    [4, 12, 10, 13], [15, 13, 21, 12], [12, 4, 10, 11], [12, 4, 18, 10],
    [18, 10, 14, 10], [12, 11, 14, 13], [14, 13, 12, 15], ...wave],
  lighthouse: [[8, 20, 10, 9], [10, 9, 14, 9], [14, 9, 16, 20], [7, 20, 17, 20],
    [9, 8, 15, 8], [9, 8, 9, 5], [15, 8, 15, 5], [9, 5, 12, 3], [12, 3, 15, 5],
    [3, 5, 6, 6], [18, 6, 21, 5], [9, 15, 15, 15]],
  buoy: [[8, 15, 10, 10], [10, 10, 14, 10], [14, 10, 16, 15], [8, 15, 16, 15],
    [12, 10, 12, 4], [10, 6, 14, 6], ...wave],
  beacon: [[7, 20, 12, 9], [12, 9, 17, 20], [9, 16, 15, 16], [12, 4, 9, 8],
    [9, 8, 15, 8], [15, 8, 12, 4]],
  slipway: [[4, 5, 20, 16], [4, 9, 15, 17], [8, 5, 18, 12], [8, 5, 10, 10],
    [10, 10, 18, 12], ...wave],
  surfing: [[4, 14, 7, 9], [7, 9, 12, 7], [12, 7, 17, 8], [17, 8, 19, 11],
    [19, 11, 15, 10], [15, 10, 13, 12], [13, 12, 15, 15], [7, 15, 20, 15], ...wave],
  marine_rock: [[5, 15, 9, 8], [9, 8, 12, 12], [12, 12, 16, 6], [16, 6, 20, 15], ...wave],
  reef: [[12, 17, 12, 5], [12, 11, 7, 8], [7, 8, 7, 5], [12, 14, 18, 10],
    [18, 10, 18, 6], [12, 8, 16, 5], ...wave],
  shoal: [[5, 13, 9, 10], [9, 10, 15, 10], [15, 10, 19, 13], [5, 13, 19, 13], ...wave],
  marina: anchor,
  dock: pier,
  pier,
  swimming,
  scuba_diving: [[4, 7, 10, 7], [10, 7, 12, 10], [12, 10, 14, 7], [14, 7, 20, 7],
    [20, 7, 19, 13], [19, 13, 14, 13], [14, 13, 12, 10], [12, 10, 10, 13],
    [10, 13, 5, 13], [5, 13, 4, 7], [20, 7, 20, 3], ...wave],
  canoe,
  rowing: canoe,
  sailing: [[12, 3, 12, 15], [12, 3, 5, 13], [5, 13, 12, 13], [14, 7, 19, 13],
    [19, 13, 14, 13], [4, 16, 20, 16], ...wave],
  water_ski: [[9, 4, 11, 4], [10, 7, 8, 12], [8, 12, 13, 14], [13, 14, 15, 18],
    [10, 8, 15, 10], [15, 10, 21, 6], [5, 19, 19, 19]],
};

const strokeDistance = (x: number, y: number, [x1, y1, x2, y2]: Stroke): number => {
  const dx = x2 - x1;
  const dy = y2 - y1;
  const lengthSquared = dx * dx + dy * dy;
  const fraction = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / lengthSquared));
  return Math.hypot(x - x1 - fraction * dx, y - y1 - fraction * dy);
};

const rasterize = (
  strokes: readonly Stroke[],
  color: readonly [number, number, number],
  width = 48,
): MarineImage => {
  const data = new Uint8Array(width * width * 4);
  for (let y = 0; y < width; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const distance = Math.min(...strokes.map((stroke) => strokeDistance((x + 0.5) * 24 / width, (y + 0.5) * 24 / width, stroke)));
      const offset = (y * width + x) * 4;
      data.set(color, offset);
      data[offset + 3] = Math.round(255 * Math.max(0, Math.min(1, (1.05 - distance) * 2)));
    }
  }
  return { width, height: width, data };
};

/** Register every returned image with pixelRatio: 2 and sdf: false, again after a style reload. */
export const createMarineImages = (
  prefix = 'outmap-marine',
  iconColor: readonly [number, number, number] = [62, 91, 101],
): Record<string, MarineImage> => {
  const images: Record<string, MarineImage> = {};
  for (const [kind, strokes] of Object.entries(iconStrokes)) {
    images[`${prefix}-${kind}`] = rasterize(strokes, iconColor);
  }
  images[`${prefix}-shoal-pattern`] = rasterize([[6, 6, 6, 6], [18, 18, 18, 18]], [92, 151, 171], 64);
  images[`${prefix}-reef-pattern`] = rasterize([[4, 6, 8, 6], [6, 4, 6, 8], [16, 18, 20, 18], [18, 16, 18, 20]], [67, 130, 121], 64);
  images[`${prefix}-tidal-pattern`] = rasterize([[3, 6, 9, 6], [15, 18, 21, 18]], [132, 137, 114], 64);
  return images;
};

/** Insert overlays above water fills and below roads; insert poi among existing POI symbols. */
export const createMarineLayers = ({
  source,
  fontStack,
  prefix = 'outmap-marine',
  textColor = '#3e5b65',
  haloColor = '#f5f3e8',
}: {
  source: string;
  fontStack: string[];
  prefix?: string;
  textColor?: string;
  haloColor?: string;
}): { overlays: MarineStyleLayer[]; poi: MarineStyleLayer } => ({
  overlays: [
    ...['reef', 'shoal'].map((kind): MarineStyleLayer => ({
      id: `${prefix}-${kind}-fill`, type: 'fill', source, 'source-layer': 'landcover', minzoom: 7,
      filter: ['==', ['get', 'class'], kind],
      paint: { 'fill-pattern': `${prefix}-${kind}-pattern`, 'fill-opacity': 0.65 },
    })),
    {
      id: `${prefix}-tidal-fill`, type: 'fill', source, 'source-layer': 'landcover', minzoom: 7,
      filter: ['any', ['==', ['get', 'subclass'], 'tidalflat'],
        ['all', ['==', ['get', 'tidal'], 'yes'],
          ['in', ['get', 'class'], ['literal', ['mud', 'sand', 'wetland']]]]],
      paint: { 'fill-pattern': `${prefix}-tidal-pattern`, 'fill-opacity': 0.65 },
    },
  ],
  poi: {
    id: `${prefix}-poi`, type: 'symbol', source, 'source-layer': 'poi', minzoom: 12,
    filter: ['has', 'marine_kind'],
    layout: {
      'icon-image': ['concat', prefix, '-', ['match', ['get', 'marine_kind'],
        ['seamark', 'mooring'], 'marina', ['get', 'marine_kind']]],
      'icon-size': 0.85,
      'icon-allow-overlap': false,
      'text-field': ['coalesce', ['get', 'name'], ''],
      'text-font': fontStack,
      'text-size': 11,
      'text-offset': [0, 1.3],
      'text-anchor': 'top',
      'text-optional': true,
      'text-allow-overlap': false,
      'symbol-sort-key': ['coalesce', ['get', 'rank'], 1000],
    },
    paint: { 'text-color': textColor, 'text-halo-color': haloColor, 'text-halo-width': 1.2 },
  },
});

/** Expression-style filters only. Apply to generic POIs only while the marine layer is enabled. */
export const excludeMarinePois = (existingFilter?: Expression[]): Expression[] => existingFilter
  ? ['all', existingFilter, ['!', ['has', 'marine_kind']]]
  : ['!', ['has', 'marine_kind']];

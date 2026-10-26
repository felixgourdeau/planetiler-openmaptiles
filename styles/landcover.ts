/** Framework-independent MapLibre/Mapbox fill layers for Outmap's extended schema. */
export interface LandcoverFillLayer {
  id: string;
  type: 'fill';
  source: string;
  'source-layer': 'landcover' | 'water';
  filter: ['==', ['get', 'class'], string];
  paint: {
    'fill-color': string;
    'fill-opacity': number;
  };
}

export interface LandcoverStyleLayers {
  /** Place below water fills, roads, buildings, and labels; replaces existing landcover fills. */
  ground: LandcoverFillLayer[];
  /** Place with existing water fills. Only matches the new generic water class. */
  water: LandcoverFillLayer[];
  /** Place above water fills, but below roads and labels. */
  aquaticOverlays: LandcoverFillLayer[];
}

const groundColors: Readonly<Record<string, string>> = {
  farmland: '#e2dfbf',
  ice: '#eef7fa',
  wood: '#b8cdaa',
  rock: '#c8c6bf',
  grass: '#d2dfb7',
  wetland: '#b8ccc0',
  sand: '#eadfbb',
  mud: '#bbae95',
  bare_ground: '#d4cbbb',
  mostly_rock: '#c4c5b5',
  greenery: '#c4d5b3',
  dry_swamp: '#c4c5a7',
  other: '#d5d2cb',
};

/** Colors are defaults, not a claim about the material of unknown landcover values. */
export const createLandcoverLayers = (source: string, idPrefix = 'outmap-cover'): LandcoverStyleLayers => {
  const fill = (
    className: string,
    color: string,
    sourceLayer: 'landcover' | 'water' = 'landcover',
    opacity = 1,
  ): LandcoverFillLayer => ({
    id: `${idPrefix}-${sourceLayer}-${className}`,
    type: 'fill',
    source,
    'source-layer': sourceLayer,
    filter: ['==', ['get', 'class'], className],
    paint: { 'fill-color': color, 'fill-opacity': opacity },
  });

  return {
    ground: Object.entries(groundColors).map(([className, color]) => fill(className, color)),
    water: [fill('water', '#a6cadb', 'water')],
    aquaticOverlays: [fill('reef', '#6caaa2', 'landcover', 0.55), fill('shoal', '#d4cc9e', 'landcover', 0.55)],
  };
};

/** Add only with a reviewed Iceland pilot archive; keep attribution visible on both clients. */
export const createIcelandNameLayer = (source: string, fonts: string[]) => ({
  id: 'outmap-iceland-names',
  type: 'symbol' as const,
  source,
  'source-layer': 'iceland_name',
  minzoom: 12,
  layout: {
    'text-field': ['get', 'name'],
    'text-font': fonts,
    'text-size': 13,
    'text-max-width': 8,
    'text-padding': 4,
    'text-allow-overlap': false,
    visibility: 'visible',
  },
  paint: { 'text-color': '#424242', 'text-halo-color': '#ffffff', 'text-halo-width': 1.5 },
});

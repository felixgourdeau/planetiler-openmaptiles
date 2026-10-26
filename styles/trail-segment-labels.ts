/** Metres are stored in the archive; changing units only changes this symbol layer. */
export type DistanceUnits = 'metric' | 'imperial';

type NumericExpression =
  | number
  | ['get', 'length_m' | 'bearing']
  | ['/', NumericExpression, number]
  | ['*', NumericExpression, number]
  | ['+' | '-' | '%', NumericExpression, number]
  | ['round' | 'abs', NumericExpression]
  | ['case', ['>', NumericExpression, number], NumericExpression, NumericExpression];

type LabelExpression =
  | ['concat', ['number-format', NumericExpression, {
      'min-fraction-digits': number;
      'max-fraction-digits': number;
    }], string]
  | ['case', ['<', NumericExpression, number], LabelExpression, LabelExpression];

export interface TrailSegmentLabelLayer {
  id: string;
  type: 'symbol';
  source: string;
  'source-layer': 'trail_segment_label';
  minzoom: number;
  layout: {
    'symbol-placement': 'point';
    'text-field': LabelExpression;
    'text-font': string[];
    'text-size': number;
    'text-rotate': NumericExpression;
    'text-rotation-alignment': 'map';
    'text-pitch-alignment': 'map';
    'text-offset': [number, number];
    'text-padding': number;
    'text-allow-overlap': boolean;
    'text-ignore-placement': boolean;
  };
  paint: {
    'text-color': string;
    'text-halo-color': string;
    'text-halo-width': number;
  };
}

const formatDistance = (value: NumericExpression, decimals: number, suffix: string): LabelExpression => [
  'concat',
  ['number-format', value, { 'min-fraction-digits': decimals, 'max-fraction-digits': decimals }],
  suffix,
];

/** Use with setLayoutProperty when the user's existing distance preference changes. */
export const createTrailDistanceText = (units: DistanceUnits): LabelExpression => {
  const metres: NumericExpression = ['get', 'length_m'];
  if (units === 'imperial') {
    return [
      'case', ['<', metres, 160.9344],
      formatDistance(['*', ['round', ['/', metres, 3.048]], 10], 0, ' ft'),
      formatDistance(['/', metres, 1609.344], 1, ' mi'),
    ];
  }
  return [
    'case', ['<', metres, 100],
    formatDistance(['*', ['round', ['/', metres, 10]], 10], 0, ' m'),
    formatDistance(['/', metres, 1000], 1, ' km'),
  ];
};

/** Point symbols need explicit upright handling as the camera bearing changes. */
export const createTrailLabelRotation = (mapBearing = 0): NumericExpression => {
  const normalizedBearing = ((mapBearing % 360) + 360) % 360;
  const trailBearing: NumericExpression = ['get', 'bearing'];
  const screenAngle: NumericExpression = [
    '-', ['%', ['+', ['-', trailBearing, normalizedBearing], 540], 360], 180,
  ];
  return ['case', ['>', ['abs', screenAngle], 90], ['+', trailBearing, 180], trailBearing];
};

/** Pass a font stack present in the host style's glyph source. */
export const createTrailSegmentLabelLayer = ({
  source,
  units,
  fontStack,
  mapBearing = 0,
  id = 'trail-segment-distances',
  textColor = '#66665b',
  haloColor = '#f5f3e8',
}: {
  source: string;
  units: DistanceUnits;
  fontStack: string[];
  mapBearing?: number;
  id?: string;
  textColor?: string;
  haloColor?: string;
}): TrailSegmentLabelLayer => ({
  id,
  type: 'symbol',
  source,
  'source-layer': 'trail_segment_label',
  minzoom: 14,
  layout: {
    'symbol-placement': 'point',
    'text-field': createTrailDistanceText(units),
    'text-font': [...fontStack],
    'text-size': 10,
    'text-rotate': createTrailLabelRotation(mapBearing),
    'text-rotation-alignment': 'map',
    'text-pitch-alignment': 'map',
    'text-offset': [0, -0.8],
    'text-padding': 4,
    'text-allow-overlap': false,
    'text-ignore-placement': false,
  },
  paint: {
    'text-color': textColor,
    'text-halo-color': haloColor,
    'text-halo-width': 1,
  },
});

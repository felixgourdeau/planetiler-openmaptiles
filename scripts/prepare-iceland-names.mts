import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { parseArgs } from 'node:util';

type RecordValue = Record<string, unknown>;
type Position = [number, number];
type Bounds = [number, number, number, number];
const record = (value: unknown): value is RecordValue =>
  typeof value === 'object' && value !== null && !Array.isArray(value);
const normalize = (name: string): string => name.normalize('NFC').toLocaleLowerCase('is').trim().replace(/\s+/g, ' ');
const positions = (value: unknown): Position[] => {
  if (!Array.isArray(value)) throw new Error('Invalid geometry coordinates');
  if (typeof value[0] === 'number' && typeof value[1] === 'number') {
    if (!Number.isFinite(value[0]) || !Number.isFinite(value[1]) ||
      Math.abs(value[0]) > 180 || Math.abs(value[1]) > 85) throw new Error('Invalid geographic coordinate');
    return [[value[0], value[1]]];
  }
  return value.flatMap(positions);
};
const bounds = (points: Position[]): Bounds => {
  if (!points.length) throw new Error('Empty geometry');
  return points.reduce<Bounds>((box, [x, y]) =>
    [Math.min(box[0], x), Math.min(box[1], y), Math.max(box[2], x), Math.max(box[3], y)],
  [180, 90, -180, -90]);
};
const overlaps = (a: Bounds, b: Bounds, padding = 0): boolean =>
  a[0] <= b[2] + padding && a[2] >= b[0] - padding && a[1] <= b[3] + padding && a[3] >= b[1] - padding;
const request = async (url: string, init?: RequestInit): Promise<unknown> => {
  const response = await fetch(url, { ...init, signal: AbortSignal.timeout(120_000) });
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${url}`);
  return response.json();
};
const { values } = parseArgs({ options: {
  bbox: { type: 'string' }, output: { type: 'string' }, approved: { type: 'string' },
  snapshots: { type: 'string' },
  'osm-geojson': { type: 'string' },
} });
if (!values.bbox || !values.output) throw new Error('Use --bbox=west,south,east,north --output=NEW_DIRECTORY [--approved=ids.json] [--snapshots=EXISTING_DIRECTORY]');
const coordinates = values.bbox.split(',').map(Number);
if (coordinates.length !== 4 || !coordinates.every(Number.isFinite)) throw new Error('Invalid bbox');
const [west, south, east, north] = coordinates;
if (west < -25 || east > -12 || south < 63 || north > 67.5 || west >= east || south >= north ||
  east - west > 0.5 || north - south > 0.5) throw new Error('Pilot bbox must be in Iceland and at most 0.5 degrees per side');
const area: Bounds = [west, south, east, north];
const output = resolve(values.output);
await mkdir(output); // Exclusive output directory prevents replacing evidence or an approved snapshot.
const save = async (name: string, value: unknown): Promise<string> => {
  const text = JSON.stringify(value, null, 2) + '\n';
  await writeFile(resolve(output, name), text, { flag: 'wx' });
  return createHash('sha256').update(text).digest('hex');
};
const official: RecordValue[] = [];
const hashes: Record<string, string> = {};
for (const kind of ['punktar', 'linur', 'flakar']) {
  const filename = `official-${kind}.json`;
  let snapshot: unknown;
  if (values.snapshots) snapshot = JSON.parse(await readFile(resolve(values.snapshots, filename), 'utf8'));
  else {
    const url = new URL('https://gis.lmi.is/geoserver/wfs');
    for (const [key, value] of Object.entries({ service: 'WFS', version: '2.0.0', request: 'GetFeature',
      typeNames: `IS_50V:ornefni_${kind}`, outputFormat: 'application/json', count: '5000',
      srsName: 'urn:ogc:def:crs:OGC:1.3:CRS84', bbox: `${values.bbox},urn:ogc:def:crs:OGC:1.3:CRS84` })) url.searchParams.set(key, value);
    snapshot = await request(url.toString());
  }
  if (!record(snapshot) || !Array.isArray(snapshot.features) || typeof snapshot.numberMatched !== 'number' ||
    snapshot.numberMatched !== snapshot.features.length) throw new Error(`Incomplete WFS response: ${kind}; reduce bbox`);
  hashes[filename] = await save(filename, snapshot);
  for (const feature of snapshot.features) {
    if (!record(feature) || !record(feature.geometry) || !record(feature.properties)) throw new Error('Invalid official feature');
    if (!['Point', 'MultiPoint', 'LineString', 'MultiLineString', 'Polygon', 'MultiPolygon'].includes(String(feature.geometry.type))) throw new Error('Unsupported geometry');
    official.push(feature);
  }
}
const query = `[out:json][timeout:90];nwr[~"^(name|name:.*|alt_name|old_name|loc_name|int_name)$"~"."](${south - 0.05},${west - 0.05},${north + 0.05},${east + 0.05});out geom;`;
const osm: unknown = values['osm-geojson'] ? await (async () => {
  const data: unknown = JSON.parse(await readFile(values['osm-geojson']!, 'utf8'));
  if (!record(data) || !Array.isArray(data.features)) throw new Error('Expected osmium GeoJSON FeatureCollection');
  const elements = data.features.map((feature: unknown) => {
    if (!record(feature) || !record(feature.geometry) || !record(feature.properties) || !feature.id) throw new Error('Export OSM with --add-unique-id=type_id');
    const box = bounds(positions(feature.geometry.coordinates));
    return { type: 'export', id: feature.id, tags: feature.properties,
      bounds: { minlon: box[0], minlat: box[1], maxlon: box[2], maxlat: box[3] } };
  });
  return { elements, sourceFile: resolve(values['osm-geojson']!) };
})() : values.snapshots ? JSON.parse(await readFile(resolve(values.snapshots, 'osm.json'), 'utf8')) :
  await request('https://overpass-api.de/api/interpreter', { method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ data: query }) });
if (!record(osm) || !Array.isArray(osm.elements) || osm.remark) throw new Error('Incomplete Overpass response');
hashes['osm.json'] = await save('osm.json', osm);
const osmNames = osm.elements.map((element: unknown) => {
  if (!record(element) || !record(element.tags)) throw new Error('Invalid OSM feature');
  const geometry = record(element.bounds) ? element.bounds : null;
  const box = geometry ? [geometry.minlon, geometry.minlat, geometry.maxlon, geometry.maxlat] :
    [element.lon, element.lat, element.lon, element.lat];
  if (!box.every(value => typeof value === 'number' && Number.isFinite(value))) throw new Error('Missing OSM geometry');
  const names = Object.entries(element.tags).filter(([key]) => /^(name(:.*)?|alt_name|old_name|loc_name|int_name)$/.test(key))
    .flatMap(([, value]) => typeof value === 'string' ? value.split(';').map(normalize) : []);
  return { id: `${element.type}/${element.id}`, names, bounds: box as Bounds };
});
const approvedInput: unknown = values.approved ? JSON.parse(await readFile(values.approved, 'utf8')) : [];
if (!Array.isArray(approvedInput) || !approvedInput.every(id => typeof id === 'string')) throw new Error('Approval must be an array of reviewed UUIDs');
const approved = new Set<string>(approvedInput);
const seen = new Set<string>();
const imported: RecordValue[] = [];
const officialNames = official.map(feature => {
  const properties = feature.properties as RecordValue;
  return { id: properties.uuid, name: normalize(String(properties.ornefni)),
    bounds: bounds(positions((feature.geometry as RecordValue).coordinates)) };
});
const report = official.map(feature => {
  const properties = feature.properties as RecordValue;
  const geometry = feature.geometry as RecordValue;
  const id = properties.uuid;
  const name = properties.ornefni;
  if (typeof id !== 'string' || !id || typeof name !== 'string' || !name.trim()) throw new Error('Missing official identity/name');
  if (seen.has(id)) throw new Error(`Duplicate official UUID ${id}; review before importing`);
  seen.add(id);
  const box = bounds(positions(geometry.coordinates));
  if (!overlaps(box, area)) throw new Error('Official response outside requested bbox');
  const aliases = [name, ...(typeof properties.tvinefni === 'string' ? properties.tvinefni.split(';') : [])].map(normalize);
  const matches = osmNames.filter(item => overlaps(box, item.bounds, 0.05) && item.names.some(value => aliases.includes(value)));
  const status = matches.length ? 'osm-name-match' : 'review-required';
  if (approved.has(id)) {
    if (matches.length) throw new Error(`Approved feature ${id} already matches OSM`);
    if (officialNames.some(item => item.id !== id && item.name === normalize(name) && overlaps(box, item.bounds, 0.05))) {
      throw new Error(`Approved feature ${id} has another nearby official feature with the same name; resolve duplication first`);
    }
    if (properties.tilvist !== 'Er til') throw new Error(`Approved feature ${id} is not marked existing`);
    imported.push({ type: 'Feature', id, geometry, properties: { name, 'name:is': name,
      official_id: id, class: properties.nafnberi, source: 'NÁTT / IS 50V Örnefni',
      source_revision: properties.gagnasafn, source_updated: properties.dagsuppfaerslu,
      license: 'CC-BY-4.0', review_status: 'approved', placement: 'point' } });
  }
  return { id, name, category: properties.nafnberi, geometry: geometry.type, bounds: box,
    status, matches: matches.map(item => item.id), approved: approved.has(id) };
});
for (const id of approved) if (!seen.has(id)) throw new Error(`Unknown approved UUID ${id}`);
hashes['comparison.json'] = await save('comparison.json', report);
hashes['approved.geojson'] = await save('approved.geojson', { type: 'FeatureCollection', features: imported });
await save('manifest.json', { bbox: area, createdAt: new Date().toISOString(), reusedSnapshots: values.snapshots ?? null,
  officialCount: official.length, osmCount: osmNames.length, approvedCount: imported.length, hashes,
  attribution: '© NÁTT / IS 50V Örnefni — CC BY 4.0',
  source: 'https://www.natt.is/is/midlun/kortagogn/ornefni',
  limitations: 'Name/bounding-box comparison is conservative, not semantic deduplication. Review unmatched features against OSM geometry and existing tiles before approving. No feature is imported automatically.' });
console.log(JSON.stringify({ output, officialCount: official.length, approvedCount: imported.length }));

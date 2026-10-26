import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { open, rename, rm, stat } from "node:fs/promises";
import { resolve } from "node:path";
import { createInterface } from "node:readline";
import { parseArgs } from "node:util";
import { setTimeout as delay } from "node:timers/promises";

const endpoint = "https://m3apwpaoxqoo5isj2pwjfbodgq0zyofc.lambda-url.ca-central-1.on.aws/elevations";
const maximumLatitude = 85.05112878;
type Point = {
  type: "point";
  id: string;
  kind: string;
  longitude: number;
  latitude: number;
  osmElevation: string | null;
};
type Sample = Point & { elevation: number | null; status: string; retrievedAt: string };
const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);
const validElevation = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value) && Math.abs(value) < 10_000;
const exists = async (path: string): Promise<boolean> => {
  try { await stat(path); return true; }
  catch (error: unknown) {
    if (isRecord(error) && error.code === "ENOENT") return false;
    throw error;
  }
};
const lines = (path: string) => createInterface({ input: createReadStream(path), crlfDelay: Infinity });
const hashFile = async (path: string): Promise<string> => {
  const hash = createHash("sha256");
  for await (const chunk of createReadStream(path)) hash.update(chunk);
  return hash.digest("hex");
};

// Only skip unambiguously numeric OSM heights here. Planetiler's existing parser
// remains authoritative and always wins over the sampled fallback, including units.
const candidate = (value: unknown): Point | null => {
  if (!isRecord(value) || value.type !== "Feature" || !isRecord(value.geometry) ||
    value.geometry.type !== "Point" || !isRecord(value.properties)) {
    throw new Error("Expected point Features from osmium export --add-unique-id=type_id.");
  }
  const kind = value.properties.natural;
  if (kind !== "peak" && kind !== "saddle" && kind !== "volcano") return null;
  if (typeof value.id !== "string" || !/^n[1-9]\d*$/.test(value.id)) {
    throw new Error("Missing OSM node ID; export with --add-unique-id=type_id.");
  }
  const coordinates = value.geometry.coordinates;
  if (!Array.isArray(coordinates) || coordinates.length < 2) throw new Error("Invalid coordinates.");
  const [longitude, latitude] = coordinates;
  if (typeof longitude !== "number" || typeof latitude !== "number" ||
    !Number.isFinite(longitude) || !Number.isFinite(latitude) ||
    Math.abs(longitude) > 180 || Math.abs(latitude) > 90) throw new Error("Invalid coordinates.");
  const raw = value.properties.ele;
  if (raw !== undefined && raw !== null && typeof raw !== "string" && typeof raw !== "number") {
    throw new Error("Unexpected ele type.");
  }
  const osmElevation = raw === undefined || raw === null ? null : String(raw);
  if (osmElevation !== null && /^-?(?:\d+(?:\.\d*)?|\.\d+)$/.test(osmElevation.trim()) &&
    validElevation(Number(osmElevation))) return null;
  return { type: "point", id: value.id, kind, longitude, latitude, osmElevation };
};

const fetchBatch = async (points: Point[]): Promise<Sample[]> => {
  const coordinates = points.map((point) => [point.longitude, point.latitude]);
  // The web endpoint expects a LineString. Duplicate a singleton only in transport.
  if (coordinates.length === 1) coordinates.push([...coordinates[0]]);
  let failure: unknown;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const response = await fetch(endpoint, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: "FeatureCollection", features: [{ type: "Feature", properties: {},
          geometry: { type: "LineString", coordinates } }] }),
        signal: AbortSignal.timeout(30_000),
      });
      if (!response.ok) throw new Error(`Elevation endpoint returned HTTP ${response.status}.`);
      const payload: unknown = await response.json();
      const elevations: unknown = Array.isArray(payload) ? payload : isRecord(payload) ? payload.elevations : null;
      if (!Array.isArray(elevations) || elevations.length !== coordinates.length ||
        elevations.some((value: unknown) => value !== null && !validElevation(value))) {
        throw new Error("Invalid elevation response: expected one finite, in-range number or null per coordinate.");
      }
      const retrievedAt = new Date().toISOString();
      return points.map((point, index) => ({ ...point, elevation: elevations[index] as number | null,
        status: elevations[index] === null ? "no_data" : "sampled", retrievedAt }));
    } catch (error: unknown) {
      failure = error;
      if (attempt < 2) await delay(1000 * 2 ** attempt);
    }
  }
  throw failure;
};

const main = async (): Promise<void> => {
  const { values } = parseArgs({ options: { input: { type: "string" }, snapshot: { type: "string" },
    "batch-size": { type: "string", default: "250" }, help: { type: "boolean" } } });
  if (values.help) {
    console.log("node scripts/backfill-peak-elevations.mts --input peaks.geojsonseq --snapshot elevations.jsonl [--batch-size=250]\nUses the web endpoint; resumes .partial for the identical input. Never overwrites a completed snapshot.");
    return;
  }
  if (!values.input || !values.snapshot) throw new Error("--input and --snapshot are required.");
  const batchSize = Number(values["batch-size"]);
  if (!Number.isInteger(batchSize) || batchSize < 1 || batchSize > 1000) throw new Error("Batch size must be 1–1000.");
  const input = resolve(values.input);
  const snapshot = resolve(values.snapshot);
  const partial = `${snapshot}.partial`;
  if ([snapshot, partial, `${snapshot}.lock`].includes(input)) throw new Error("Input and output paths must differ.");
  const lock = await open(`${snapshot}.lock`, "wx");
  try {
    if (await exists(snapshot)) throw new Error("Completed snapshot already exists; use a new snapshot path.");
    const inputSha256 = await hashFile(input);
    const header = { type: "header", version: 1, policy: "fill-gaps", endpoint, inputSha256 };
    const completed = new Map<string, string>();
    const digest = createHash("sha256");
    let count = 0;
    if (await exists(partial)) {
      let first = true;
      for await (const line of lines(partial)) {
        const row: unknown = JSON.parse(line);
        if (first) {
          if (JSON.stringify(row) !== JSON.stringify(header)) throw new Error("Partial snapshot belongs to another input/version/endpoint.");
          first = false;
        } else {
          if (!isRecord(row) || row.type !== "point" || typeof row.id !== "string" ||
            completed.has(row.id) || (row.elevation !== null && !validElevation(row.elevation))) {
            throw new Error("Invalid partial snapshot; preserve it for inspection and use a new path.");
          }
          completed.set(row.id, JSON.stringify([row.kind, row.longitude, row.latitude, row.osmElevation]));
          count += 1;
        }
        digest.update(`${line}\n`);
      }
      if (first) throw new Error("Empty partial snapshot; preserve it and use a new path.");
      // A successful append always ends with LF. Do not append after a torn write.
      const handle = await open(partial, "r");
      try {
        const size = (await handle.stat()).size;
        const last = Buffer.alloc(1);
        await handle.read(last, 0, 1, size - 1);
        if (last[0] !== 10) throw new Error("Partial snapshot has an interrupted trailing write; use a new path.");
      } finally { await handle.close(); }
    } else {
      const handle = await open(partial, "wx");
      try { await handle.writeFile(`${JSON.stringify(header)}\n`); await handle.sync(); }
      finally { await handle.close(); }
      digest.update(`${JSON.stringify(header)}\n`);
    }
    const output = await open(partial, "a");
    try {
      const persist = async (samples: Sample[]): Promise<void> => {
        const text = samples.map((sample) => `${JSON.stringify(sample)}\n`).join("");
        await output.writeFile(text); await output.sync(); digest.update(text); count += samples.length;
        console.error(`Checkpointed ${count} elevation results.`);
      };
      let pending: Point[] = [];
      const seen = new Set<string>();
      for await (const line of lines(input)) {
        if (!line.trim()) continue;
        const point = candidate(JSON.parse(line.replace(/^\u001e/, "")) as unknown);
        if (!point) continue;
        if (seen.has(point.id)) throw new Error(`Duplicate input node ${point.id}.`);
        seen.add(point.id);
        const previous = completed.get(point.id);
        if (previous !== undefined) {
          if (previous !== JSON.stringify([point.kind, point.longitude, point.latitude, point.osmElevation])) {
            throw new Error(`Partial snapshot differs for ${point.id}.`);
          }
          completed.delete(point.id);
          continue;
        }
        if (Math.abs(point.latitude) > maximumLatitude) {
          await persist([{ ...point, elevation: null, status: "outside_coverage", retrievedAt: new Date().toISOString() }]);
          continue;
        }
        pending.push(point);
        if (pending.length >= batchSize) { await persist(await fetchBatch(pending)); pending = []; }
      }
      if (pending.length > 0) await persist(await fetchBatch(pending));
      if (completed.size > 0 || await hashFile(input) !== inputSha256) throw new Error("Input changed during processing.");
      await output.writeFile(`${JSON.stringify({ type: "complete", count, sha256: digest.digest("hex") })}\n`);
      await output.sync();
    } finally { await output.close(); }
    // Under the exclusive lock, a completed path is never reused by this command.
    if (await exists(snapshot)) throw new Error("Destination appeared during processing; refusing to replace it.");
    await rename(partial, snapshot);
    console.log(JSON.stringify({ snapshot, count, sha256: await hashFile(snapshot), inputSha256, endpoint }, null, 2));
  } finally { await lock.close(); await rm(`${snapshot}.lock`); }
};

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});

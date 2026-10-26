import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { createReadStream, createWriteStream } from "node:fs";
import { mkdir, readFile, writeFile, stat } from "node:fs/promises";
import { createInterface } from "node:readline";
import { once } from "node:events";

const root = "/opt/pilot";
const bucket = "s3://outmap-planet-pilot-442073793062-20261025";
const stage = process.argv[2];
if (stage !== "prepare" && stage !== "build") throw new Error("Expected prepare or build");
const started = Date.now();
const run = (command: string, args: string[]) => {
  console.log(new Date().toISOString(), command, args.join(" "));
  execFileSync(command, args, { cwd: root, stdio: "inherit" });
};
const status = async (state: string, extra: Record<string, unknown> = {}) => {
  const value = { stage, state, updatedAt: new Date().toISOString(), elapsedSeconds: (Date.now() - started) / 1000, ...extra };
  await writeFile(`${root}/${stage}-status.json`, JSON.stringify(value, null, 2));
  run("aws", ["s3", "cp", `${root}/${stage}-status.json`, `${bucket}/results/${stage}-status.json`, "--only-show-errors"]);
};
const hash = async (path: string, algorithm: string) => {
  const digest = createHash(algorithm);
  for await (const chunk of createReadStream(path)) digest.update(chunk);
  return digest.digest("hex");
};
const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === "object" && value !== null;

try {
  await mkdir(`${root}/sources`, { recursive: true });
  await status("running");
  if (stage === "prepare") {
    await status("downloading-planet");
    run("aws", ["s3", "cp", "s3://osm-pds/2026/planet-261019.osm.pbf", `${root}/sources/planet.osm.pbf`, "--no-sign-request", "--region", "us-east-1", "--only-show-errors"]);
    await status("verifying-planet");
    if ((await stat(`${root}/sources/planet.osm.pbf`)).size !== 95507771401 ||
      await hash(`${root}/sources/planet.osm.pbf`, "md5") !== "5bbb8c9cae945af88e6af773fed33a3e") throw new Error("Planet checksum mismatch");
    await status("downloading-auxiliary-inputs");
    run("java", ["-Xmx4g", "-jar", `${root}/outmap.jar`, "--only-download", `--osm-path=${root}/sources/planet.osm.pbf`, `--download-dir=${root}/sources`, "--download-threads=4"]);
    await status("extracting-global-peak-nodes");
    run("osmium", ["tags-filter", `${root}/sources/planet.osm.pbf`, "n/natural=peak,saddle,volcano", "-R", "-o", `${root}/peaks.osm.pbf`]);
    run("osmium", ["export", `${root}/peaks.osm.pbf`, "--add-unique-id=type_id", "--geometry-types=point", "-f", "geojsonseq", "-o", `${root}/global-peaks.geojsonseq`]);
    const output = createWriteStream(`${root}/corridor-peaks.geojsonseq`, { flags: "wx" });
    let globalNodes = 0;
    let globalCandidates = 0;
    let corridorNodes = 0;
    let corridorCandidates = 0;
    for await (const raw of createInterface({ input: createReadStream(`${root}/global-peaks.geojsonseq`), crlfDelay: Infinity })) {
      const line = raw.replace(/^\x1e/, "");
      if (!line.trim()) continue;
      const feature: unknown = JSON.parse(line);
      if (!isRecord(feature) || !isRecord(feature.geometry) || !isRecord(feature.properties) || !Array.isArray(feature.geometry.coordinates)) throw new Error("Invalid exported node");
      const [longitude, latitude] = feature.geometry.coordinates;
      if (typeof longitude !== "number" || typeof latitude !== "number") throw new Error("Invalid coordinate");
      globalNodes++;
      const elevation = String(feature.properties.ele ?? "");
      const missing = !(/^-?(?:\d+(?:\.\d*)?|\.\d+)$/.test(elevation.trim()) && Number.isFinite(Number(elevation)) && Math.abs(Number(elevation)) < 10000);
      if (missing) globalCandidates++;
      if (longitude >= -123.85 && longitude <= -122.15 && latitude >= 49.15 && latitude <= 50.75) {
        corridorNodes++;
        if (missing) corridorCandidates++;
        if (!output.write(`${line}\n`)) await once(output, "drain");
      }
    }
    output.end();
    await once(output, "finish");
    const counts = { globalNodes, globalCandidates, corridorNodes, corridorCandidates, globalRequestsAt250: Math.ceil(globalCandidates / 250) };
    await writeFile(`${root}/elevation-counts.json`, JSON.stringify(counts, null, 2));
    await status("backfilling-corridor", counts);
    run("node", [`${root}/backfill-peak-elevations.mts`, `--input=${root}/corridor-peaks.geojsonseq`, `--snapshot=${root}/corridor-elevations.jsonl`]);
    const files = ["corridor-peaks.geojsonseq", "corridor-elevations.jsonl", "elevation-counts.json"];
    for (const file of files) run("aws", ["s3", "cp", `${root}/${file}`, `${bucket}/prepared/${file}`, "--only-show-errors"]);
    run("aws", ["s3", "sync", `${root}/sources`, `${bucket}/prepared/sources`, "--exclude", "planet.osm.pbf", "--only-show-errors"]);
    await status("complete", counts);
  } else {
    const prepared: unknown = JSON.parse(await readFile(`${root}/prepare-status.json`, "utf8"));
    if (!isRecord(prepared) || prepared.state !== "complete") throw new Error("Preparation is not complete");
    await status("staging-local-nvme");
    run("cp", ["-a", `${root}/sources`, "/mnt/pilot/sources"]);
    await mkdir("/mnt/pilot/tmp", { recursive: true });
    await status("generating");
    run("/usr/bin/time", ["-v", "java", "-Xmx48g", "-jar", `${root}/outmap.jar`,
      "--osm-path=/mnt/pilot/sources/planet.osm.pbf", "--download-dir=/mnt/pilot/sources",
      "--output=/mnt/pilot/sea-to-sky-20261025-r2.pmtiles", "--bounds=-123.75,49.25,-122.25,50.65",
      "--minzoom=0", "--maxzoom=14", "--trail-segment-labels=false", "--threads=64", "--storage=mmap", "--tmpdir=/mnt/pilot/tmp",
      `--peak-elevation-snapshot=${root}/corridor-elevations.jsonl`, "--download=false"]);
    await status("saving-output");
    const archive = "/mnt/pilot/sea-to-sky-20261025-r2.pmtiles";
    const sha256 = await hash(archive, "sha256");
    const bytes = (await stat(archive)).size;
    run("aws", ["s3", "cp", archive, `${bucket}/results/sea-to-sky-20261025-r2.pmtiles`, "--only-show-errors"]);
    await status("complete", { sha256, bytes });
  }
} catch (error: unknown) {
  console.error(error);
  await status("failed", { error: String(error) });
  process.exitCode = 1;
} finally {
  try { run("aws", ["s3", "cp", `${root}/${stage === "build" ? "build-r2" : stage}.log`, `${bucket}/results/${stage === "build" ? "build-r2" : stage}.log`, "--only-show-errors"]); }
  finally { run("shutdown", ["-h", "now"]); }
}

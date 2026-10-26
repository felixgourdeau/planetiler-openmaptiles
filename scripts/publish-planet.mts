import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { mkdtemp, open, rm, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { parseArgs, promisify } from "node:util";

const executeFile = promisify(execFile);
const tileOrigin = "https://planet.tiles.outmap.pro";

export type ReleaseOptions = {
  archive: string;
  revision: string;
  destination: string;
  publish: boolean;
};

type RunRclone = (args: string[]) => Promise<string>;

export const validateOptions = (options: ReleaseOptions): void => {
  // A build counter distinguishes rebuilds of the same OSM snapshot.
  if (!/^planet-\d{8}-r[1-9]\d*$/.test(options.revision)) {
    throw new Error(
      "Use a new revision like planet-20260923-r1; increment rN for every rebuild."
    );
  }
  const date = options.revision.slice(7, 15);
  const isoDate = `${date.slice(0, 4)}-${date.slice(4, 6)}-${date.slice(6, 8)}`;
  const parsed = new Date(`${isoDate}T00:00:00Z`);
  if (
    !Number.isFinite(parsed.getTime()) ||
    parsed.toISOString().slice(0, 10) !== isoDate
  ) {
    throw new Error("The revision must contain a valid UTC date.");
  }
  if (!/^[\w-]+:tiles$/.test(options.destination)) {
    throw new Error(
      "Destination must be an existing rclone remote's tiles bucket, such as r2:tiles."
    );
  }
};

export const inspectArchive = async (archive: string) => {
  const before = await stat(archive, { bigint: true });
  if (!before.isFile() || before.size < 127n) {
    throw new Error(
      "Expected a completed PMTiles v3 file (at least 127 bytes)."
    );
  }
  const handle = await open(archive, "r");
  try {
    const header = Buffer.alloc(127);
    const { bytesRead } = await handle.read(header, 0, header.length, 0);
    if (
      bytesRead !== 127 ||
      header.toString("ascii", 0, 7) !== "PMTiles" ||
      header[7] !== 3
    ) {
      throw new Error("Archive does not have a PMTiles v3 header.");
    }
    if (header[99] !== 1 || header.readBigUInt64LE(72) === 0n) {
      throw new Error("Expected a nonempty vector (MVT) archive.");
    }
  } finally {
    await handle.close();
  }
  const hash = createHash("sha256");
  for await (const chunk of createReadStream(archive)) hash.update(chunk);
  const after = await stat(archive, { bigint: true });
  if (
    before.size !== after.size ||
    before.mtimeNs !== after.mtimeNs ||
    before.ino !== after.ino
  ) {
    throw new Error(
      "Archive changed while hashing; finish the build before publishing."
    );
  }
  return {
    bytes: before.size.toString(),
    sha256: hash.digest("hex"),
    mtimeNs: before.mtimeNs,
  };
};

const assertDestinationUnused = async (
  options: ReleaseOptions,
  run: RunRclone
): Promise<void> => {
  // List the two exact keys. A failed/unauthorized listing must never mean "absent".
  const output = await run([
    "lsjson",
    options.destination,
    "--files-only",
    "--max-depth",
    "1",
    "--include",
    `/${options.revision}.pmtiles`,
    "--include",
    `/${options.revision}.release.json`,
    "--contimeout",
    "15s",
    "--timeout",
    "30s",
    "--retries",
    "1",
  ]);
  const entries: unknown = JSON.parse(output);
  if (!Array.isArray(entries))
    throw new Error("Unexpected rclone listing; refusing to publish.");
  if (entries.length > 0) {
    throw new Error(
      `Revision ${options.revision} already exists. Choose a new revision; never overwrite it.`
    );
  }
};

const runRclone: RunRclone = async (args) => {
  const { stdout } = await executeFile("rclone", args, {
    encoding: "utf8",
    maxBuffer: 1024 * 1024,
  });
  return stdout;
};

export const releasePlanet = async (
  options: ReleaseOptions,
  run: RunRclone = runRclone
) => {
  validateOptions(options);
  const archive = resolve(options.archive);
  // Reject a reused revision before reading a potentially 90 GB archive.
  await assertDestinationUnused(options, run);
  const { bytes, sha256, mtimeNs } = await inspectArchive(archive);
  const manifest = {
    revision: options.revision,
    archive: `${options.revision}.pmtiles`,
    bytes,
    sha256,
    tileTemplate: `${tileOrigin}/${options.revision}/{z}/{x}/{y}.mvt`,
    tileJson: `${tileOrigin}/${options.revision}.json`,
  };
  const uploadArgs = [
    "copyto",
    archive,
    `${options.destination}/${manifest.archive}`,
    "--immutable",
    "--checksum",
    "--no-update-modtime",
    "--stats",
    "0",
    "--metadata-set",
    `outmap-dataset-revision=${options.revision}`,
    "--metadata-set",
    `sha256=${sha256}`,
  ];
  if (!options.publish) return { published: false, manifest, uploadArgs };

  // Recheck after the long hash pass. --immutable also protects the transfer.
  await assertDestinationUnused(options, run);
  const beforeUpload = await stat(archive, { bigint: true });
  if (
    beforeUpload.size.toString() !== bytes ||
    beforeUpload.mtimeNs !== mtimeNs
  ) {
    throw new Error("Archive changed after hashing; refusing to publish.");
  }
  await run(uploadArgs);
  const afterUpload = await stat(archive, { bigint: true });
  if (
    afterUpload.size.toString() !== bytes ||
    afterUpload.mtimeNs !== mtimeNs
  ) {
    throw new Error(
      "Archive changed during upload. Do not promote this revision; use a new revision for the retry."
    );
  }
  const directory = await mkdtemp(join(tmpdir(), "outmap-planet-release-"));
  try {
    const manifestPath = join(directory, `${options.revision}.release.json`);
    await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, {
      flag: "wx",
    });
    await run([
      "copyto",
      manifestPath,
      `${options.destination}/${options.revision}.release.json`,
      "--immutable",
      "--checksum",
      "--no-update-modtime",
      "--stats",
      "0",
    ]);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
  return { published: true, manifest, uploadArgs };
};

const main = async (): Promise<void> => {
  const { values } = parseArgs({
    options: {
      archive: { type: "string" },
      revision: { type: "string" },
      destination: { type: "string", default: "r2:tiles" },
      publish: { type: "boolean", default: false },
      help: { type: "boolean", default: false },
    },
  });
  if (values.help) {
    console.log(
      "node scripts/publish-planet.mts --archive /path/planet.pmtiles --revision planet-YYYYMMDD-rN [--destination r2:tiles] [--publish]\nDefault: read-only preflight and SHA-256 calculation. --publish uploads; it never edits app styles."
    );
    return;
  }
  if (!values.archive || !values.revision)
    throw new Error("--archive and --revision are required. Use --help.");
  console.error(
    "Checking the revision and hashing the completed archive; a planet archive may take several minutes."
  );
  const result = await releasePlanet({
    archive: values.archive,
    revision: values.revision,
    destination: values.destination ?? "r2:tiles",
    publish: values.publish ?? false,
  });
  console.log(JSON.stringify(result, null, 2));
  if (result.published)
    console.error(
      "Uploaded. Verify the public TileJSON, decoded sample tiles, and regional visual QA before promoting app styles. See docs/planet-releases.md."
    );
};

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  main().catch((error: unknown) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}

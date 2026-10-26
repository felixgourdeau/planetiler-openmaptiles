# Publishing an immutable planet revision

Every rebuilt archive gets a new `planet-YYYYMMDD-rN` revision, including rebuilds
that use the same OSM snapshot. The date is the build date; increment `rN` for
another build that day. Never replace an existing revision or delete it while
released clients or saved offline packs still reference it.

## Verified hosting path

On 2026-09-23, Cloudflare settings and an R2 listing confirmed:

- `planet-tiles` forwards to the `PMTILES` service binding, `pmtiles-cloudflare`.
- `pmtiles-cloudflare` binds `BUCKET` to the R2 bucket `tiles`.
- `tiles/planet-250825.pmtiles` is the existing versioned archive (89,749,023,592 bytes).
- `tiles/planet.pmtiles` also exists and is a separate legacy archive.
- Current v7 clients request `https://planet.tiles.outmap.pro/planet-250825/{z}/{x}/{y}.mvt`.

Do not upload a new build over either existing object. Verify that the existing
service resolves the new revision's public TileJSON and MVT URLs before promotion.

## Build and preflight

Use the parallel `array` node index with mmap storage for all future production
runs: `--nodemap-type=array --nodemap-storage=mmap`. These are now the source
defaults in `OpenMapTilesMain`; pass the flags explicitly when using an older JAR.
Keep the measured 256-GiB RAM / 48-GiB heap configuration and place the PBF,
temporary indexes, intermediate features and output on local NVMe. The full-planet
first-pass comparison was 14m28s with sparsearray versus 2m32s with array;
[benchmark evidence](sea-to-sky-pilot-2026-10-25/index-benchmark/README.md).
An explicit node-index argument still overrides the default for diagnostics or
small-extract workflows. This setting does not itself provision RAM or storage.

Use Java 21 for Planetiler, Node 22.20+ for the TypeScript release command, and the
already configured `rclone` remote for this account. No npm packages are needed.
The example revision below must be replaced with the actual new build revision.
Normal tile builds compile the checked-in generated Java files. Regenerating
those files from upstream YAML is a separate operation that currently overwrites
Outmap's hand-edited feature filters and schema constants; preserve those changes
before running `scripts/regenerate-openmaptiles.sh`.

For optional peak/saddle elevation gap-filling, follow
[the snapshot workflow](peak-elevation-backfill.md). Preserve valid OSM heights,
retain the completed snapshot and its logged SHA-256 with the release evidence,
and pass `--peak-elevation-snapshot` explicitly. Do not call the elevation service
during feature generation or reuse a partially completed snapshot.

```sh
./mvnw -q -DskipTests package
java -Xmx48g -jar target/planetiler-openmaptiles-3.15.1-SNAPSHOT-with-deps.jar \
  --osm-path=/path/to/planet.osm.pbf \
  --output=/path/to/planet-20260923-r1.pmtiles \
  --storage=mmap --nodemap-type=array --nodemap-storage=mmap --download

node scripts/publish-planet.mts \
  --archive=/path/to/planet-20260923-r1.pmtiles \
  --revision=planet-20260923-r1
```

The release command defaults to read-only preflight. It rejects an occupied
archive or manifest key, validates the PMTiles v3/vector header, streams SHA-256
without loading the archive into memory, and prints the exact upload arguments
and tile URL. Hashing a full planet requires a complete read of the archive.
Header checks do not replace regional geometry, layer, and visual validation.

## Upload and verify

After reviewing preflight and regional test results, run the same command with
`--publish`. It rechecks the destination after hashing, uploads with
`rclone copyto --immutable --checksum`, then uploads a checksum/revision manifest
at `planet-YYYYMMDD-rN.release.json`. It never edits client files or deletes data.
Use only one publisher for a revision: the preflight and rclone guard are not an
R2 bucket lock and cannot stop another independently authorized uploader.

A failure is not permission to overwrite. Inspect an interrupted upload before
retrying; if either final object exists, choose another revision. Do not promote
a revision whose upload failed or whose archive changed during upload.

Before updating clients, check:

1. The new `/<revision>.json` TileJSON returns 200 and its tile templates point at
   that exact revision, with the expected bounds and zoom range.
2. Representative MVTs decode successfully, including Mt Coramba at zooms 11–14.
3. The expected layers and attributes exist, and regional tile size and rendering
   are acceptable. A successful upload alone does not establish map quality.

## Client promotion and rollback

Update the actual source URL only after the new archive passes validation:

- Web: `outmap-landing-page/public/style/outmap-v7.json` and `outmap-v7-light.json`.
- Mobile: `outmap2/src/modules/page-map/styles/outmap-v7.json`.
- Audit other active consumers before rollout, including the legacy source in
  `outmap2/src/modules/page-map/styles/styles.selector.ts`, older web styles, and
  the separate native OG renderer. Do not blindly replace historical fixtures.

Keep the prior archives online. Rollback restores the previous source URL.
The existing mobile offline descriptor accepts `planet-[\w.-]+` and derives its
fixed revision from that path, so this naming scheme needs no schema change.

For custom mobile packs, Refresh must compare the saved OpenMapTiles source with
the installed app's source and stage a replacement under the same pack ID when
they differ. Preserve coverage, zoom limits, other sources, and user settings.
Both native stores retain the active generation while staging and only promote
a fully validated replacement. Resume and Repair keep the saved request.

The old native `refreshPack(packId)` and `checkForUpdates(packId)` methods alone
read the saved request, so they cannot discover a newly bundled fixed revision.
The TypeScript service must perform this comparison before invoking them.

Saved offline imagery may continue using older cached tiles. Publishing a new
revision does not require automatic cache deletion or an automatic download.
Explicit Refresh remains the opt-in path for downloading the installed basemap
revision while retaining the active generation until the replacement is ready.

Physical iOS and Android acceptance should cover: an old saved pack, an app with
the new style URL, update detection without a download, explicit Refresh,
interrupted/failed replacement, successful promotion, and airplane-mode rendering.
Protected-resource lookups currently use the full tile URL, with no fallback
between different `planet-*` paths. The policy allows older cached imagery;
it does not mean an old tile can already satisfy a request for a different URL.

References: [rclone immutable behavior](https://rclone.org/docs/#immutable) and
[single-file copy](https://rclone.org/commands/rclone_copyto/).

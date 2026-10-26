# Production archive replacement — October 26, 2026

User explicitly requested replacing `tiles/planet-250825.pmtiles` and retaining the old archive under `backups/`. The user reiterated that backup must precede replacement.

## Current state

- Production remains the old 89,749,023,592-byte object.
- Backup completed: `tiles/backups/planet-250825-before-20261026.pmtiles`. Exact size and three 1-MiB byte ranges match the original. See `backup-verification.json`. This is not an independent full-file hash.
- Direct Cloudflare Super Slurper migration is running, job `53daf2e70f7d45d1b2504c5f462463714bde36725712a66481bf33ea9d0821d4`.
- Source selection is the exact full object prefix `planet-20261025-r1/artifacts/planet-20261025-r1.pmtiles` in AWS bucket `outmap-planet-pilot-442073793062-20261025`; destination is R2 `tiles`; overwrite is false. There is exactly one matching source object.
- No EC2 instance was launched. The initial local relay was interrupted before completion and its multipart upload was removed by rclone. The completed server-side backup was not interrupted.

## Authorization and credentials

User approved direct migration using a temporary scoped AWS credential, then separately approved bucket-wide names/metadata listing needed by Cloudflare. Automatic approval review initially rejected the wider listing; the explicit additional approval resolved that rejection. Object downloads remain limited to the one new archive. Policy has a six-hour expiration, and the IAM user/key must be deleted after migration. Username: `outmap-r2-migration-20261026`; inline policy: `ReadOnePlanetArchive`. Credentials are temporary files outside the repository and must not be committed.

The Cloudflare connector could not create migration jobs (403); the existing local Wrangler OAuth authorization could. Source precheck succeeded only when its listing was narrowed to the exact archive prefix; specifying only `keys` did not constrain the precheck. Job creation rejects combining keys and prefix, so the job uses exact prefix only. R2 destination credentials were already configured locally.

## Remaining steps

1. Verify import status, exact size, SHA metadata and sampled ranges against S3.
2. Confirm the backup receipt before overwriting `planet-250825.pmtiles` with an R2 server-side copy.
3. Verify the new production object and served MVT samples; purge only the affected production tile path and TileJSON caches.
4. Remove temporary migration user/key and leftover transfer staging after preserving publication evidence. Keep the requested backup and the source release.

Existing browser/device caches may retain old tiles until their one-day lifetime expires. Offline downloads are not automatically replaced by changing an R2 key. No style or Worker code change is authorized or needed for this object replacement.

# Estimate recorded before the r3 full-PBF generation

The decoded r2 tile simulation estimates **63.46 MB → 56.79 MB** of addressed gzip tile payload (**10.5% reduction**) from conservative low-zoom pruning plus kerb removal. The largest tile falls from ~794 KB to ~275 KB (65%). The estimate preserves all other z14 content, and does not move POI minzooms.

This is not a measured r3 archive size. PMTiles deduplication, actual source-side outdoor-priority decisions and newly assigned ranks after explicit exclusions affect the final result. Sidewalk savings are not included because the previous archive does not preserve a reliable sidewalk subtype on every affected feature. New trail-length label bytes are also not included. Those effects will be measured in the completed r3 archive.

No recommendation to show POIs earlier is included in this change. Ranks 1–12 retain headroom; all currently visible low-zoom generic POIs, transit exceptions and original outdoor-priority exceptions are retained. The current style JSON files are unchanged. Kerbs and explicitly mapped sidewalks are the user-approved exceptions to z14 preservation; ordinary roads with sidewalk=yes/both/separate remain.

The r3 build uses the full `planet-261019.osm.pbf`. A buffered complete-way extract is used only to prepare regionally valid length labels; the generator's PBF input is not replaced. Chains extending into uncertain outer topology are omitted instead of shortened. Full-world preparation has not been benchmarked by this pilot.

Current Spot quotes used for planning: m7g.xlarge $0.0851/hour for preparation; m7gd.16xlarge $1.2554/hour for generation, both us-east-1a. Target approximately $1–2 for this pilot's compute/staging, excluding retries or interruption. Actual runtime remains to be measured. Both instances are configured to terminate on shutdown with delete-on-termination root disks and hard time limits.

The first m7g.xlarge Spot preparation was interrupted by AWS during checksum verification. The retry uses m7g.4xlarge in us-east-1d at a quoted $0.2836/hour; the refreshed m7gd.16xlarge build quote in that zone is $1.539/hour. These are planning rates, not a billing receipt. Periodic log uploads were added before the retry.

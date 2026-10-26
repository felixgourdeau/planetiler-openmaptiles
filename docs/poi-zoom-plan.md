# POI tile zoom plan — 2026-10-25

Density reduction remains a proposal. Outdoor priorities and supplemental admission have since been implemented locally; see [the measured audit](poi-audit-2026-10-25/README.md). Client styles have not changed.

## Reviewed consumers

- Mobile: `outmap2/src/modules/page-map/styles/outmap-v7.json`; `styles.selector.ts`
  returns this style even for persisted legacy version selections.
- Web: `outmap-landing-page/public/style/outmap-v7.json`, light and ski-light,
  both topo-no-dem variants, and `outmap-og-topo-v1.json`.
- Also inspected web v6 and third-party styles for compatibility. The Gaia style
  uses a different POI schema and is not a consumer of this layer contract.
- Reviewed generic POI layout/paint and the web selection rules. Visibility,
  pitch/distance filtering and symbol collisions can hide additional features;
  they cannot justify removing a feature eligible in another supported mode.

These are local source observations, not a survey of every installed app version.

## Exact current rules

| POI selection | Web/mobile light | Web/mobile dark | Other consumers |
| --- | --- | --- | --- |
| All classes, rank 1–6, excluding subclass bus_stop | z12 | z12, with extra pitch filtering | Same in inspected web variants/v6 |
| All classes, rank 7–19 | z16 | z16 | Same |
| All classes, rank >=20 | z17 | z17 | Same |
| class=bus AND subclass=bus_stop icon, regardless of rank | z14 | z12 | z14 in single-theme variants |
| class=bus AND subclass=bus_stop label | z16 | z16 | Legacy web v6 z15 |
| class=airport OR class=rail, regardless of rank | Layer z0; actual start is source availability | Same | Same |

All ordinary POI categories use these rank rules, including outdoor amenities,
shops, hospitals, attractions, campsites, waterfalls and stones. There is no
style-supported justification for setting every shop to z14: an isolated shop
can currently rank 1 and display at z12. `rail` must not be confused with another
class such as `railway`; use the exact emitted class and existing style filter.

Generator exceptions: camp_pitch starts at z14; university/college has a z10
start plus an 80-pixel size requirement below z13. Stones and waterfalls now use the common ranking/grid setup. Ordinary
features start at z12. Rank is assigned per label-grid group at each tile zoom,
not permanently to an OSM feature.

Both v7 vector sources declare maxzoom=14. Display zooms 15–22 overzoom z14 data.
Never assign a tile minzoom of 15, 16 or 17 in this archive: those POIs would be
missing from current clients even if a deeper archive were generated.

## Recommended first implementation

1. Use the updated outdoor-first candidate generation and sort order; preserve grid size and rank
   assignment. Assign ranks to the complete candidate set BEFORE pruning.
2. At z12 and z13 only, retain ranks 1–12 (twice the currently visible six), plus
   EVERY ultra-priority outdoor feature regardless of rank, EVERY class=bus/subclass=bus_stop and EVERY class=airport|rail feature.
   Retain any otherwise exceptional/unranked feature conservatively.
3. Preserve z0–11 output, including the existing university/college exception.
4. At z14 and above, retain ALL currently emitted POIs and their original ranks.
   Do not cap the maximum zoom, and bypass pruning at config.maxzoom() if it is
   below 14. Builds intended for current clients should still include z14.
5. Do not renumber survivors: doing so could turn a previously hidden POI into a
   rank-1 POI and alter current rendering.

| Ordinary POIs follow these cases; ultra-priority types are exempt from pruning | Proposed tile availability | Earlier-display headroom |
| --- | --- | --- |
| Generic rank 1–6 | Existing z12; optional second phase z11 | z11 would provide one zoom of headroom; not part of first implementation |
| Generic rank 7–12 | Keep at z12–13 too | Extra density reserve without changing current styles |
| Generic rank 13–19 | Full detail at z14; omit only at z12–13 | Current display z16; available two zooms sooner |
| Generic rank >=20 | Full detail at z14; omit only at z12–13 | Current display z17; available three zooms sooner |
| Every bus stop matched by dedicated bus style | Keep existing z12 | Cannot delay to z13: dark mode already displays at z12 |
| Every airport/rail matched by dedicated transit style | Keep existing availability at every zoom | Layer permits z0; cannot safely prune based on generic rank |
| University/college below z12 | Preserve existing z10 and size rule | Existing behavior preserved |
| Camp pitch | Preserve existing z14 | Available at full detail; earlier generation would change current visible behavior for low ranks |

The proposal intentionally saves z12–13 bytes without changing current styling.
A second phase can make generic top-ranked POIs and bus stops available at z11
for one-zoom-earlier styles. That adds low-zoom data and needs its own size and
visual comparison. A global category-specific redesign is unnecessary for the
first improvement and would risk current-client regressions.

## Acceptance before publication

Generate baseline/candidate archives from identical buffered regional inputs:
Paris/Tokyo (dense POIs), Montreal (bus stops), Adirondacks and Whistler (sparse
outdoor amenities), a large university and a campsite with individual pitches.

- Decode z10–14 tiles; compare feature identity, attributes and original ranks.
- Confirm every feature eligible under any current style remains at z12–13.
- Confirm complete z14 POI identity/attribute/rank parity, including tile buffers.
- Check seams, light/dark, pitch, localization, selection and z14–18 overzooming
  on web and physical iOS/Android, including airplane-mode reload.
- Compare compressed tile bytes and feature counts by zoom/layer (median, p95,
  p99 and maximum), archive bytes, generation time and memory.
- Do not predict a fixed percentage saving from historical worst-case logs.

No unit tests or repository typechecks. Regional archive comparisons and manual
rendering acceptance remain unperformed. Publish only under a new immutable
revision after acceptance; preserve old revisions for installed/offline clients.

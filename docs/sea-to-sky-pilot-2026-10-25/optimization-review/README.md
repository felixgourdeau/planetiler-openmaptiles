# Speed, size and cost review — 2026-10-25

User decisions: preserve current trail eligibility (paths, footways, steps and tracks, subject to existing exclusions); preserve current style JSON compatibility; postpone styling new layers. No generator, style or infrastructure changes were made in this review. Local archive experiments do not publish modified tiles.

## Measured bulk of large tiles

Inspected all 30 pilot tiles larger than 200 kB compressed. Decomposed original protobuf layers and simulated changes by decoding/re-encoding actual features with the existing JAR. Re-encoding slightly changes dictionary ordering/compression; comparisons use a re-encoded baseline. Independent layer-removal savings are not additive.

Worst tile `12/647/1402`:

- Original compressed: 794,278 bytes; uncompressed: 2,058,711 bytes.
- POI layer: 1,740,275 raw bytes, **84.5%** of tile bytes, **39,284 feature occurrences**.
- Transportation: 281,215 raw bytes, **13.7%**, 3,614 feature occurrences.
- POI class counts include **18,784 kerbs**, 2,476 information features, 2,167 bicycle parking, 1,729 parking, 1,360 bus, 1,264 shops and 1,086 restaurants. Counts include tile buffers and are not regional unique-object totals.
- Removing contact/hours metadata changes nothing at z12: those fields already start at z14.
- Removing `polygon_label` saves only ~373 compressed bytes; `outdoor` is absent at z12. These new layers are not the cause of this tile's size.

At `10/162/350`, transportation is 696,672 of ~800 kB raw bytes. Deferring supplemental transportation attributes to z14, while retaining a conservative set of legacy and access/difficulty fields, reduced the re-encoded tile from **426,011 to 347,749 bytes (18.4%)**, without simplifying geometry. This simulation did not rerun line merging; any further merging benefit is unmeasured.

At the largest z14 tile, `14/2588/5607`, POI is 538,655 raw bytes, transportation 103,312, buildings 73,372 and house numbers 32,503. Removing contact/hours fields would shrink compressed bytes from 389,735 to 334,036 (~14.3%), but this is diagnostic only: the recommendation is to preserve z14 detail. Removing the new outdoor or polygon-label layer saves only a few kB there.

## Priority 1: reduce low-zoom POI payload while preserving the style

The current rank assignment retains every feature at z12/13, including many ranks only displayed at z16/17. Normal z16/17 rendering uses the z14 source tiles. Filter after ranking, retain original ranks and IDs, keep all z14 data and attributes, and do not change the style JSON.

An aggressive simulation kept ranks 1–12, unranked records, airport/rail and dedicated bus-stop exceptions. It cut the worst tile to 221,457 bytes. A more conservative simulation also retained a broad outdoor category list, campsites, sports/pitches, shelters, water, harbors and **all parking**. Results:

| Scope | Re-encoded baseline gzip | Conservative simulation | Saving |
|---|---:|---:|---:|
| Worst tile | 794,322 | 274,658 | **65.4%** |
| All 468 z12 tiles | 10,469,926 | 6,993,402 | **33.2%** |
| All 1,750 z13 tiles | 15,680,907 | 13,240,008 | **15.6%** |
| z14 | unchanged | unchanged | 0% |

The aggregate reduction is 5.92 MB of addressed tile payload. It is not an exact repacked-archive prediction because PMTiles can deduplicate tile contents.

This is a measured candidate, not a shipped filter or a formal visual-equivalence proof. The actual implementation should retain the original source-side ultra-priority decision, including sport-tagged features and trailhead parking, rather than attempt to reconstruct it from the decoded class/subclass. Low-zoom tags omit some original evidence. The simulation uses broad categories, so exact implementation savings may differ. Validate all supported style variants, pitch/LOD, collisions and selection. Preserve all transit exceptions. Do not prune before rank assignment or renumber survivors.

## Priority 2: defer supplemental transportation detail

Keep all fields used by current rendering/interaction at their current zooms; move unneeded raw/normalized duplicates, provenance and descriptive fields to z14. Full z14 detail remains available for overzoom and future styling. The measured transportation experiment keeps class/subclass/network/brunnel/layer/oneway/ramp/service/surface/access/bicycle/foot/horse/mtb_scale/toll/expressway, hiking difficulty, trail visibility, lifecycle, conditional access and piste classification fields. Its whitelist is a diagnostic proposal, not an audited final API contract.

Many distinct attributes also prevent line merging, and directional metadata deliberately blocks unsafe merges. Deferring metadata can improve merging, but must not merge directional information incorrectly or change visible geometry. Audit runtime style modifications and feature-query consumers before deciding the final field list. Do not remove road/trail geometry or simplify it indiscriminately.

## Make trail-length processing dramatically faster

Keep the existing eligibility rules. The algorithm and dispatch are the problem, not the inclusion of footways/tracks.

1. Reject irrelevant OSM types before acquiring any lock. The current synchronized method serializes even billions of nodes. The prior run measured ~3.1M nodes/sec with the handler and ~35M/sec with it disabled; this demonstrates the avoidable dispatch bottleneck, not a measured 11× speedup for the completed length algorithm.
2. Eliminate the shared per-feature graph lock. Use worker-local compact edge records, then merge/deduplicate by endpoint IDs. Avoid a Java `Vertex`, `ArrayList` and `Edge` object graph for every intermediate node.
3. Use primitive arrays / compact disk-backed data to compute node degree and road-intersection boundary flags. Retain existing eligibility and invalid-geometry behavior. Ordinary roads only mark junction boundaries; they never receive lengths.
4. Walk each graph edge once, collapsing degree-two chains into junction-to-junction segments. Compute length and midpoint before tile clipping/simplification; preserve loops, duplicated-edge handling and minimum length semantics.
5. Persist the resulting segment-label snapshot with the planet hash and policy/version hash. Reuse it for style/schema reruns against the same input. The final full-PBF tile build reads prepared point labels, not a graph. Do not silently reuse it for a different OSM snapshot.
6. For a global graph that does not fit in memory, spill/sort compact records or partition with explicit cross-partition reconciliation. Do not cut chains at arbitrary region/tile boundaries and report their truncated lengths.

Moving the existing slow code into a preparation stage alone would not make the total pipeline faster. The parallel compact algorithm is the speed improvement; a frozen snapshot removes repeated work from subsequent builds. Use the existing Java/JAR libraries where possible rather than adding dependencies. Preserve the full planet input for the tile generator.

Acceptance target: no shared-lock cost for irrelevant features, no fixed regional-size graph cap, identical distances/anchors on representative complete segments, safe incomplete-geometry handling, and negligible graph-related overhead in a rerun using a snapshot. No global graph speedup factor or first-run duration can be honestly guaranteed until measured. The first OSM pass already took 17m13s with the layer disabled and was mostly one worker; trail changes cannot eliminate that unrelated cost.

Implementation options:

- **Recommended:** compact parallel preprocessing plus immutable snapshot; more engineering and a new internal data contract, best reuse and world-scale path.
- **Smaller patch:** move filters outside synchronization and optimize the in-memory representation; useful immediate improvement, but does not by itself establish global memory safety or reuse.

Affected files: `TrailSegmentLabels.java`, extra-layer registration/snapshot reader, preparation runner; separate size work in `Poi.java`, `Transportation.java` and attribute helpers. Approval is required under AGENTS.md before making these data-model/pipeline and feature-emission changes. This review implements neither option.

## Lower cost without pretending runtime is measured

Read-only AWS Spot lookup on 2026-10-25 found latest quoted m7gd.16xlarge prices of **$1.2554/hour in us-east-1a**, $1.511–1.543 in several other AZs, and $2.5376 in us-east-1f. Its previously verified On-Demand rate is $3.4171/hour. The cheapest observed price is ~63% lower. c7gd.16xlarge Spot was $2.8756/hour in observed AZs, barely below its $2.903 On-Demand rate; switching that exact instance to Spot would barely help.

Assuming capacity is available in a $1.26–1.54 pool, **2–6 hours of m7gd compute is $2.51–9.26**. With modest cheap preparation and transient storage, **$4–12** is a reasonable conditional run budget for the existing profile, excluding first-time global segment preprocessing, interruption/retry costs, endpoint charges and ongoing retention/serving. Price is not a capacity guarantee. Spot interruption can lose NVMe work and force a restart; saving inputs does not make an interrupted Planetiler build resumable. AWS documentation: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/spot-interruptions.html.

The world build is still unmeasured. **2–4 hours on large compute is an optimization target**, not a replacement for the existing uncertain 2–6-hour planning range. Cached preparation removes 1–3 hours from the waiting path of later runs; it does not erase the first preparation cost. More RAM can address the observed page-cache pressure; lower tile output can reduce encoding/sorting work. Both need a fresh benchmark. No expensive instance was launched for this analysis.

# Outdoor route and obstacle attributes

The original hiking extension is documented below. The October 25 expansion
adds the normalized contract in the next section and supersedes the earlier
statement that other mode-specific and directional fields are not retained.

The existing `transportation` highway-line output now includes the following
optional string attributes from z9 onward, whenever the feature itself exists.
No layer, existing field, feature eligibility, access decision, or zoom range is
renamed or removed. The checked-in generated mappings/schema remain untouched.

| Attribute | Values |
| --- | --- |
| `sac_scale` | `strolling`, `hiking`, `mountain_hiking`, `demanding_mountain_hiking`, `alpine_hiking`, `demanding_alpine_hiking`, `difficult_alpine_hiking` |
| `trail_visibility` | `excellent`, `good`, `intermediate`, `bad`, `horrible`, `no` |
| `access:conditional` | Original nonblank OSM expression |
| `foot:conditional` | Original nonblank OSM expression |
| `bicycle:conditional` | Original nonblank OSM expression |
| `horse:conditional` | Original nonblank OSM expression |
| `vehicle:conditional` | Original nonblank OSM expression |
| `motor_vehicle:conditional` | Original nonblank OSM expression |

Difficulty/visibility values have surrounding whitespace removed and use
locale-independent lowercase before canonical-value validation. Missing, invalid,
numeric, regional and ambiguous multi-value ratings are omitted, never interpreted
as easy, visible or unrestricted. No country-dependent conversion is applied.
This normalizes the representation worldwide, not the completeness or accuracy
of worldwide mapping. Difficulty and visibility remain separate properties.

Conditional expressions retain their exact text, including punctuation and case;
blank values are omitted. They are not parsed or evaluated and do not override
existing access/foot/bicycle/horse output. Absence does not mean unrestricted
access. Other mode-specific, directional and relation-based conditions are not
added by this change. Consumers must not treat these six fields as an exhaustive
legal-access model or a current closure feed.

Current clients can ignore these additive fields. Attributes may prevent merging
adjacent lines with different values, increasing feature counts/tile sizes; that
is necessary to avoid conflating their information. Existing line geometry,
IDs and tile byte layout are therefore not promised to be identical.

## Peak compatibility

Named non-saddle peaks with missing or invalid elevation now retain their name,
class and existing label/rank machinery. Unknown elevation fields are omitted,
not set to zero. Existing valid elevations and saddle behavior are preserved.
Unnamed peaks without usable elevation remain excluded. New named candidates
can affect local peak ranking/collisions. Current peak styles already have no
elevation-presence filter; their formatted label reads optional `ele`, so inspect
the name-only result and spacing on both native renderers before promotion.

## Verification

Source/diff review only; no unit tests, typechecks, compilation, regional archive
generation or rendering acceptance performed. Before publication, decode known
examples for each canonical value, blank/unknown values, independent mode
conditions, mixed-case conditions, adjacent ways with different attributes,
and named peaks with absent/invalid elevation. Check current web/mobile styles,
tile sizes and offline overzooming. Use a new immutable archive revision.

Sources inspected for the canonical vocabulary and conditional syntax:

- [OSM hiking difficulty](https://wiki.openstreetmap.org/wiki/Key:sac_scale)
- [OSM trail visibility](https://wiki.openstreetmap.org/wiki/Key:trail_visibility)
- [OSM conditional restrictions](https://wiki.openstreetmap.org/wiki/Conditional_restrictions)


## Normalized route and obstacle extension — 2026-10-25

Implemented in `OutdoorAttributes.java`, called by `Transportation` (z9+, only
where the feature is emitted) and outdoor obstacle `Poi` output (its own minzoom,
usually z12). This is generator source, not evidence of a published archive or
client integration. Existing generated tables and schema constants are preserved.

### Field contract

- Existing fields retain their prior behavior, including binary `surface`, access,
  and numeric `oneway`. New consumers should use the normalized fields below.
- Categorical input is trimmed with locale-independent lowercase. A known value
  produces `<key>_normalized`; the original nonblank value is `<key>_raw`.
- Strict vocabularies: access modes, surface, tracktype, smoothness, SAC hiking
  difficulty, trail visibility, MTB difficulty/uphill/IMBA, locked, informal, lit,
  fee, 4WD suitability, and seasonality. Unknown or compound values retain raw
  evidence without a canonical value. No numeric SAC conversion is attempted.
- Open vocabularies (highway, service, usage, hazard, barrier, maintenance,
  crossings, assistance equipment, winter/piste information, etc.) use the same
  suffixes but only lexical normalization. They are not validated safety ratings.
  For example `maintenance_normalized` is source terminology, not a globally
  standardized maintenance class. The helper's explicit lists define coverage.
- Empty values are omitted. Absence never means easy, safe, maintained, open,
  unlocked, unrestricted, or accessible. `4wd_only` is independent of legal access.
- Names, operators, owners, refs, descriptions, opening hours, websites and
  source/survey/check dates are retained under `outdoor:<key>` without changing
  their spelling or evaluating their contents. These dates are source claims,
  not dates verified by Outmap.
- Conditional expressions retain their exact text under their OSM key. No
  timezone, holiday, vehicle-class or current-open/closed evaluation occurs.

### Coverage

| Group | Retained information |
| --- | --- |
| Road identity | Highway, service, usage, name, ref, operator, owner; no inference that all tracks are forestry roads |
| Vehicle/activity access | General, foot, bicycle, horse, vehicle, motor vehicle, motorcar, motorcycle, ATV, OHV, snowmobile, ski, wheelchair, HGV, agricultural, forestry, dog and ski submodes |
| Access direction/time | Each mode's forward/backward values and conditional expressions, including directional conditions |
| Condition | Actual surface, track grade, smoothness, maintenance descriptors, winter service, informal status, visibility |
| Difficulty | SAC, MTB scale/uphill/IMBA, via-ferrata descriptor, piste difficulty |
| Crossings/hazards | Hazard and animal/weather subtype, ford, bridge, tunnel, depth, flood/avalanche-prone flags, tidal/intermittent information |
| Accessibility | Wheelchair access, width, incline, steps, handrails, ramps, ladders and safety ropes |
| Restrictions | Width, height, length, vehicle weight, axle load, speed, fee, seasonal information, opening hours |
| Direction | Explicit general and mode-specific one-way values and conditions |
| Barriers | Gate variants, chains, ropes, blocks, bollards, cycle/motorcycle barriers, stiles, cattle grids, logs, debris, kerbs, height restrictors and turnstiles |
| Provenance | Source, source dates, survey/check dates and access/barrier checks where supplied |

One-way normalization maps `yes/true/1` to `1`, `no/false/0` to `0`, and
`-1/reverse` to `-1`. Other values such as reversible remain raw. Roundabout and
jurisdictional defaults are not inferred; `junction_normalized` remains available.
Directional highway features are isolated during merging, as the existing
one-way output already was. This includes incline and left/right/forward/backward
fields so their orientation is not conflated with another segment.

### Measurements

| Output | Supported input |
| --- | --- |
| `<dimension>_m` | Bare metres; `m`, `cm`, `mm`, `ft`, `feet`, `in`; feet/inches such as `5'6"`; inches such as `50"` |
| `maxweight_kg`, `maxaxleload_kg` | Bare metric tonnes; `t`, `kg`, `lb`, `lbs`, `st` (short tons), `lt` (long tons) |
| `maxspeed_kmh` | Bare km/h; `km/h`, `kmh`, `kph`, `mph` |
| `incline_percent` | Signed numeric percentage, with or without `%` |
| `incline_direction` | Explicit `up` or `down`, without manufacturing a grade |
| `step_count_normalized` | Nonnegative integer of up to nine digits |

Dimensions cover width, estimated width, legal/physical maximum width and height,
maximum length, and depth. Originals remain under `_raw`; conditional limits are
retained separately. Ranges, multiple values, degree inclines, special values such
as `none`, unknown units, non-finite conversions and ambiguous uppercase weight
units remain raw. Conversion does not establish clearance or passability.

### Lifecycle and route relations

The supplemental handler admits recognized highway geometry with destroyed,
abandoned, disused, demolished, removed, razed, proposed and construction prefixes,
without regenerating tables or mutating source tags. `lifecycle` identifies the
selected prefix; each supplied `<lifecycle>:highway` is retained separately.
When multiple prefixes exist, the documented helper list supplies deterministic
precedence; no active-highway tag is replaced. Consumers must explicitly style
or hide these inactive/planned ways before publishing the archive. Old clients
that ignore lifecycle can otherwise depict them as ordinary routes.

Road/hiking relation metadata now also supports foot/walking, bicycle, MTB, horse,
ski, snowmobile, motorcycle, motorcar, ATV and offroad routes. Named or referenced
relations without a network are retained. `transportation_name` adds
`route_N_type` alongside its existing network/ref/name/colour fields. This is
route membership metadata, not a turn-restriction or routable network model.
Lifecycle-only ways retain their original names/refs in `outdoor:*` metadata;
the existing dedicated name-layer eligibility is unchanged.

### Obstacle POIs

Existing mapped gates and barriers are enriched in place. Supplemental matching
adds missing obstacle types and explicit hazards, fords, elevators, passing places
and turning circles without duplicating existing mapped POIs. Barriers/hazards
receive priority after the existing essential outdoor POIs and before ordinary
POIs. Current class/subclass values are preserved for existing mapped features.

`outdoor_obstacle` identifies the source key, while `barrier_normalized` or
`hazard_normalized` carries its type. `outdoor_geometry` distinguishes point,
line and area source geometry. Lines use a representative midpoint and polygons
a point on surface: these POIs are markers, not complete hazard extents or routing
barriers. Gates retain lock state, access by mode, conditions, dimensions, opening
hours and provenance when supplied. Locked does not automatically mean no access
for every activity. Multi-tag existing POIs keep their existing classification
and also receive the obstacle fields.

### Verification and release limits

Source review and `git diff --check` only. No unit tests, typechecks, compilation,
regional archive generation, tile decoding, size benchmark, or web/mobile render
acceptance were run. This repository explicitly prohibits unit tests and
repository typechecks. No dependencies were added; no archive was published.

Before release, inspect representative regional tiles for recognized/unknown and
mixed-case values, metric/imperial units, conditional syntax, reverse ways,
adjacent direction-dependent segments, unnamed barriers, multi-tag POIs and
lifecycle-only roads. Check label collisions and lifecycle styling in web/mobile,
including offline overzooming. Measure tile/feature growth: extra metadata and
less merging can increase both. Live closures, agency maintenance ratings,
DEM-derived slope and comprehensive legal access need their owning datasets or
services; OSM retention alone cannot fill missing source information.

Normalization references inspected:

- [OSM units](https://wiki.openstreetmap.org/wiki/Map_features/Units)
- [OSM weight limits](https://wiki.openstreetmap.org/wiki/Key:maxweight)
- [OSM incline](https://wiki.openstreetmap.org/wiki/Key:incline)
- [OSM lifecycle prefixes](https://wiki.openstreetmap.org/wiki/Lifecycle_prefix)
- [OSM ATV access](https://wiki.openstreetmap.org/wiki/Key:atv)
- [OSM 4WD suitability](https://wiki.openstreetmap.org/wiki/Key:4wd_only)
- [OSM locked barriers](https://wiki.openstreetmap.org/wiki/Key:locked)

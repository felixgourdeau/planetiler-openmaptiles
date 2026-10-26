/*
Copyright (c) 2024, MapTiler.com & OpenMapTiles contributors.
All rights reserved.

Code license: BSD 3-Clause License

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

Design license: CC-BY 4.0

See https://github.com/openmaptiles/openmaptiles/blob/master/LICENSE.md for details on usage
*/
package org.openmaptiles.layers;

import static java.util.Map.entry;
import static org.openmaptiles.util.Utils.coalesce;
import static org.openmaptiles.util.Utils.nullIfEmpty;
import static org.openmaptiles.util.Utils.nullIfLong;
import static org.openmaptiles.util.Utils.nullOrEmpty;

import com.carrotsearch.hppc.LongIntMap;
import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.collection.Hppc;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.expression.MultiExpression;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.Translations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.locationtech.jts.geom.Point;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.openmaptiles.generated.Tables;
import org.openmaptiles.util.OmtLanguageUtils;
import org.openmaptiles.util.OutdoorAttributes;
import org.openmaptiles.util.CoverageAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the logic for generating map elements for things like shops, parks, and schools in the {@code poi} layer from
 * source features.
 * <p>
 * This class is ported to Java from
 * <a href="https://github.com/openmaptiles/openmaptiles/tree/master/layers/poi">OpenMapTiles poi sql files</a>.
 */
public class Poi implements
  OpenMapTilesSchema.Poi,
  Tables.OsmPoiPoint.Handler,
  Tables.OsmPoiPolygon.Handler,
  OpenMapTilesProfile.OsmAllProcessor,
  ForwardingProfile.LayerPostProcessor,
  ForwardingProfile.FinishHandler {

  /*
   * process() creates the raw POI feature from OSM elements and postProcess()
   * assigns the feature rank from order in the tile at render-time.
   */

  private static final Logger LOGGER = LoggerFactory.getLogger(Poi.class);
  private static final Map<String, Integer> CLASS_RANKS = Map.ofEntries(
    entry(FieldValues.CLASS_HOSPITAL, 20),
    entry(FieldValues.CLASS_RAILWAY, 40),
    entry(FieldValues.CLASS_BUS, 50),
    entry(FieldValues.CLASS_ATTRACTION, 70),
    entry(FieldValues.CLASS_HARBOR, 75),
    entry(FieldValues.CLASS_COLLEGE, 80),
    entry(FieldValues.CLASS_SCHOOL, 85),
    entry(FieldValues.CLASS_STADIUM, 90),
    entry("zoo", 95),
    entry(FieldValues.CLASS_TOWN_HALL, 100),
    entry(FieldValues.CLASS_CAMPSITE, 110),
    entry(FieldValues.CLASS_CEMETERY, 115),
    entry(FieldValues.CLASS_PARK, 120),
    entry(FieldValues.CLASS_LIBRARY, 130),
    entry("police", 135),
    entry(FieldValues.CLASS_POST, 140),
    entry(FieldValues.CLASS_GOLF, 150),
    entry(FieldValues.CLASS_SHOP, 400),
    entry(FieldValues.CLASS_GROCERY, 500),
    entry(FieldValues.CLASS_FAST_FOOD, 600),
    entry(FieldValues.CLASS_CLOTHING_STORE, 700),
    entry(FieldValues.CLASS_BAR, 800)
  );
  // Keep current client classes; priority is based on the precise OSM subtype.
  private static final Set<String> ULTRA_PRIORITY_SUBCLASSES = Set.of(
    "viewpoint", "attraction", "camp_site", "caravan_site", "camp_pitch", "outdoor", "sports",
    "alpine_hut", "wilderness_hut", "chalet", "hostel", "shelter", "drinking_water", "water_point", "toilets",
    "waterfall", "stone", "trailhead", "cave_entrance", "bicycle_repair_station",
    "sports_centre", "stadium", "pitch", "swimming_area", "swimming_pool", "golf_course", "miniature_golf", "ice_rink"
  );

  private static final Set<String> UNIVERSITY_POI_SUBCLASSES = Set.of("university", "college");
  private static final List<String> AGG_STOP_SUBCLASS_ORDER = List.of(
    "subway",
    "tram_stop",
    "bus_station",
    "bus_stop"
  );
  private static final Comparator<Tables.OsmPoiPoint> BY_SUBCLASS = Comparator
    .comparingInt(s -> AGG_STOP_SUBCLASS_ORDER.indexOf(s.subclass()));
  private static final Set<String> BRAND_OPERATOR_REF_SUBCLASSES = Set.of("charging_station", "parcel_locker");
  private static final Set<String> MARINE_BUOYS = Set.of(
    "buoy_cardinal", "buoy_lateral", "buoy_isolated_danger", "buoy_safe_water", "buoy_special_purpose",
    "buoy_installation");
  private static final Set<String> MARINE_BEACONS = Set.of(
    "beacon_cardinal", "beacon_lateral", "beacon_isolated_danger", "beacon_safe_water", "beacon_special_purpose",
    "light_major", "light_minor", "daymark");
  private record MarineTag(String key, String kind) {}

  /** A bounded set of mapped features, not an inference from water depth or proximity to the coast. */
  private static MarineTag marineTag(SourceFeature source) {
    String seamark = source.getString("seamark:type");
    if ("wreck".equals(seamark)) return new MarineTag("seamark:type", "wreck");
    if (source.hasTag("historic", "wreck")) return new MarineTag("historic", "wreck");
    if (source.hasTag("man_made", "lighthouse")) return new MarineTag("man_made", "lighthouse");
    if (source.hasTag("leisure", "slipway")) return new MarineTag("leisure", "slipway");
    if (source.hasTag("highway", "service") && source.hasTag("service", "slipway")) {
      return new MarineTag("service", "slipway");
    }
    if (seamark != null && MARINE_BUOYS.contains(seamark)) return new MarineTag("seamark:type", "buoy");
    if (seamark != null && MARINE_BEACONS.contains(seamark)) return new MarineTag("seamark:type", "beacon");
    if (source.hasTag("man_made", "buoy", "beacon")) {
      return new MarineTag("man_made", source.getString("man_made"));
    }
    if (source.hasTag("seamark", "buoy", "beacon")) {
      return new MarineTag("seamark", source.getString("seamark"));
    }
    if ("rock".equals(seamark)) return new MarineTag("seamark:type", "marine_rock");
    if (source.hasTag("natural", "reef", "shoal")) return new MarineTag("natural", source.getString("natural"));
    if (source.hasTag("sport", "surfing", "swimming", "scuba_diving", "canoe", "sailing", "rowing", "water_ski")) {
      return new MarineTag("sport", source.getString("sport"));
    }
    if (source.hasTag("leisure", "marina", "swimming_area")) {
      return new MarineTag("leisure", source.hasTag("leisure", "marina") ? "marina" : "swimming");
    }
    if (source.hasTag("waterway", "dock")) return new MarineTag("waterway", "dock");
    if (source.hasTag("man_made", "pier")) return new MarineTag("man_made", "pier");
    if (seamark != null && !seamark.isBlank() && !Set.of("no", "yes").contains(seamark)) {
      return new MarineTag("seamark:type", "seamark");
    }
    if (source.hasTag("mooring") && !source.hasTag("mooring", "no")) return new MarineTag("mooring", "mooring");
    return null;
  }
  private final MultiExpression.Index<String> classMapping;
  private final Translations translations;
  private final Stats stats;
  private final int maximumZoom;
  private static final String KEEP_LOW_ZOOM = "__keep_low_zoom";
  private final Map<String, List<Tables.OsmPoiPoint>> aggStops = new HashMap<>();

  public Poi(Translations translations, PlanetilerConfig config, Stats stats) {
    this.classMapping = FieldMappings.Class.index();
    this.translations = translations;
    this.stats = stats;
    this.maximumZoom = config.maxzoom();
  }

  static int poiClassRank(String clazz) {
    return CLASS_RANKS.getOrDefault(clazz, 1_000);
  }

  /** Do not infer outdoor/public parking from a name, surface, or nearby landscape. */
  private static boolean isPublicTrailheadParking(SourceFeature source) {
    if (!source.hasTag("amenity", "parking") ||
      !(source.hasTag("trailhead", "yes") || source.hasTag("highway", "trailhead"))) return false;
    for (String key : List.of("access", "vehicle", "motor_vehicle", "motorcar", "foot")) {
      String value = source.getString(key);
      if (value != null && !Set.of("yes", "public", "permissive").contains(value)) return false;
      if (source.hasTag(key + ":conditional")) return false;
    }
    // Missing access is unknown, not proof that a car park is public.
    return source.hasTag("access", "yes", "public", "permissive");
  }

  private String poiClass(String subclass, String mappingKey) {
    // New standalone types reuse icons already present in current client sprites.
    if ("highway".equals(mappingKey) && "trailhead".equals(subclass)) return "information";
    if ("tourism".equals(mappingKey) && "wilderness_hut".equals(subclass)) return "lodging";
    if ("natural".equals(mappingKey) && Set.of("stone", "cave_entrance").contains(subclass)) return "attraction";
    if ("amenity".equals(mappingKey)) {
      if ("water_point".equals(subclass)) return "water"; // Do not imply potability.
      if ("bicycle_repair_station".equals(subclass)) return "bicycle";
      if ("bench".equals(subclass)) return "information";
    }
    if ("sport".equals(mappingKey) && Set.of("hiking", "walking", "trail_running").contains(subclass)) {
      return "information";
    }
    // Special case subclass collision between office=university and amenity=university
    if ("amenity".equals(mappingKey) && "university".equals(subclass)) {
      return FieldValues.CLASS_COLLEGE;
    }

    subclass = coalesce(subclass, "");
    return classMapping.getOrElse(Map.of(
      "subclass", subclass,
      "mapping_key", coalesce(mappingKey, "")
    ), subclass);
  }

  private int minzoom(String subclass, String mappingKey) {
    boolean lowZoom = ("station".equals(subclass) && "railway".equals(mappingKey)) ||
      "halt".equals(subclass) || "ferry_terminal".equals(subclass);
    if ("camp_pitch".equals(subclass)) return 14;
    return lowZoom ? 12 : 12;
  }

  @Override
  public void release() {
    aggStops.clear();
  }

  private static final Set<String> OUTDOOR_BARRIERS = Set.of(
    "gate", "lift_gate", "swing_gate", "kissing_gate", "sliding_gate", "hampshire_gate",
    "chain", "rope", "block", "bollard", "cycle_barrier", "motorcycle_barrier", "stile",
    "cattle_grid", "bump_gate", "kerb", "log", "debris", "jersey_barrier", "height_restrictor",
    "horse_stile", "turnstile", "full-height_turnstile", "sally_port", "border_control", "toll_booth");

  private static String obstacleKey(SourceFeature source) {
    String barrier = OutdoorAttributes.normalized(source.getString("barrier"));
    if (barrier != null && OUTDOOR_BARRIERS.contains(barrier)) return "barrier";
    String hazard = OutdoorAttributes.normalized(source.getString("hazard"));
    if (hazard != null && !"no".equals(hazard)) return "hazard";
    if (source.hasTag("highway", "ford", "elevator", "passing_place", "turning_circle")) return "highway";
    if (source.hasTag("ford", "yes", "stepping_stones")) return "ford";
    return null;
  }

  private static String supplementalOutdoorKey(SourceFeature source) {
    String original = originalSupplementKey(source);
    if (original != null) return original;
    if (source.hasTag("highway", "trailhead")) return "highway";
    if (source.hasTag("tourism", "wilderness_hut")) return "tourism";
    if (source.hasTag("natural", "cave_entrance", "stone", "spring", "hot_spring", "geyser",
      "sinkhole", "arch", "cape", "peninsula", "valley") ||
      (source.hasTag("natural", "tree") && (source.hasTag("name") ||
        source.hasTag("denotation", "landmark", "natural_monument")))) return "natural";
    if (source.hasTag("waterway", "waterfall")) return "waterway";
    if (source.hasTag("emergency", "phone", "access_point", "assembly_point", "mountain_rescue",
      "defibrillator", "lifeguard", "water_rescue", "rescue_box")) return "emergency";
    if (source.hasTag("highway", "emergency_access_point")) return "highway";
    if (source.hasTag("leisure", "picnic_table", "firepit", "fitness_station", "fitness_centre",
      "fishing", "bird_hide", "bathing_place", "beach_resort")) return "leisure";
    if (source.hasTag("man_made", "tower", "observation_tower", "survey_point", "adit", "mineshaft",
      "water_well", "avalanche_protection", "breakwater", "groyne")) return "man_made";
    if (source.hasTag("whitewater", "put_in", "egress", "put_in;egress", "rapid", "waterfall")) return "whitewater";
    if (source.hasTag("entrance") && !source.hasTag("entrance", "no")) return "entrance";
    // Open vocabularies: preserve previously unlisted actual categories without interpreting boolean placeholders.
    for (String key : List.of("shop", "amenity", "sport", "historic")) {
      String value = source.getString(key);
      if (value != null && !value.isBlank() && !Set.of("no", "none", "yes", "vacant").contains(value)) return key;
    }
    return null;
  }

  private static boolean existingPoiMapping(SourceFeature source) {
    return Tables.OsmPoiPoint.MAPPING.evaluate(source) || Tables.OsmPoiPolygon.MAPPING.evaluate(source);
  }

  private static String originalSupplementKey(SourceFeature source) {
    return source.hasTag("highway", "trailhead") ? "highway" :
      source.hasTag("tourism", "wilderness_hut") ? "tourism" :
      source.hasTag("natural", "cave_entrance") ? "natural" :
      source.hasTag("amenity", "bench", "water_point", "bicycle_repair_station") ? "amenity" :
      source.hasTag("sport", "hiking", "walking", "trail_running") ? "sport" : null;
  }

  private static boolean originalSupplement(SourceFeature source) {
    return originalSupplementKey(source) != null;
  }

  /** Add missing outdoor/marine mappings without regenerating the custom generated tables. */
  @Override
  public void processAllOsm(SourceFeature source, FeatureCollector features) {
    String obstacle = obstacleKey(source);
    if (obstacle != null && !Tables.OsmPoiPoint.MAPPING.evaluate(source) &&
      !Tables.OsmPoiPolygon.MAPPING.evaluate(source)) {
      var element = new Tables.OsmPoiPoint(source, obstacle);
      if (source.isPoint()) {
        setupPoiFeature(element, features.point(LAYER_NAME), null);
      } else if (source.canBePolygon() && !source.hasTag("area", "no")) {
        setupPoiFeature(element, features.pointOnSurface(LAYER_NAME), null);
      } else if (source.canBeLine()) {
        setupPoiFeature(element, features.lineMidpoint(LAYER_NAME), null);
      }
      return;
    }
    String key = supplementalOutdoorKey(source);
    MarineTag marine = marineTag(source);
    if (marine != null) {
      // Preserve marine classification/placement for objects admitted before this expansion.
      if (!Set.of("seamark", "mooring").contains(marine.kind())) key = originalSupplementKey(source);
      // Existing dispatch emits the POI when it matches; setupPoiFeature adds marine attributes there too.
      if ((source.isPoint() && Tables.OsmPoiPoint.MAPPING.evaluate(source)) ||
        (source.canBePolygon() && Tables.OsmPoiPolygon.MAPPING.evaluate(source))) return;
      var element = new Tables.OsmPoiPoint(source, key == null ? marine.key() : key);
      if (source.isPoint()) {
        setupPoiFeature(element, features.point(LAYER_NAME), null);
      } else if (source.canBePolygon() && !source.hasTag("area", "no")) {
        setupPoiFeature(element, key == null ? features.pointOnSurface(LAYER_NAME) :
          features.centroidIfConvex(LAYER_NAME), null);
      } else if (source.canBeLine()) {
        setupPoiFeature(element, features.lineMidpoint(LAYER_NAME), null);
      }
      return;
    }
    if (key == null) return;
    // Existing dispatch wins for multi-tag features; never emit a duplicate POI.
    if (source.isPoint() && !Tables.OsmPoiPoint.MAPPING.evaluate(source)) {
      process(new Tables.OsmPoiPoint(source, key), features);
    } else if (source.canBePolygon() && !source.hasTag("area", "no") &&
      !Tables.OsmPoiPolygon.MAPPING.evaluate(source)) {
      setupPoiFeature(new Tables.OsmPoiPolygon(source, key), originalSupplement(source) ?
        features.centroidIfConvex(LAYER_NAME) : features.pointOnSurface(LAYER_NAME), null);
    } else if (source.canBeLine() && source.hasTag("waterway", "waterfall")) {
      setupPoiFeature(new Tables.OsmPoiPoint(source, key), features.lineMidpoint(LAYER_NAME), null);
    }
  }

  @Override
  public void process(Tables.OsmPoiPoint element, FeatureCollector features) {
    if (element.uicRef() != null && AGG_STOP_SUBCLASS_ORDER.contains(element.subclass())) {
      // multiple threads may update this concurrently
      String aggStopKey = element.uicRef()
        .concat(coalesce(nullIfEmpty(element.name()), ""))
        .concat(coalesce(nullIfEmpty(element.network()), ""))
        .concat(coalesce(nullIfEmpty(element.operator()), ""));
      synchronized (this) {
        aggStops.computeIfAbsent(aggStopKey, key -> new ArrayList<>()).add(element);
      }
    } else {
      setupPoiFeature(element, features.point(LAYER_NAME), null);
    }
  }

  private void processAggStop(Tables.OsmPoiPoint element, FeatureCollector.Factory featureCollectors,
    Consumer<FeatureCollector.Feature> emit, Integer aggStop) {
    try {
      var features =
        featureCollectors.get(SimpleFeature.fromWorldGeometry(element.source().worldGeometry(), element.source().id()));
      setupPoiFeature(element, features.point(LAYER_NAME), aggStop);
      for (var feature : features) {
        emit.accept(feature);
      }
    } catch (GeometryException e) {
      e.log(stats, "agg_stop_geometry_2",
        "Error getting geometry for the stop " + element.source().id() + " (agg_stop)");
    }
  }

  /**
   * We've put aside some stops for {@code agg_stop} processing and we do that processing here.
   * <p>
   * The main point is to group together stops with same {@code uid_ref} and then order them first based on subclass
   * (see {@code AGG_STOP_ORDER}) and then based on distance from centroid (calculated from all the stops). The first
   * one gets {@code agg_stop=1}, the rest will be "normal" (e.g. no {@code agg_stop} attribute).
   * <p>
   * ref: <a href=
   * "https://github.com/openmaptiles/openmaptiles/blob/master/layers/poi/poi_stop_agg.sql#L26,L28">poi_stop_agg.sql</a>
   */
  @Override
  public void finish(String sourceName, FeatureCollector.Factory featureCollectors,
    Consumer<FeatureCollector.Feature> emit) {
    if (OpenMapTilesProfile.OSM_SOURCE.equals(sourceName)) {
      var timer = stats.startStage("agg_stop");
      LOGGER.info("Processing {} agg_stop sets", aggStops.size());

      for (var aggStopSet : aggStops.values()) {
        if (aggStopSet.size() == 1) {
          processAggStop(aggStopSet.getFirst(), featureCollectors, emit, 1);
          continue;
        }
        Tables.OsmPoiPoint nearest = null;
        try {
          // find most important stops based on subclass
          var firstSubclass = aggStopSet.stream().min(BY_SUBCLASS).get().subclass();
          var topAggStops =
            aggStopSet.stream().filter(s -> firstSubclass.equals(s.subclass())).toArray(Tables.OsmPoiPoint[]::new);

          // calculate the centroid and ...
          List<Point> aggStopPoints = new ArrayList<>(aggStopSet.size());
          for (var aggStop : aggStopSet) {
            aggStopPoints.add(aggStop.source().worldGeometry().getCentroid());
          }
          var aggStopCentroid = GeoUtils.combinePoints(aggStopPoints).getCentroid();

          // ... find one stop nearest to the centroid
          double minDistance = Double.MAX_VALUE;
          for (var aggStop : topAggStops) {
            double distance = aggStopCentroid.distance(aggStop.source().worldGeometry());
            if (distance < minDistance || nearest == null ||
              (distance == minDistance && aggStop.source().id() < nearest.source().id())) {
              minDistance = distance;
              nearest = aggStop;
            }
          }
        } catch (GeometryException e) {
          e.log(stats, "agg_stop_geometry_1",
            "Error getting geometry for some of the stops with UIC ref. " + aggStopSet.getFirst().uicRef() +
              " (agg_stop)");
          // we're not able to calculate agg_stop, so simply dump the stops as they are
          nearest = null;
        }

        // now emit the stops
        final Tables.OsmPoiPoint nearestFinal = nearest; // final needed for lambda
        aggStopSet
          .forEach(s -> processAggStop(s, featureCollectors, emit, s == nearestFinal ? 1 : null));
      }

      timer.stop();
    }
  }

  @Override
  public void process(Tables.OsmPoiPolygon element, FeatureCollector features) {
    setupPoiFeature(element, features.centroidIfConvex(LAYER_NAME), null);
  }

  private <T extends Tables.WithSubclass & Tables.WithStation & Tables.WithFunicular & Tables.WithSport & Tables.WithInformation & Tables.WithReligion & Tables.WithMappingKey & Tables.WithName & Tables.WithIndoor & Tables.WithLayer & Tables.WithSource & Tables.WithOperator & Tables.WithNetwork & Tables.WithBrand & Tables.WithRef> void setupPoiFeature(
    T element, FeatureCollector.Feature output, Integer aggStop) {
    String rawSubclass = element.subclass();
    if ("station".equals(rawSubclass) && "subway".equals(element.station())) {
      rawSubclass = "subway";
    }
    if ("station".equals(rawSubclass) && "yes".equals(element.funicular())) {
      rawSubclass = "halt";
    }

    // ATM names fall back to operator, or else network
    String name = element.name();
    var tags = element.source().tags();
    if ("atm".equals(rawSubclass) && nullOrEmpty(name)) {
      name = coalesce(nullIfEmpty(element.operator()), nullIfEmpty(element.network()));
      if (name != null) {
        tags.put("name", name);
      }
    }

    // Parcel locker without name: use either brand or operator and add ref if present
    if (BRAND_OPERATOR_REF_SUBCLASSES.contains(rawSubclass) && nullOrEmpty(name)) {
      name = coalesce(nullIfEmpty(element.brand()), nullIfEmpty(element.operator()));
      String ref = nullIfEmpty(element.ref());
      if (ref != null) {
        name = name == null ? ref : (name + " " + ref);
      }
      if (name != null) {
        tags.put("name", name);
      }
    }

    // Camp pitch: tiny features, often have only a numeric ref -> use it as a fallback name
    if ("camp_pitch".equals(rawSubclass) && nullOrEmpty(name)) {
      String ref = nullIfEmpty(element.ref());
      if (ref != null) {
        name = ref;
        tags.put("name", name);
      }
    }

    String subclass = switch (rawSubclass) {
      case "information" -> nullIfEmpty(element.information());
      case "place_of_worship" -> nullIfEmpty(element.religion());
      case "pitch" -> nullIfEmpty(element.sport());
      default -> rawSubclass;
    };
    String poiClass = poiClass(rawSubclass, element.mappingKey());
    MarineTag marine = marineTag(element.source());
    // New standalone marine features use an existing client icon class as a compatibility fallback.
    // Existing POI classifications (e.g. a lighthouse also tagged tourism=attraction) are preserved.
    if (marine != null && originalSupplementKey(element.source()) == null &&
      !Tables.OsmPoiPoint.MAPPING.evaluate(element.source()) &&
      !Tables.OsmPoiPolygon.MAPPING.evaluate(element.source())) {
      poiClass = FieldValues.CLASS_HARBOR;
    }
    boolean addedCoverage = !existingPoiMapping(element.source()) &&
      !originalSupplement(element.source()) && obstacleKey(element.source()) == null &&
      marine == null;
    if (addedCoverage) {
      subclass = rawSubclass;
      poiClass = switch (element.mappingKey()) {
        case "shop" -> "shop";
        case "natural" -> element.source().hasTag("natural", "spring", "hot_spring", "geyser") ? "water" : "attraction";
        case "historic", "man_made" -> "attraction";
        case "waterway" -> "waterfall";
        case "whitewater" -> "harbor";
        default -> "information";
      };
    }
    int poiClassRank = poiClassRank(poiClass);
    boolean ultraPriority = "parking".equals(rawSubclass) ? isPublicTrailheadParking(element.source()) :
      ULTRA_PRIORITY_SUBCLASSES.contains(rawSubclass) ||
        !nullOrEmpty(element.sport()) || "winter_sports".equals(rawSubclass);
    // Even unnamed essentials precede ordinary named POIs (whose best class score is 20).
    // Camp pitches explicitly use their raw subtype here, while their class stays "campsite".
    int rankOrder = ultraPriority ? (nullOrEmpty(name) ? 1 : 0) :
      obstacleKey(element.source()) != null ? (nullOrEmpty(name) ? 3 : 2) :
      "bench".equals(rawSubclass) ? (nullOrEmpty(name) ? 11 : 10) :
      poiClassRank + (nullOrEmpty(name) ? 2000 : 0);

    int minzoom = addedCoverage || (marine != null && Set.of("seamark", "mooring").contains(marine.kind())) ?
      14 : minzoom(element.subclass(), element.mappingKey());
    // Remove this internal marker after ranking. It never becomes a tile attribute.
    output.setAttr(KEEP_LOW_ZOOM, ultraPriority || "parking".equals(rawSubclass) ||
      "harbor".equals(poiClass) || "park".equals(poiClass) || "water".equals(poiClass));
    if (!addedCoverage && UNIVERSITY_POI_SUBCLASSES.contains(rawSubclass)) {
      // universities that are at least 10% of a tile may appear from Z10
      output.setMinPixelSizeBelowZoom(13, 80); // 80x80px is ~10% of a 256x256px tile
      minzoom = 10;
    }

    output.setBufferPixels(BUFFER_SIZE)
      .setAttr(Fields.CLASS, poiClass)
      .setAttr(Fields.SUBCLASS, subclass)
      .setAttr(Fields.LAYER, nullIfLong(element.layer(), 0))
      .setAttr(Fields.LEVEL, Parse.parseLongOrNull(element.source().getTag("level")))
      .setAttr(Fields.INDOOR, element.indoor() ? 1 : null)
      .setAttr(Fields.AGG_STOP, aggStop)
      .putAttrs(OmtLanguageUtils.getNames(element.source().tags(), translations))
      .setPointLabelGridPixelSize(14, 64)
      .setSortKey(rankOrder)
      .setMinZoom(minzoom);
    CoverageAttributes.poi(element.source(), output);
    if (addedCoverage) output.setAttr("kind", element.mappingKey());
    if (obstacleKey(element.source()) != null) {
      OutdoorAttributes.copy(element.source(), output, minzoom);
      output.setAttr("outdoor_obstacle", obstacleKey(element.source()))
        .setAttr("outdoor_geometry", element.source().isPoint() ? "point" :
          element.source().canBePolygon() && !element.source().hasTag("area", "no") ? "area" : "line");
    }
    if (marine != null) {
      output.setAttr(Fields.MARINE_KIND, marine.kind())
        .setAttr(Fields.SEAMARK_TYPE, element.source().getString("seamark:type"));
      if (nullOrEmpty(name)) {
        String marineName = coalesce(nullIfEmpty(element.source().getString("seamark:name")),
          nullIfEmpty(element.ref()));
        if (marineName != null) output.setAttr(Fields.NAME, marineName);
      }
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) {
    // infer the "rank" field from the order of features within each label grid square
    LongIntMap groupCounts = Hppc.newLongIntHashMap();
    for (VectorTile.Feature feature : items) {
      int gridrank = groupCounts.getOrDefault(feature.group(), 1);
      groupCounts.put(feature.group(), gridrank + 1);
      if (!feature.tags().containsKey(Fields.RANK)) {
        feature.tags().put(Fields.RANK, gridrank);
      }
    }
    List<VectorTile.Feature> retained = new ArrayList<>(items.size());
    for (VectorTile.Feature feature : items) {
      boolean keep = Boolean.TRUE.equals(feature.tags().remove(KEEP_LOW_ZOOM));
      Object rank = feature.tags().get(Fields.RANK);
      boolean transit = Set.of("airport", "rail").contains(String.valueOf(feature.tags().get(Fields.CLASS))) ||
        ("bus".equals(feature.tags().get(Fields.CLASS)) && "bus_stop".equals(feature.tags().get(Fields.SUBCLASS)));
      if (zoom < 12 || zoom >= 14 || zoom == maximumZoom || keep || transit ||
        !(rank instanceof Number number) || number.intValue() <= 12) retained.add(feature);
    }
    return retained;
  }
}

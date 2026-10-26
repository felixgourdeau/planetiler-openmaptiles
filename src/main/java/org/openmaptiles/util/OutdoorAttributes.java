package org.openmaptiles.util;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Additive outdoor metadata. Missing or unrecognized values never imply permission or suitability. */
public final class OutdoorAttributes {
  private OutdoorAttributes() {}

  public static final List<String> LIFECYCLES = List.of(
    "destroyed", "abandoned", "disused", "demolished", "removed", "razed", "proposed", "construction");
  private static final List<String> MODES = List.of(
    "access", "foot", "bicycle", "horse", "vehicle", "motor_vehicle", "motorcar", "motorcycle",
    "atv", "ohv", "snowmobile", "ski", "wheelchair", "hgv", "agricultural", "forestry", "dog", "ski:nordic", "ski:alpine");
  private static final Set<String> ACCESS = Set.of(
    "yes", "no", "private", "permissive", "designated", "official", "destination", "delivery",
    "customers", "permit", "agricultural", "forestry", "discouraged", "use_sidepath", "dismount",
    "limited", "unknown", "leashed", "unleashed");
  private static final Set<String> BOOLEAN = Set.of("yes", "no");
  private static final Map<String, Set<String>> CATEGORIES = Map.ofEntries(
    Map.entry("tracktype", Set.of("grade1", "grade2", "grade3", "grade4", "grade5")),
    Map.entry("smoothness", Set.of("excellent", "good", "intermediate", "bad", "very_bad",
      "horrible", "very_horrible", "impassable")),
    Map.entry("surface", Set.of("paved", "unpaved", "asphalt", "concrete", "concrete:lanes",
      "concrete:plates", "paving_stones", "sett", "unhewn_cobblestone", "cobblestone", "metal",
      "wood", "compacted", "fine_gravel", "gravel", "pebblestone", "rock", "rocky", "ground", "dirt",
      "earth", "mud", "sand", "grass", "grass_paver", "gravel_turf", "woodchips", "snow", "ice", "salt")),
    Map.entry("locked", BOOLEAN), Map.entry("informal", BOOLEAN), Map.entry("lit", BOOLEAN),
    Map.entry("fee", Set.of("yes", "no", "donation")),
    Map.entry("4wd_only", Set.of("yes", "no", "recommended")),
    Map.entry("seasonal", Set.of("yes", "no", "spring", "summer", "autumn", "winter", "wet_season", "dry_season")),
    Map.entry("mtb:scale", Set.of("0", "1", "2", "3", "4", "5", "6", "0-", "0+", "1-", "1+",
      "2-", "2+", "3-", "3+", "4-", "4+", "5-", "5+", "6-", "6+")),
    Map.entry("mtb:scale:uphill", Set.of("0", "1", "2", "3", "4", "5")),
    Map.entry("mtb:scale:imba", Set.of("0", "1", "2", "3", "4")),
    Map.entry("sac_scale", Set.of("strolling", "hiking", "mountain_hiking", "demanding_mountain_hiking",
      "alpine_hiking", "demanding_alpine_hiking", "difficult_alpine_hiking")),
    Map.entry("trail_visibility", Set.of("excellent", "good", "intermediate", "bad", "horrible", "no")));
  private static final List<String> DESCRIPTORS = List.of(
    "highway", "service", "barrier", "hazard", "hazard:animal", "hazard:weather",
    "ford", "bridge", "tunnel", "junction", "crossing", "crossing:signals", "traffic_calming",
    "usage", "maintenance", "maintenance:gritting", "maintenance:snowplowing", "winter_service", "maintained",
    "trailblazed", "trailblazed:visibility", "handrail", "handrail:left", "handrail:right",
    "ramp", "ramp:wheelchair", "ramp:bicycle", "segregated", "designation", "trail_marking",
    "piste:type", "piste:difficulty", "piste:grooming", "safety_rope", "ladder", "via_ferrata_scale",
    "flood_prone", "avalanche_prone", "tidal", "intermittent", "winter_road", "ice_road", "shelter",
    "trail_visibility:description", "obstacle");
  private static final List<String> TEXT = List.of(
    "operator", "operator:type", "owner", "ref", "name", "description", "description:en", "opening_hours",
    "opening_hours:conditional", "check_date", "survey:date", "source", "source:date", "source:access",
    "source:surface", "check_date:access", "check_date:barrier", "website", "url");
  private static final List<String> LENGTHS = List.of(
    "width", "est_width", "maxwidth", "maxwidth:physical", "maxheight", "maxheight:physical", "maxlength", "depth");
  private static final Pattern METRIC_LENGTH = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*(m|cm|mm|ft|feet|in)?$");
  private static final Pattern FEET_INCHES = Pattern.compile("^([0-9]+)\\s*'\\s*([0-9]+(?:\\.[0-9]+)?)?\\s*\"?$");

  private static final Pattern MASS = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*(t|kg|lb|lbs|st|lt)?$");
  private static final Pattern SPEED = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*(km/h|kmh|kph|mph)?$");
  private static final Pattern GRADE = Pattern.compile("^([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*%?$");

  public static String normalized(String value) {
    if (value == null || value.isBlank()) return null;
    return value.strip().toLowerCase(Locale.ROOT);
  }

  private static void put(FeatureCollector.Feature output, String key, Object value, int zoom) {
    if (value != null) output.setAttrWithMinzoom(key, value, zoom);
  }

  private static String text(SourceFeature source, String key) {
    String value = source.getString(key);
    return value == null || value.isBlank() ? null : value;
  }

  private static void category(SourceFeature source, FeatureCollector.Feature output, String key,
    Set<String> accepted, int zoom) {
    String raw = text(source, key);
    String value = normalized(raw);
    if (accepted == BOOLEAN && value != null) {
      value = switch (value) {
        case "true", "1" -> "yes";
        case "false", "0" -> "no";
        default -> value;
      };
    }
    // Existing fields are left intact; strict normalized counterparts are always explicit.
    put(output, key + "_normalized", value != null && accepted.contains(value) ? value : null, zoom);
    put(output, key + "_raw", raw, zoom);
  }

  private static Double meters(String raw) {
    String value = normalized(raw);
    if (value == null) return null;
    var feet = FEET_INCHES.matcher(value);
    if (feet.matches()) {
      double inches = feet.group(2) == null ? 0 : Double.parseDouble(feet.group(2));
      if (inches >= 12) return null;
      double result = Double.parseDouble(feet.group(1)) * 0.3048 + inches * 0.0254;
      return Double.isFinite(result) ? result : null;
    }
    if (value.endsWith("\"")) value = value.substring(0, value.length() - 1).strip() + " in";
    var metric = METRIC_LENGTH.matcher(value);
    if (!metric.matches()) return null;
    double factor = switch (metric.group(2) == null ? "m" : metric.group(2)) {
      case "cm" -> 0.01;
      case "mm" -> 0.001;
      case "ft", "feet" -> 0.3048;
      case "in" -> 0.0254;
      default -> 1;
    };
    double result = Double.parseDouble(metric.group(1)) * factor;
    return Double.isFinite(result) ? result : null;
  }

  private static Double quantity(String raw, Pattern pattern, Map<String, Double> factors) {
    if (raw == null || raw.isBlank()) return null;
    String value = raw.strip();
    var match = pattern.matcher(value);
    if (!match.matches()) return null;
    String unit = match.group(2);
    double result = Double.parseDouble(match.group(1)) * factors.getOrDefault(unit == null ? "" : unit, 1d);
    return Double.isFinite(result) ? result : null;
  }

  public static void copy(SourceFeature source, FeatureCollector.Feature output, int zoom) {
    for (var entry : CATEGORIES.entrySet()) category(source, output, entry.getKey(), entry.getValue(), zoom);
    for (String mode : MODES) {
      category(source, output, mode, ACCESS, zoom);
      for (String suffix : List.of(":conditional", ":forward:conditional", ":backward:conditional")) {
        put(output, mode + suffix, text(source, mode + suffix), zoom);
      }
      for (String direction : List.of(":forward", ":backward")) {
        category(source, output, mode + direction, ACCESS, zoom);
      }
    }
    for (String key : DESCRIPTORS) {
      String raw = text(source, key);
      // Open vocabularies are lexical normalization only, not an inferred classification.
      put(output, key + "_normalized", normalized(raw), zoom);
      put(output, key + "_raw", raw, zoom);
    }
    for (String key : TEXT) put(output, "outdoor:" + key, text(source, key), zoom);
    for (String key : LENGTHS) {
      String raw = text(source, key);
      put(output, key + "_raw", raw, zoom);
      put(output, key + "_m", meters(raw), zoom);
      put(output, key + ":conditional", text(source, key + ":conditional"), zoom);
    }
    for (String key : List.of("maxweight", "maxaxleload", "maxspeed", "incline", "step_count")) {
      put(output, key + "_raw", text(source, key), zoom);
      put(output, key + ":conditional", text(source, key + ":conditional"), zoom);
    }
    for (String key : List.of("maxweight", "maxaxleload")) {
      put(output, key + "_kg", quantity(text(source, key), MASS,
        Map.of("", 1000d, "t", 1000d, "kg", 1d, "lb", 0.45359237, "lbs", 0.45359237, "st", 907.18474, "lt", 1016.0469088)), zoom);
    }
    put(output, "maxspeed_kmh", quantity(text(source, "maxspeed"), SPEED, Map.of("mph", 1.609344)), zoom);
    String incline = normalized(source.getString("incline"));
    if (incline != null) {
      var grade = GRADE.matcher(incline);
      if (grade.matches()) {
        double value = Double.parseDouble(grade.group(1));
        if (Double.isFinite(value)) put(output, "incline_percent", value, zoom);
      } else if (Set.of("up", "down").contains(incline)) {
        put(output, "incline_direction", incline, zoom);
      }
    }
    String steps = normalized(source.getString("step_count"));
    if (steps != null && steps.matches("[0-9]{1,9}")) {
      put(output, "step_count_normalized", Integer.parseInt(steps), zoom);
    }
    for (String suffix : List.of("", ":foot", ":bicycle", ":horse", ":vehicle", ":motor_vehicle",
      ":motorcar", ":motorcycle", ":atv", ":ohv", ":snowmobile")) {
      String key = "oneway" + suffix;
      String value = normalized(source.getString(key));
      Integer direction = value == null ? null : switch (value) {
        case "yes", "true", "1" -> 1;
        case "no", "false", "0" -> 0;
        case "-1", "reverse" -> -1;
        default -> null;
      };
      put(output, key + "_normalized", direction, zoom);
      put(output, key + "_raw", text(source, key), zoom);
      put(output, key + ":conditional", text(source, key + ":conditional"), zoom);
    }
    for (String key : List.of("locked", "4wd_only", "fee")) {
      put(output, key + ":conditional", text(source, key + ":conditional"), zoom);
    }
    for (String lifecycle : LIFECYCLES) {
      put(output, lifecycle + ":highway", normalized(source.getString(lifecycle + ":highway")), zoom);
      put(output, lifecycle + "_raw", text(source, lifecycle), zoom);
    }
  }
}

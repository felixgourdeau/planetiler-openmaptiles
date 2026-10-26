package org.openmaptiles.util;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;

/** Sparse detail fields, available in the z14 tiles used for client overzooming. */
public final class CoverageAttributes {
  private CoverageAttributes() {}

  public static void copy(SourceFeature source, FeatureCollector.Feature output, String... keys) {
    for (String key : keys) {
      String value = source.getString(key);
      if (value != null && !value.isBlank()) output.setAttrWithMinzoom(key, value, 14);
    }
  }

  public static void poi(SourceFeature source, FeatureCollector.Feature output) {
    copy(source, output, "opening_hours", "opening_hours:conditional", "fee", "fee:conditional",
      "access", "access:conditional", "wheelchair", "operator", "capacity", "reservation", "backcountry",
      "shelter_type", "drinking_water", "toilets", "shower", "hot_water", "electricity", "tents", "caravans",
      "motorhome", "bbq", "fireplace", "seasonal", "intermittent", "sport", "emergency", "ref",
      "ele", "locked", "maxwidth:physical", "width", "boat", "canoe", "whitewater", "mooring",
      "seamark:berth:category", "seamark:mooring:category", "seamark:buoy_lateral:category",
      "seamark:buoy_lateral:colour", "seamark:buoy_cardinal:category", "seamark:buoy_cardinal:colour",
      "seamark:beacon_lateral:category", "seamark:beacon_cardinal:category", "seamark:light:colour",
      "seamark:light:character", "seamark:light:period", "seamark:light:range",
      "seamark:rock:water_level", "seamark:wreck:category", "seamark:wreck:water_level");
    // Preserve sparse subtype-specific seamark data without repeating the existing type/name aliases.
    for (String key : source.tags().keySet()) {
      if (key.startsWith("seamark:") && !key.equals("seamark:type") && !key.equals("seamark:name")) {
        copy(source, output, key);
      }
    }
    // A single canonical field avoids storing the same contact value twice.
    for (String key : List.of("website", "phone", "mobile", "email", "fax")) {
      String value = source.getString(key);
      if (value == null || value.isBlank()) value = source.getString("contact:" + key);
      if ((value == null || value.isBlank()) && "website".equals(key)) value = source.getString("url");
      if (value != null && !value.isBlank()) output.setAttrWithMinzoom(key, value, 14);
    }
  }
}

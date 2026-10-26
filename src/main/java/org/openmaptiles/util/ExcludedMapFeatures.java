package org.openmaptiles.util;

import com.onthegomap.planetiler.reader.WithTags;

/** Explicit feature exclusions; a road's sidewalk=yes/both tag never excludes the road. */
public final class ExcludedMapFeatures {
  private ExcludedMapFeatures() {}

  public static boolean excluded(WithTags feature) {
    return feature.hasTag("barrier", "kerb") || feature.hasTag("highway", "kerb", "sidewalk") ||
      feature.hasTag("footway", "sidewalk") || feature.hasTag("area:highway", "sidewalk");
  }
}

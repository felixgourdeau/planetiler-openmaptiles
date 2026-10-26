package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.Translations;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openmaptiles.Layer;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.Tables;
import org.openmaptiles.util.CoverageAttributes;
import org.openmaptiles.util.OmtLanguageUtils;

/** Fine outdoor geometry in the existing archive. All additions start at z14. */
public class OutdoorFeatures implements Layer, OpenMapTilesProfile.OsmAllProcessor,
  OpenMapTilesProfile.OsmRelationPreprocessor, ForwardingProfile.LayerPostProcessor {
  private final Translations translations;

  public OutdoorFeatures(Translations translations) {
    this.translations = translations;
  }

  @Override public String name() { return "outdoor"; }

  @Override public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) {
    // Pack equal-attribute geometry together without dropping paths or simplifying coordinates.
    return FeatureMerge.mergeMultiPolygon(FeatureMerge.mergeMultiLineString(items));
  }

  private record Route(long id, Map<String, String> tags) implements OsmRelationInfo {
    @Override public long estimateMemoryUsageBytes() {
      long size = 64;
      for (var entry : tags.entrySet()) size += 64 + 2L * (entry.getKey().length() + entry.getValue().length());
      return size;
    }
  }

  @Override public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (!relation.hasTag("type", "route") || !relation.hasTag("route", "hiking", "foot", "walking",
      "bicycle", "mtb", "horse", "canoe", "piste", "ski", "via_ferrata")) return null;
    Map<String, String> tags = new LinkedHashMap<>();
    for (String key : List.of("route", "name", "ref", "network", "colour", "osmc:symbol",
      "piste:type", "piste:difficulty", "piste:grooming", "whitewater:grade")) {
      String value = relation.getString(key);
      if (value != null && !value.isBlank()) tags.put(key, value);
    }
    return List.of(new Route(relation.id(), Map.copyOf(tags)));
  }

  @Override public void processAllOsm(SourceFeature source, FeatureCollector features) {
    if (!source.canBeLine() && !source.canBePolygon()) return;
    String category = null;
    String subtype = null;
    boolean polygon = source.canBePolygon() && !source.hasTag("area", "no");
    boolean highway = Tables.OsmHighwayLinestring.MAPPING.evaluate(source) && !source.hasTag("area", "yes");
    if (source.hasTag("piste:type") && !source.hasTag("piste:type", "no") && !highway) {
      category = "piste";
      subtype = source.getString("piste:type");
      // Closed Nordic circuits are lines, not filled ski areas.
      polygon = polygon && !source.hasTag("piste:type", "nordic") &&
        (source.hasTag("area", "yes") || source.hasTag("type", "multipolygon"));
    } else if (source.hasTag("highway", "via_ferrata")) {
      category = "via_ferrata";
      subtype = "via_ferrata";
      polygon = false;
    } else if (source.hasTag("barrier", "fence", "wall", "hedge", "retaining_wall", "city_wall", "ditch")) {
      category = "barrier";
      subtype = source.getString("barrier");
      polygon = source.hasTag("area", "yes") && polygon;
    } else if (source.hasTag("natural", "valley", "arch", "sinkhole", "cape", "peninsula", "tree_row")) {
      category = "natural";
      subtype = source.getString("natural");
      if (source.hasTag("natural", "valley", "tree_row")) polygon = source.hasTag("area", "yes") && polygon;
    } else if (source.hasTag("man_made", "avalanche_protection", "breakwater", "groyne")) {
      category = "man_made";
      subtype = source.getString("man_made");
    } else if (source.hasTag("whitewater", "rapid", "portage_way") || source.hasTag("waterway", "rapids")) {
      category = "whitewater";
      subtype = source.getString("whitewater", source.getString("waterway"));
    }
    var routes = source.relationInfo(Route.class);
    // Highway members already carry route information in transportation_name.
    if (category == null && !highway && !routes.isEmpty() && source.canBeLine()) {
      category = "route";
      polygon = false;
    }
    if (category == null) return;
    if (!polygon && !source.canBeLine()) return;
    var output = polygon ? features.polygon(name()) : features.line(name());
    output.setMinZoom(14).setBufferPixels(4).setAttr("class", category).setAttr("subclass", subtype)
      .putAttrs(OmtLanguageUtils.getNames(source.tags(), translations));
    CoverageAttributes.copy(source, output, "ref", "access", "foot", "bicycle", "horse", "canoe", "ski",
      "access:conditional", "foot:conditional", "piste:type", "piste:difficulty", "piste:grooming",
      "piste:name", "piste:ref", "colour", "patrolled", "operator", "via_ferrata_scale", "width",
      "height", "fence_type", "material", "locked", "maxwidth:physical", "whitewater:grade",
      "whitewater:section_name", "whitewater", "portage", "intermittent", "seasonal");
    int index = 0;
    var ordered = routes.stream().map(member -> member.relation()).distinct()
      .sorted(java.util.Comparator.comparingLong(Route::id)).toList();
    for (var route : ordered) {
      String prefix = "route_" + (++index) + "_";
      route.tags().forEach((key, value) -> output.setAttr(prefix + key, value));
    }
  }
}

package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Translations;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.TopologyException;
import org.openmaptiles.Layer;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.util.OmtLanguageUtils;

/** Independent, opt-in styling surface for named polygon labels. Existing layers are unchanged. */
public class PolygonLabel implements Layer, OpenMapTilesProfile.OsmAllProcessor {
  public static final String LAYER_NAME = "polygon_label";
  private final Translations translations;
  private final Stats stats;
  private final int maxZoom;

  public PolygonLabel(Translations translations, PlanetilerConfig config, Stats stats) {
    this.translations = translations;
    this.stats = stats;
    maxZoom = config.maxzoom();
  }

  @Override
  public String name() {
    return LAYER_NAME;
  }

  @Override
  public void processAllOsm(SourceFeature source, FeatureCollector features) {
    if (!source.canBePolygon() || source.hasTag("area", "no")) {
      return;
    }
    String category = category(source);
    if (category == null) {
      return;
    }
    Map<String, Object> names = OmtLanguageUtils.getNames(source.tags(), translations);
    Object name = names.get("name");
    if (name == null || name.toString().isBlank()) {
      name = names.get("name_int");
    }
    if (name == null || name.toString().isBlank()) {
      return;
    }
    names.put("name", name);
    try {
      Polygon polygon = PolygonLabelGeometry.largestPolygon(source.validatedPolygon());
      if (polygon == null || polygon.isEmpty() || polygon.getArea() <= 0) {
        return;
      }
      // Anchor both placement options on the largest connected component of a multipolygon.
      int pointZoom = Math.min(maxZoom, zoomForPixels(Math.sqrt(polygon.getArea()), 48));
      LineString line = null;
      try {
        line = PolygonLabelGeometry.labelLine(polygon);
      } catch (TopologyException | IllegalArgumentException exception) {
        // A failed line calculation must not discard an otherwise usable point label.
        stats.dataError("polygon_label_line_geometry");
      }
      int lineZoom = line == null ? maxZoom + 1 :
        Math.max(pointZoom, zoomForPixels(line.getLength(), Math.max(160, name.toString().length() * 12d)));
      if (lineZoom <= maxZoom) {
        emit(features, line, names, category, "line", lineZoom, maxZoom);
        emit(features, polygon.getInteriorPoint(), names, category, "point", lineZoom, maxZoom)
          .setAttr("line_available", true);
      }
      if (pointZoom < lineZoom) {
        emit(features, polygon.getInteriorPoint(), names, category, "point", pointZoom,
          Math.min(maxZoom, lineZoom - 1))
            .setAttr("line_available", false);
      }
    } catch (GeometryException exception) {
      exception.log(stats, "polygon_label_polygon", "Unable to read named polygon " + source.id());
    } catch (TopologyException | IllegalArgumentException exception) {
      stats.dataError("polygon_label_polygon_geometry");
    }
  }

  private FeatureCollector.Feature emit(FeatureCollector features, Geometry geometry, Map<String, Object> names,
    String category,
    String placement, int minZoom, int maxZoom) {
    return features.geometry(LAYER_NAME, geometry)
      .putAttrs(names)
      .setAttr("class", category)
      .setAttr("placement", placement)
      .setZoomRange(minZoom, maxZoom)
      .setBufferPixels(128)
      // The short generated path is already smoothed and validated inside the polygon.
      .setPixelToleranceAtAllZooms(0)
      .setMinPixelSizeAtAllZooms(0);
  }

  private static int zoomForPixels(double worldSize, double pixels) {
    return Math.max(5, (int) Math.ceil(Math.log(pixels / (256 * worldSize)) / Math.log(2)));
  }

  /** Specific natural features take precedence over overlapping land-use/protection tags. */
  private static String category(SourceFeature source) {
    String natural = source.getString("natural", "");
    switch (natural) {
      case "glacier", "bay", "fjord", "strait", "beach", "wetland" -> {
        return natural;
      }
      default -> {
        // Continue with water, place and land-use tags.
      }
    }
    if (source.hasTag("water", "reservoir") || source.hasTag("landuse", "reservoir")) {
      return "reservoir";
    }
    if (source.hasTag("natural", "water") &&
      (!source.hasTag("water") || source.hasTag("water", "lake", "pond", "lagoon"))) {
      return "lake";
    }
    if (source.hasTag("place", "island", "islet")) {
      return "island";
    }
    if (source.hasTag("natural", "wood") || source.hasTag("landuse", "forest")) {
      return "forest";
    }
    if (source.hasTag("leisure", "park", "nature_reserve") ||
      source.hasTag("boundary", "national_park", "protected_area")) {
      return "park";
    }
    return null;
  }
}

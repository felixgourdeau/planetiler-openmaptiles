package org.openmaptiles.addons;

import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Translations;
import java.util.ArrayList;
import java.util.List;
import org.openmaptiles.Layer;

/**
 * Registry of extra custom layers that you can add to the openmaptiles schema.
 */
public class ExtraLayers {

  public static List<Layer> create(Translations translations, PlanetilerConfig config, Stats stats) {
    List<Layer> layers = new ArrayList<>();
    layers.add(new OutdoorFeatures(translations));
    layers.add(new PolygonLabel(translations, config, stats));
    if (config.arguments().getBoolean("trail_segment_labels",
      "Generate junction-to-junction trail distance labels", true)) {
      if (config.arguments().getString("trail_segment_snapshot", "Prepared segment snapshot", "").isBlank()) {
        layers.add(new TrailSegmentLabels(config));
      } else {
        layers.add(new TrailSegmentSnapshot());
      }
    }
    return List.copyOf(layers);
  }
}

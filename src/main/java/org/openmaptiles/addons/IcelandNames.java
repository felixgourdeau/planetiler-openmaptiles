package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import org.openmaptiles.Layer;

/** Optional, reviewed NÁTT labels. The default planet build does not include this source. */
public class IcelandNames implements Layer {
  @Override
  public String name() {
    return "iceland_name";
  }

  public void process(SourceFeature source, FeatureCollector features) {
    if (!source.hasTag("review_status", "approved") || !source.hasTag("license", "CC-BY-4.0") ||
      !source.hasTag("source", "NÁTT / IS 50V Örnefni")) {
      throw new IllegalArgumentException("Iceland source must be a reviewed prepare-iceland-names snapshot");
    }
    String name = source.getString("name", "");
    String id = source.getString("official_id", "");
    if (name.isBlank() || id.isBlank()) throw new IllegalArgumentException("Missing Iceland name identity");
    // Preserve original input geometry in the snapshot; use a simple point label for this bounded pilot.
    features.pointOnSurface(name())
      .setAttr("name", name)
      .setAttr("name:is", name)
      .setAttr("official_id", id)
      .setAttr("class", source.getString("class"))
      .setAttr("source", source.getString("source"))
      .setAttr("source_revision", source.getString("source_revision"))
      .setAttr("license", "CC-BY-4.0")
      .setZoomRange(12, 14)
      .setBufferPixels(128);
  }
}

package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import org.openmaptiles.Layer;
import org.openmaptiles.tools.PrepareTrailSegments;

/** Prepared complete segment labels; no OSM handlers or mutable graph in the tile build. */
public final class TrailSegmentSnapshot implements Layer {
  public static final String SOURCE = "trail_segments";
  @Override public String name() { return TrailSegmentLabels.LAYER_NAME; }

  public void process(SourceFeature source, FeatureCollector features) {
    if (!source.hasTag("policy", PrepareTrailSegments.POLICY) || !source.isPoint())
      throw new IllegalArgumentException("Invalid trail snapshot feature");
    long id = ((Number) source.getTag("segment_id")).longValue();
    long length = ((Number) source.getTag("length_m")).longValue();
    long bearing = ((Number) source.getTag("bearing")).longValue();
    long loop = ((Number) source.getTag("loop")).longValue();
    if (id < 1 || length < 25 || bearing < -90 || bearing > 90 || (loop != 0 && loop != 1))
      throw new IllegalArgumentException("Invalid trail snapshot attributes");
    features.point(name()).setId(id).setMinZoom(14).setBufferPixels(0)
      .setAttr("length_m", length).setAttr("bearing", bearing).setAttr("loop", loop);
  }
}

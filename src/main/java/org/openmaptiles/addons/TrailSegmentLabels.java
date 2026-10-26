package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.WithTags;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmSourceFeature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.openmaptiles.Layer;
import org.openmaptiles.OpenMapTilesProfile;

/** Junction-to-junction distances, computed before tile clipping or line simplification. */
public final class TrailSegmentLabels implements Layer, ForwardingProfile.OsmWayPreprocessor,
  OpenMapTilesProfile.OsmAllProcessor, ForwardingProfile.FinishHandler {

  public static final String LAYER_NAME = "trail_segment_label";
  private final Map<Long, Vertex> vertices = new HashMap<>();
  private final int maxNodes;
  private final int maxEdges;
  private int edgeCount;
  private String failure;

  public TrailSegmentLabels(PlanetilerConfig config) {
    maxNodes = config.arguments().getInteger("trail_segment_max_nodes", "Maximum trail graph nodes", 2_000_000);
    maxEdges = config.arguments().getInteger("trail_segment_max_edges", "Maximum trail graph edges", 2_000_000);
    if (maxNodes < 1 || maxEdges < 1) {
      throw new IllegalArgumentException("Trail graph limits must be positive");
    }
  }

  @Override
  public String name() {
    return LAYER_NAME;
  }

  public static boolean eligible(WithTags tags) {
    return tags.hasTag("highway", "path", "footway", "steps", "track") &&
      !org.openmaptiles.util.ExcludedMapFeatures.excluded(tags) &&
      !tags.hasTag("area", "yes") && !tags.hasTag("indoor", "yes") &&
      !tags.hasTag("footway", "sidewalk", "crossing") &&
      !tags.hasTag("foot", "no", "private") &&
      (!tags.hasTag("access", "no", "private") || tags.hasTag("foot", "yes", "designated", "permissive")) &&
      !tags.hasTag("disused", "yes") && !tags.hasTag("abandoned", "yes") && !tags.hasTag("construction");
  }

  @Override
  public synchronized void preprocessOsmWay(OsmElement.Way way) {
    if (failure != null || !eligible(way)) {
      return;
    }
    for (var node : way.nodes()) {
      if (!vertices.containsKey(node.value)) {
        if (vertices.size() >= maxNodes) {
          failure = "Trail graph exceeds --trail-segment-max-nodes; use a smaller regional extract";
          return;
        }
        vertices.put(node.value, new Vertex(node.value));
      }
    }
  }

  @Override
  public void processAllOsm(SourceFeature source, FeatureCollector features) {
    if (!(source instanceof OsmSourceFeature osm) ||
      !(osm.originalElement() instanceof OsmElement.Way way) || !way.hasTag("highway")) {
      return;
    }
    synchronized (this) { processWay(source, way); }
  }

  private void processWay(SourceFeature source, OsmElement.Way way) {
    if (failure != null) return;
    if (!eligible(way)) {
      // A road or excluded trail meeting a labeled trail is still a segment boundary.
      for (var node : way.nodes()) {
        var vertex = vertices.get(node.value);
        if (vertex != null) {
          vertex.boundary = true;
        }
      }
      return;
    }
    try {
      if (!(source.line() instanceof LineString line) || line.getNumPoints() != way.nodes().size()) {
        invalidate(way);
        return;
      }
      var coordinates = line.getCoordinates();
      for (int index = 0; index < coordinates.length; index++) {
        vertices.get(way.nodes().get(index)).coordinate = coordinates[index];
      }
      for (int index = 1; index < coordinates.length; index++) {
        var start = vertices.get(way.nodes().get(index - 1));
        var end = vertices.get(way.nodes().get(index));
        if (start == end) {
          continue;
        }
        // Shared OSM edges are counted once even when duplicated in multiple ways.
        if (start.edges.stream().anyMatch(edge -> edge.other(start) == end)) {
          continue;
        }
        if (edgeCount >= maxEdges) {
          failure = "Trail graph exceeds --trail-segment-max-edges; use a smaller regional extract";
          return;
        }
        var edge = new Edge(start, end);
        start.edges.add(edge);
        end.edges.add(edge);
        edgeCount++;
      }
    } catch (GeometryException exception) {
      // Do not join through an incomplete way and publish a plausible but truncated distance.
      invalidate(way);
    }
  }

  private void invalidate(OsmElement.Way way) {
    for (var node : way.nodes()) {
      var vertex = vertices.get(node.value);
      if (vertex != null) {
        vertex.invalid = true;
      }
    }
  }

  @Override
  public synchronized void finish(String sourceName, FeatureCollector.Factory collectors,
    Consumer<FeatureCollector.Feature> emit) {
    if (!OpenMapTilesProfile.OSM_SOURCE.equals(sourceName)) {
      return;
    }
    if (failure != null) {
      throw new IllegalStateException(failure);
    }
    // Sorting makes anchors and feature IDs independent of parallel OSM processing order.
    var ordered = vertices.values().stream().sorted(Comparator.comparingLong(vertex -> vertex.id)).toList();
    for (var vertex : ordered) {
      vertex.edges.sort(Comparator.comparingLong(edge -> edge.other(vertex).id));
    }
    long segmentId = 1;
    for (var vertex : ordered) {
      if (vertex.isBoundary()) {
        for (var edge : vertex.edges) {
          if (!edge.visited) {
            emitSegment(vertex, edge, segmentId++, collectors, emit);
          }
        }
      }
    }
    // Remaining degree-two components are closed loops: emit their full circumference once.
    for (var vertex : ordered) {
      for (var edge : vertex.edges) {
        if (!edge.visited) {
          emitSegment(vertex, edge, segmentId++, collectors, emit);
        }
      }
    }
    vertices.clear();
  }

  private static void emitSegment(Vertex start, Edge first, long id, FeatureCollector.Factory collectors,
    Consumer<FeatureCollector.Feature> emit) {
    List<Vertex> points = new ArrayList<>();
    List<Double> lengths = new ArrayList<>();
    points.add(start);
    var current = start;
    var edge = first;
    double total = 0;
    boolean valid = !start.invalid;
    while (!edge.visited) {
      edge.visited = true;
      var next = edge.other(current);
      valid &= !next.invalid;
      double length = GeoUtils.metersBetween(
        GeoUtils.getWorldLon(current.coordinate.x), GeoUtils.getWorldLat(current.coordinate.y),
        GeoUtils.getWorldLon(next.coordinate.x), GeoUtils.getWorldLat(next.coordinate.y));
      lengths.add(length);
      total += length;
      points.add(next);
      if (next.isBoundary() || next == start) {
        break;
      }
      var previous = edge;
      edge = next.edges.get(0) == previous ? next.edges.get(1) : next.edges.get(0);
      current = next;
    }
    if (!valid || !Double.isFinite(total) || total < 25) {
      return;
    }
    double remaining = total / 2;
    for (int index = 0; index < lengths.size(); index++) {
      double length = lengths.get(index);
      if (length > 0 && remaining <= length) {
        Coordinate from = points.get(index).coordinate;
        Coordinate to = points.get(index + 1).coordinate;
        double fraction = remaining / length;
        double dx = to.x - from.x;
        // Keep interpolation local for segments crossing the antimeridian.
        dx -= Math.rint(dx);
        double x = from.x + dx * fraction;
        x -= Math.floor(x);
        double y = from.y + (to.y - from.y) * fraction;
        double angle = Math.toDegrees(Math.atan2(to.y - from.y, dx));
        if (angle > 90) {
          angle -= 180;
        } else if (angle < -90) {
          angle += 180;
        }
        var features = collectors.get(SimpleFeature.fromWorldGeometry(GeoUtils.point(x, y), id));
        emit.accept(features.point(LAYER_NAME)
          .setId(id)
          .setMinZoom(14)
          .setBufferPixels(0)
          .setAttr("length_m", Math.round(total))
          .setAttr("bearing", Math.round(angle))
          .setAttr("loop", points.getLast() == start ? 1 : 0));
        return;
      }
      remaining -= length;
    }
  }

  private static final class Vertex {
    private final long id;
    private final List<Edge> edges = new ArrayList<>(2);
    private Coordinate coordinate;
    private boolean boundary;
    private boolean invalid;

    private Vertex(long id) {
      this.id = id;
    }

    private boolean isBoundary() {
      return boundary || invalid || edges.size() != 2;
    }
  }

  private static final class Edge {
    private final Vertex start;
    private final Vertex end;
    private boolean visited;

    private Edge(Vertex start, Vertex end) {
      this.start = start;
      this.end = end;
    }

    private Vertex other(Vertex vertex) {
      return vertex == start ? end : start;
    }
  }
}

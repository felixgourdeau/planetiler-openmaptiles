package org.openmaptiles.addons;

import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

/** Bounded cross-section label paths in normalized Web Mercator coordinates, before tile clipping. */
final class PolygonLabelGeometry {
  private static final int SAMPLE_COUNT = 32;
  private static final int MAX_VERTICES = 20_000;
  private static final double MAX_TURN = Math.toRadians(25);

  private PolygonLabelGeometry() {}

  static Polygon largestPolygon(Geometry geometry) {
    if (geometry instanceof Polygon polygon) {
      return polygon;
    }
    Polygon largest = null;
    if (geometry instanceof GeometryCollection collection) {
      for (int index = 0; index < collection.getNumGeometries(); index++) {
        Polygon candidate = largestPolygon(collection.getGeometryN(index));
        if (candidate != null && (largest == null || candidate.getArea() > largest.getArea())) {
          largest = candidate;
        }
      }
    }
    return largest;
  }

  static LineString labelLine(Polygon polygon) {
    // Bound overlay work on enormous coastlines/forests; these still receive a point label.
    if (polygon.getNumPoints() > MAX_VERTICES) {
      return null;
    }
    Geometry rectangle = MinimumDiameter.getMinimumRectangle(polygon);
    if (!(rectangle instanceof Polygon)) {
      return null;
    }
    Coordinate[] corners = rectangle.getCoordinates();
    double firstLength = corners[0].distance(corners[1]);
    double secondLength = corners[1].distance(corners[2]);
    double length = Math.max(firstLength, secondLength);
    double width = Math.min(firstLength, secondLength);
    if (width <= 0 || length < width * 1.5) {
      return null;
    }
    int start = firstLength >= secondLength ? 0 : 1;
    double angle = Math.atan2(corners[start + 1].y - corners[start].y,
      corners[start + 1].x - corners[start].x);
    // A small inset reserves geometric clearance from the exterior and holes.
    Geometry interior = polygon.buffer(-width * 0.04);
    if (interior.isEmpty()) {
      return null;
    }
    PreparedGeometry prepared = PreparedGeometryFactory.prepare(interior);
    LineString best = null;
    for (double offset : new double[]{0, -Math.PI / 6, Math.PI / 6}) {
      LineString candidate = samplePath(interior, prepared, angle + offset);
      if (candidate != null && candidate.getLength() >= length * 0.45 &&
        (best == null || candidate.getLength() > best.getLength())) {
        best = candidate;
      }
    }
    return best;
  }

  private static LineString samplePath(Geometry interior, PreparedGeometry prepared, double angle) {
    double axisX = Math.cos(angle);
    double axisY = Math.sin(angle);
    Coordinate origin = interior.getEnvelopeInternal().centre();
    double minimum = Double.POSITIVE_INFINITY;
    double maximum = Double.NEGATIVE_INFINITY;
    for (Coordinate coordinate : interior.getCoordinates()) {
      double projection = (coordinate.x - origin.x) * axisX + (coordinate.y - origin.y) * axisY;
      minimum = Math.min(minimum, projection);
      maximum = Math.max(maximum, projection);
    }
    double reach = Math.hypot(interior.getEnvelopeInternal().getWidth(), interior.getEnvelopeInternal().getHeight());
    List<Coordinate> run = new ArrayList<>();
    LineString best = null;
    for (int index = 1; index < SAMPLE_COUNT; index++) {
      double projection = minimum + (maximum - minimum) * index / SAMPLE_COUNT;
      double x = origin.x + projection * axisX;
      double y = origin.y + projection * axisY;
      LineString crossSection = interior.getFactory().createLineString(new Coordinate[]{
        new Coordinate(x - axisY * reach, y + axisX * reach),
        new Coordinate(x + axisY * reach, y - axisX * reach)
      });
      LineString widest = longestLine(interior.intersection(crossSection));
      Coordinate midpoint = widest == null ? null : new Coordinate(
        (widest.getCoordinateN(0).x + widest.getCoordinateN(widest.getNumPoints() - 1).x) / 2,
        (widest.getCoordinateN(0).y + widest.getCoordinateN(widest.getNumPoints() - 1).y) / 2);
      if (midpoint == null || (!run.isEmpty() && !prepared.covers(interior.getFactory().createLineString(
        new Coordinate[]{run.getLast(), midpoint})))) {
        best = choosePath(interior, prepared, run, best);
        run.clear();
      }
      if (midpoint != null) {
        run.add(midpoint);
      }
    }
    return choosePath(interior, prepared, run, best);
  }

  private static LineString longestLine(Geometry geometry) {
    if (geometry instanceof LineString line) {
      return line.isEmpty() || line.getLength() == 0 ? null : line;
    }
    LineString longest = null;
    if (geometry instanceof GeometryCollection collection) {
      for (int index = 0; index < collection.getNumGeometries(); index++) {
        LineString candidate = longestLine(collection.getGeometryN(index));
        if (candidate != null && (longest == null || candidate.getLength() > longest.getLength())) {
          longest = candidate;
        }
      }
    }
    return longest;
  }

  private static LineString choosePath(Geometry interior, PreparedGeometry prepared, List<Coordinate> run,
    LineString best) {
    if (run.size() < 8) {
      return best;
    }
    Coordinate[] smoothed = run.toArray(Coordinate[]::new);
    for (int pass = 0; pass < 3; pass++) {
      Coordinate[] next = smoothed.clone();
      for (int index = 1; index < smoothed.length - 1; index++) {
        next[index] = new Coordinate(
          (smoothed[index - 1].x + 2 * smoothed[index].x + smoothed[index + 1].x) / 4,
          (smoothed[index - 1].y + 2 * smoothed[index].y + smoothed[index + 1].y) / 4);
      }
      smoothed = next;
    }
    for (int index = 1; index < smoothed.length - 1; index++) {
      double before = Math.atan2(smoothed[index].y - smoothed[index - 1].y,
        smoothed[index].x - smoothed[index - 1].x);
      double after = Math.atan2(smoothed[index + 1].y - smoothed[index].y,
        smoothed[index + 1].x - smoothed[index].x);
      if (Math.abs(Math.atan2(Math.sin(after - before), Math.cos(after - before))) > MAX_TURN) {
        return best;
      }
    }
    LineString candidate = interior.getFactory().createLineString(smoothed);
    return candidate.isSimple() && prepared.covers(candidate) &&
      (best == null || candidate.getLength() > best.getLength()) ? candidate : best;
  }
}

package org.openmaptiles.tools;

import com.carrotsearch.hppc.LongIntHashMap;
import com.carrotsearch.hppc.LongArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmInputFile;
import java.nio.file.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import org.openmaptiles.addons.TrailSegmentLabels;

/** Compact two-pass preparation. Output is immutable and contains complete, unclipped segments only. */
public final class PrepareTrailSegments {
  public static final String POLICY = "trail-segments-v1";
  // A 0.90 load factor keeps the measured <950M-reference planet within HPPC's maximum table size.
  private final LongIntHashMap nodes = new LongIntHashMap(16, 0.90);
  private final LongArrayList edges = new LongArrayList();
  private double[] longitude;
  private double[] latitude;
  private boolean[] boundary;
  private boolean[] invalid;
  private int[] offsets;
  private int[] adjacency;
  private long[] uniqueEdges;
  private boolean[] visited;
  private long rejected;
  private long segments;
  private static final ObjectMapper JSON = new ObjectMapper();

  private static void scan(Path input, Consumer<OsmElement> consumer) throws IOException {
    try (var source = OsmInputFile.readFrom(input)) {
      source.forEachBlock(block -> { for (var element : block) consumer.accept(element); });
    }
  }

  private int index(long id) {
    int found = nodes.getOrDefault(id, -1);
    if (found >= 0) return found;
    int next = nodes.size();
    if (next >= 950_000_000) throw new IllegalStateException("Graph requires partitioning");
    nodes.put(id, next);
    return next;
  }

  private void checkCapacity(Path input) throws IOException {
    long[] counts = new long[3];
    scan(input, element -> {
      if (element instanceof OsmElement.Way way && TrailSegmentLabels.eligible(way)) {
        counts[0]++;
        counts[1] = Math.addExact(counts[1], way.nodes().size());
        counts[2] = Math.addExact(counts[2], Math.max(0, way.nodes().size() - 1));
      }
    });
    // Conservative upper bounds include repeated way nodes, hash-table growth and sort copies.
    long estimated = Math.addExact(Math.multiplyExact(counts[1], 160L),
      Math.multiplyExact(counts[2], 48L));
    long budget = Runtime.getRuntime().maxMemory() / 10 * 7;
    System.out.println("Graph capacity eligibleWays=" + counts[0] + " nodeReferences=" + counts[1] +
      " edgeUpperBound=" + counts[2] + " estimatedPeakBytes=" + estimated + " heapBudgetBytes=" + budget);
    if (counts[1] >= 950_000_000L || counts[2] > (Integer.MAX_VALUE - 8L) / 2 || estimated > budget) {
      throw new IllegalStateException("Global graph requires partitioning or more memory; no graph allocated");
    }
  }

  private void prepare(Path input, double[] bounds, boolean planet) throws IOException {
    scan(input, element -> {
      if (element instanceof OsmElement.Way way && TrailSegmentLabels.eligible(way)) {
        int previous = -1;
        for (var node : way.nodes()) {
          int current = index(node.value);
          if (previous >= 0 && previous != current) {
            int a = Math.min(previous, current), b = Math.max(previous, current);
            edges.add(((long) a << 32) | (b & 0xffffffffL));
          }
          previous = current;
        }
      }
    });
    int size = nodes.size();
    longitude = new double[size]; latitude = new double[size];
    Arrays.fill(longitude, Double.NaN); Arrays.fill(latitude, Double.NaN);
    boundary = new boolean[size]; invalid = new boolean[size];
    scan(input, element -> {
      if (element instanceof OsmElement.Node node) {
        int i = nodes.getOrDefault(node.id(), -1);
        if (i >= 0) {
          longitude[i] = node.lon(); latitude[i] = node.lat();
          // Complete-way regional extraction does not guarantee topology beyond these bounds.
          invalid[i] = !planet && (node.lon() <= bounds[0] || node.lon() >= bounds[2] ||
            node.lat() <= bounds[1] || node.lat() >= bounds[3]);
        }
      } else if (element instanceof OsmElement.Way way && way.hasTag("highway") &&
        !TrailSegmentLabels.eligible(way)) {
        for (var node : way.nodes()) {
          int i = nodes.getOrDefault(node.value, -1);
          if (i >= 0) boundary[i] = true;
        }
      }
    });
    for (int i = 0; i < size; i++) invalid[i] |= !Double.isFinite(longitude[i]) ||
      !Double.isFinite(latitude[i]) || Math.abs(latitude[i]) >= 85.05112878;
    long[] sorted = edges.toArray(); edges.release(); Arrays.parallelSort(sorted);
    int length = 0;
    for (long edge : sorted) if (length == 0 || sorted[length - 1] != edge) sorted[length++] = edge;
    uniqueEdges = Arrays.copyOf(sorted, length); visited = new boolean[length];
    if (length > (Integer.MAX_VALUE - 8) / 2) throw new IllegalStateException("Graph requires partitioning");
    offsets = new int[size + 1];
    for (long edge : uniqueEdges) { offsets[(int)(edge >>> 32) + 1]++; offsets[(int)edge + 1]++; }
    for (int i = 1; i < offsets.length; i++) offsets[i] += offsets[i - 1];
    adjacency = new int[2 * length]; int[] position = offsets.clone();
    for (int i = 0; i < length; i++) {
      long edge = uniqueEdges[i]; adjacency[position[(int)(edge >>> 32)]++] = i;
      adjacency[position[(int)edge]++] = i;
    }
    System.out.println("Compact graph nodes=" + size + " edges=" + length);
  }

  private boolean end(int vertex) {
    return boundary[vertex] || invalid[vertex] || offsets[vertex + 1] - offsets[vertex] != 2;
  }
  private int other(int edge, int vertex) {
    long value = uniqueEdges[edge]; int a = (int)(value >>> 32), b = (int)value;
    return vertex == a ? b : a;
  }
  private static double worldY(double lat) {
    return (1 - Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2)) / Math.PI) / 2;
  }

  private void walk(int start, int first, BufferedWriter output) throws IOException {
    var chain = new com.carrotsearch.hppc.IntArrayList();
    var lengths = new com.carrotsearch.hppc.DoubleArrayList();
    int vertex = start, edge = first; double total = 0; boolean valid = !invalid[start];
    chain.add(start);
    while (!visited[edge]) {
      visited[edge] = true;
      int next = other(edge, vertex);
      valid &= !invalid[next];
      double distance = GeoUtils.metersBetween(longitude[vertex], latitude[vertex], longitude[next], latitude[next]);
      lengths.add(distance); total += distance; chain.add(next);
      if (end(next) || next == start) break;
      int a = adjacency[offsets[next]], b = adjacency[offsets[next] + 1];
      edge = a == edge ? b : a; vertex = next;
    }
    if (!valid || !Double.isFinite(total) || total < 25) { rejected++; return; }
    double remaining = total / 2;
    for (int i = 0; i < lengths.size(); i++) {
      double distance = lengths.get(i);
      if (distance > 0 && remaining <= distance) {
        int a = chain.get(i), b = chain.get(i + 1);
        double fraction = remaining / distance;
        double x = (longitude[a] + 180) / 360;
        double dx = (longitude[b] - longitude[a]) / 360; dx -= Math.rint(dx);
        double ya = worldY(latitude[a]), dy = worldY(latitude[b]) - ya;
        double anchorX = x + fraction * dx; anchorX -= Math.floor(anchorX);
        double anchorY = ya + fraction * dy;
        double lat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * anchorY))));
        double bearing = Math.toDegrees(Math.atan2(dy, dx));
        if (bearing > 90) bearing -= 180; else if (bearing < -90) bearing += 180;
        long id = ++segments;
        output.write(JSON.writeValueAsString(Map.of("type", "Feature", "id", id,
          "geometry", Map.of("type", "Point", "coordinates", List.of(anchorX * 360 - 180, lat)),
          "properties", Map.of("segment_id", id, "length_m", Math.round(total),
            "bearing", Math.round(bearing), "loop", chain.get(chain.size() - 1) == start ? 1 : 0,
            "policy", POLICY))));
        output.newLine(); return;
      }
      remaining -= distance;
    }
  }

  public static String sha256(Path path) throws Exception {
    var digest = MessageDigest.getInstance("SHA-256");
    try (var input = Files.newInputStream(path)) {
      byte[] buffer = new byte[1024 * 1024]; int length;
      while ((length = input.read(buffer)) >= 0) digest.update(buffer, 0, length);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 4) throw new IllegalArgumentException(
      "input.pbf output.geojsonseq fullPlanetMD5 west,south,east,north|planet");
    long started = System.nanoTime();
    Path input = Path.of(args[0]), output = Path.of(args[1]);
    if (Files.exists(output) || Files.exists(Path.of(args[1] + ".json"))) throw new IllegalArgumentException("Snapshot exists");
    boolean planet = "planet".equals(args[3]);
    double[] bounds = planet ? new double[]{-180, -90, 180, 90} :
      Arrays.stream(args[3].split(",")).mapToDouble(Double::parseDouble).toArray();
    if (bounds.length != 4 || bounds[0] >= bounds[2] || bounds[1] >= bounds[3] ||
      !args[2].matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid provenance/bounds");
    Map<String, Object> parent = new LinkedHashMap<>();
    if (planet) {
      var md5 = MessageDigest.getInstance("MD5");
      var sha256 = MessageDigest.getInstance("SHA-256");
      try (var stream = Files.newInputStream(input)) {
        byte[] buffer = new byte[1024 * 1024]; int count;
        while ((count = stream.read(buffer)) >= 0) {
          md5.update(buffer, 0, count); sha256.update(buffer, 0, count);
        }
      }
      if (!args[2].equals(HexFormat.of().formatHex(md5.digest())))
        throw new IllegalArgumentException("Full planet input MD5 mismatch");
      parent.put("parentPlanetSha256", HexFormat.of().formatHex(sha256.digest()));
      parent.put("parentPlanetBytes", Files.size(input));
      Path highways = output.resolveSibling(output.getFileName() + ".highways.osm.pbf");
      // Retain every highway and referenced node, including non-eligible roads needed at junctions.
      int status = new ProcessBuilder("osmium", "tags-filter", input.toString(), "w/highway",
        "-o", highways.toString()).inheritIO().start().waitFor();
      if (status != 0) throw new IOException("Global highway extraction failed: " + status);
      input = highways;
    }
    var graph = new PrepareTrailSegments();
    if (planet) graph.checkCapacity(input);
    graph.prepare(input, bounds, planet);
    Path partial = Path.of(args[1] + ".partial");
    try (var writer = Files.newBufferedWriter(partial, StandardOpenOption.CREATE_NEW)) {
      for (int v = 0; v < graph.longitude.length; v++) if (graph.end(v)) {
        for (int i = graph.offsets[v]; i < graph.offsets[v + 1]; i++) {
          int edge = graph.adjacency[i]; if (!graph.visited[edge]) graph.walk(v, edge, writer);
        }
      }
      for (int edge = 0; edge < graph.uniqueEdges.length; edge++) if (!graph.visited[edge])
        graph.walk((int)(graph.uniqueEdges[edge] >>> 32), edge, writer);
    }
    Files.move(partial, output, StandardCopyOption.ATOMIC_MOVE);
    var manifest = new LinkedHashMap<String, Object>(Map.of("state", "complete", "policy", POLICY, "planetMd5", args[2],
      "preparationBounds", bounds, "sourceSha256", sha256(input), "sha256", sha256(output),
      "segments", graph.segments, "rejectedChains", graph.rejected,
      "elapsedSeconds", (System.nanoTime() - started) / 1e9));
    manifest.put("scope", planet ? "planet" : "regional");
    manifest.putAll(parent);
    JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[1] + ".json").toFile(), manifest);
    System.out.println(JSON.writeValueAsString(manifest));
  }
}

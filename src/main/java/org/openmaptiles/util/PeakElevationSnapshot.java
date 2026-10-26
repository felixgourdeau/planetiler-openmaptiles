package org.openmaptiles.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmSourceFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Immutable, validated fallback data. Tile generation never contacts the elevation service. */
public final class PeakElevationSnapshot {
  private static final Logger LOGGER = LoggerFactory.getLogger(PeakElevationSnapshot.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Map<Long, Sample> samples;

  private PeakElevationSnapshot(Map<Long, Sample> samples) {
    this.samples = Map.copyOf(samples);
  }

  public static boolean validElevation(Double elevation) {
    return elevation != null && Double.isFinite(elevation) && Math.abs(elevation) < 10_000;
  }

  public static PeakElevationSnapshot load(String filename) {
    if (filename == null) return new PeakElevationSnapshot(Map.of());
    Path path = Path.of(filename);
    if (filename.endsWith(".partial")) throw new IllegalArgumentException("Use a completed elevation snapshot");
    Map<Long, Sample> samples = new HashMap<>();
    Set<Long> ids = new HashSet<>();
    try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
      String line = reader.readLine();
      JsonNode header = line == null ? null : JSON.readTree(line);
      require(header != null && "header".equals(header.path("type").asText()) &&
        header.path("version").asInt() == 1 && "fill-gaps".equals(header.path("policy").asText()) &&
        header.path("inputSha256").asText().matches("[0-9a-f]{64}") &&
        header.path("endpoint").asText().startsWith("https://"), "Invalid snapshot header");
      update(digest, line);
      update(fileDigest, line);
      boolean complete = false;
      long count = 0;
      while ((line = reader.readLine()) != null) {
        require(!complete, "Unexpected data after snapshot footer");
        JsonNode row = JSON.readTree(line);
        require(row != null && row.isObject(), "Invalid snapshot row");
        update(fileDigest, line);
        if ("complete".equals(row.path("type").asText())) {
          require(row.path("count").isIntegralNumber() && row.path("count").asLong() == count &&
            HexFormat.of().formatHex(digest.digest()).equals(row.path("sha256").asText()),
            "Snapshot count/checksum mismatch");
          complete = true;
          continue;
        }
        update(digest, line);
        require("point".equals(row.path("type").asText()) && row.path("id").asText().matches("n[1-9][0-9]*"),
          "Invalid snapshot node ID");
        long id = Long.parseLong(row.path("id").asText().substring(1));
        require(ids.add(id), "Duplicate snapshot node ID");
        String kind = row.path("kind").asText();
        require(Set.of("peak", "saddle", "volcano").contains(kind), "Invalid snapshot feature kind");
        double longitude = coordinate(row, "longitude", 180);
        double latitude = coordinate(row, "latitude", 90);
        JsonNode original = row.get("osmElevation");
        require(original != null && (original.isNull() || original.isTextual()), "Invalid original elevation");
        require(row.path("retrievedAt").isTextual(), "Missing retrieval timestamp");
        JsonNode elevation = row.get("elevation");
        String status = row.path("status").asText();
        require(elevation != null, "Missing elevation result");
        if (!elevation.isNull()) {
          require(elevation.isNumber() && validElevation(elevation.doubleValue()) &&
            "sampled".equals(status) && Math.abs(latitude) <= 85.05112878, "Invalid elevation sample");
          samples.put(id, new Sample(kind, Math.round(longitude * 1e7), Math.round(latitude * 1e7),
            original.isNull() ? null : original.textValue(), elevation.doubleValue()));
        } else {
          require("no_data".equals(status) || "outside_coverage".equals(status), "Invalid no-data result");
        }
        count++;
      }
      require(complete, "Incomplete elevation snapshot");
      LOGGER.info("Elevation snapshot {}: {} usable of {} results; sha256={}; inputSha256={}; endpoint={}",
        path, samples.size(), count, HexFormat.of().formatHex(fileDigest.digest()),
        header.path("inputSha256").asText(), header.path("endpoint").asText());
      return new PeakElevationSnapshot(samples);
    } catch (IOException | NoSuchAlgorithmException exception) {
      throw new IllegalArgumentException("Unable to load elevation snapshot " + path, exception);
    }
  }

  public Double elevation(SourceFeature feature, String originalElevation) {
    if (!(feature instanceof OsmSourceFeature osm) || !(osm.originalElement() instanceof OsmElement.Node node)) {
      return null;
    }
    Sample sample = samples.get(node.id());
    // A moved/reclassified node or changed original height must never reuse stale data.
    if (sample == null || !sample.kind.equals(feature.getString("natural")) ||
      sample.longitudeE7 != Math.round(node.lon() * 1e7) || sample.latitudeE7 != Math.round(node.lat() * 1e7) ||
      !Objects.equals(sample.originalElevation, originalElevation)) return null;
    return sample.elevation;
  }

  private static double coordinate(JsonNode row, String key, double limit) {
    JsonNode value = row.get(key);
    require(value != null && value.isNumber() && Double.isFinite(value.doubleValue()) &&
      Math.abs(value.doubleValue()) <= limit, "Invalid snapshot coordinate");
    return value.doubleValue();
  }

  private static void update(MessageDigest digest, String line) {
    digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }

  private record Sample(String kind, long longitudeE7, long latitudeE7, String originalElevation, double elevation) {}
}

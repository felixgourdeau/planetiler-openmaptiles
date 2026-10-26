package org.openmaptiles;

import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.config.Arguments;
import java.nio.file.Path;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for generating a map using the OpenMapTiles schema.
 */
public class OpenMapTilesMain {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenMapTilesMain.class);

  public static void main(String[] args) throws Exception {
    run(Arguments.fromArgsOrConfigFile(args));
  }

  static void run(Arguments arguments) throws Exception {
    // Parallel node writes: measured on the full planet with 256 GiB RAM and local NVMe.
    arguments = arguments.withDefault("nodemap_type", "array").withDefault("nodemap_storage", "mmap");
    Path dataDir = Path.of("data");
    Path sourcesDir = arguments.file("download_dir", "download directory", dataDir.resolve("sources"));
    // use --area=... argument, AREA=... env var or area=... in config to set the region of the world to use
    // will be ignored if osm_path or osm_url are set
    String area = arguments.getString(
      "area",
      "name of the extract to download if osm_url/osm_path not specified (i.e. 'monaco' 'rhode island' 'australia' or 'planet')",
      "monaco"
    );

    var runner = Planetiler.create(arguments)
      .setDefaultLanguages(OpenMapTilesSchema.LANGUAGES)
      .fetchWikidataNameTranslations(sourcesDir.resolve("wikidata_names.json"))
      // defer creation of the profile because it depends on data from the runner
      .setProfile(OpenMapTilesProfile::new)
      // override any of these with arguments: --osm_path=... or --osm_url=...
      // or OSM_PATH=... OSM_URL=... environmental argument
      // or osm_path=... osm_url=... in a config file
      .addShapefileSource("EPSG:3857", OpenMapTilesProfile.LAKE_CENTERLINE_SOURCE,
        sourcesDir.resolve("lake_centerline.shp.zip"),
        // upstream is at https://github.com/acalcutt/osm-lakelines/releases/download/latest/lake_centerline.shp.zip ,
        // following is same URL as used in the OpenMapTiles (but SHP format), a mirror maintained by MapTiler
        "https://dev.maptiler.download/geodata/omt/lake_centerline.shp.zip")
      .addShapefileSource(OpenMapTilesProfile.WATER_POLYGON_SOURCE,
        sourcesDir.resolve("water-polygons-split-3857.zip"),
        "https://osmdata.openstreetmap.de/download/water-polygons-split-3857.zip")
      .addNaturalEarthSource(OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
        sourcesDir.resolve("natural_earth_vector.sqlite.zip"),
        // upstream is at https://naciscdn.org/naturalearth/packages/natural_earth_vector.sqlite.zip ,
        // following is same URL as used in the OpenMapTiles, a mirror maintained by MapTiler
        "https://dev.maptiler.download/geodata/omt/natural_earth_vector.sqlite.zip")
      .addOsmSource(OpenMapTilesProfile.OSM_SOURCE,
        sourcesDir.resolve(area.replaceAll("[^a-zA-Z]+", "_") + ".osm.pbf"),
        "planet".equalsIgnoreCase(area) ? ("aws:latest") : ("geofabrik:" + area))
      // override with --mbtiles=... argument or MBTILES=... env var or mbtiles=... in a config file
      .setOutput("mbtiles", dataDir.resolve("output.mbtiles"));
    String icelandNames = arguments.getString("iceland_names", "Reviewed Iceland names GeoJSON pilot snapshot", "");
    if (!icelandNames.isBlank()) {
      runner.addGeoJsonSource("iceland_names", Path.of(icelandNames));
    }
    String trailSnapshot = arguments.getString("trail_segment_snapshot", "Prepared segment snapshot", "");
    if (!trailSnapshot.isBlank()) {
      Path snapshot = Path.of(trailSnapshot);
      var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
        Path.of(trailSnapshot + ".json").toFile());
      String expectedMd5 = arguments.getString("trail_snapshot_planet_md5", "Verified full PBF MD5", "");
      if (!"complete".equals(manifest.path("state").asText()) ||
        !org.openmaptiles.tools.PrepareTrailSegments.POLICY.equals(manifest.path("policy").asText()) ||
        expectedMd5.isBlank() || !expectedMd5.equals(manifest.path("planetMd5").asText()) ||
        !org.openmaptiles.tools.PrepareTrailSegments.sha256(snapshot).equals(manifest.path("sha256").asText())) {
        throw new IllegalArgumentException("Trail snapshot provenance/checksum mismatch");
      }
      var bounds = runner.config().bounds().latLon();
      var prepared = manifest.path("preparationBounds");
      boolean planetSnapshot = "planet".equals(manifest.path("scope").asText());
      if (planetSnapshot && (!manifest.path("parentPlanetSha256").asText().matches("[a-f0-9]{64}") ||
        manifest.path("parentPlanetBytes").asLong() <= 0 || prepared.size() != 4 ||
        prepared.get(0).asDouble() != -180 || prepared.get(1).asDouble() != -90 ||
        prepared.get(2).asDouble() != 180 || prepared.get(3).asDouble() != 90)) {
        throw new IllegalArgumentException("Global trail snapshot provenance/coverage mismatch");
      }
      if (!planetSnapshot && (prepared.size() != 4 || bounds.getMinX() <= prepared.get(0).asDouble() ||
        bounds.getMinY() <= prepared.get(1).asDouble() || bounds.getMaxX() >= prepared.get(2).asDouble() ||
        bounds.getMaxY() >= prepared.get(3).asDouble())) {
        throw new IllegalArgumentException("Output bounds must be inside trail preparation bounds");
      }
      runner.addGeoJsonSource(org.openmaptiles.addons.TrailSegmentSnapshot.SOURCE, snapshot);
    }
    runner.run();

    LOGGER.info("""
      Acknowledgments
      Generated vector tiles are produced work of OpenStreetMap data.
      Such tiles are reusable under CC-BY license granted by OpenMapTiles team:
      - https://github.com/openmaptiles/openmaptiles/#license
      Maps made with these vector tiles must display a visible credit:
      - © OpenMapTiles © OpenStreetMap contributors
      Thanks to all free, open source software developers and Open Data Contributors!
      """);
  }
}

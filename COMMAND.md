https://docs.protomaps.com/basemaps/build

Production publishing: follow [docs/planet-releases.md](docs/planet-releases.md).
Every rebuild needs a new `planet-YYYYMMDD-rN.pmtiles` object and matching client URL.
The commands and logs below are historical build notes, not production upload instructions.

Pour build les adirondacks:

./mvnw -q -DskipTests clean package

java -Xmx4g -jar target/*with-deps.jar \
  --osm-path=/Users/felixgourdeau/repos/adks.osm.pbf \
  --output=adk2.pmtiles \
  --download --force

  

Pour builder la planet au complet:

./mvnw -q -DskipTests clean package

java -Xmx24g -XX:MaxHeapFreeRatio=40 \
  -jar ~/repos/planetiler-openmaptiles/target/planetiler-openmaptiles-3.15.1-SNAPSHOT-with-deps.jar \
  --osm-path=/Users/felixgourdeau/repos/planet-250825.osm.pbf \
  --output=/Users/felixgourdeau/repos/temp/planet.pmtiles \
  --storage=mmap \
  --download --force




java -Xmx24g -XX:MaxHeapFreeRatio=40 \
  -Djava.io.tmpdir=/Volumes/MyPassport/planet-run/tmp \
  -jar ~/repos/planetiler-openmaptiles/target/*with-deps.jar \
  --tmpdir=/Volumes/MyPassport/planet-run/tmp \
  --osm-path=/Users/felixgourdeau/repos/temp/planet-250728.osm.pbf \
  --output=/Volumes/MyPassport/planet-run/planet.pmtiles \
  --storage=mmap \
  --download --force





  75:08:39 INF [archive] -  features: [ 4.2B 100% 346k/s ] 317G  tiles: [ 268M 299k/s ] 89G  
    cpus: 1.5 gc:  0% heap: 3.6G/25G direct: 5.7M postGC: 3.1G
    read( 3%) ->  (0/1.3k) -> encode( 9% 13%  9% 10% 10% 10%  9% 11% 11% 12% 10%) ->  (6/1.2k) -> write( 6%)
    last tile: 14/15019/3583 (z14 98%) https://onthegomap.github.io/planetiler-demo/#14.5/70.61626/150.01831
75:08:49 INF [archive] -  features: [ 4.2B 100% 548k/s ] 317G  tiles: [ 273M 512k/s ] 89G  
    cpus: 1 gc:  0% heap: 3.4G/25G direct: 5.7M postGC: 2.7G
    read( 5%) ->  (0/1.3k) -> encode( 6%  5%  6%  6%  6%  7%  7%  5%  6%  6%  6%) ->  (5/1.2k) -> write( 3%)
    last tile: 14/15204/43 (z14 100%) https://onthegomap.github.io/planetiler-demo/#14.5/84.96799/154.08325
75:08:50 INF [archive:write] - Finished z14 in 6h22m1s cpu:1h30m54s gc:17s avg:0.2
75:08:50 INF [archive:write] - Building directories with 14346 entries per leaf, attempt 1...
75:08:55 INF [archive:write] - Built directories with 3501 leaves, 16341B root directory
75:08:55 INF [archive:write] - Building directories with 17215 entries per leaf, attempt 2...
75:08:59 INF [archive] -  features: [ 4.2B 100% 109k/s ] 317G  tiles: [ 274M 106k/s ] 89G  
    cpus: 1.2 gc:  0% heap: 4.3G/25G direct: 5.7M postGC: 2.7G
    read( -%) ->  (0/1.3k) -> encode( -%  -%  -%  -%  -%  -%  -%  -%  -%  -%  -%) ->  (0/1.2k) -> write(84%)
    last tile: 14/16383/0 (z14 100%) https://onthegomap.github.io/planetiler-demo/#14.5/85.05018/179.98901
75:09:00 INF [archive:write] - Built directories with 2917 leaves, 13969B root directory
75:09:00 INF [archive:write] - Writing metadata and leaf directories...
75:09:04 INF [archive:write] - Writing header...
75:09:04 INF [archive:write] - # addressed tiles: 274666350
75:09:04 INF [archive:write] - # of tile entries: 50212075
75:09:04 INF [archive:write] - # of tile contents: 44216817
75:09:04 INF [archive:write] - Root directory: 13kB
75:09:04 INF [archive:write] - # leaves: 2917
75:09:04 INF [archive:write] - Leaf directories: 94MB
75:09:04 INF [archive:write] - Avg leaf size: 32kB
75:09:04 INF [archive:write] - Total dir bytes: 94MB
75:09:04 INF [archive:write] - Average bytes per addressed tile: 0.3428532071729937
75:09:04 INF [archive] -  features: [ 4.2B 100%    0/s ] 317G  tiles: [ 274M    0/s ] 89G  
    cpus: 0.4 gc:  0% heap: 2.8G/25G direct: 3.3M postGC: 2.9G
    read( -%) ->  (0/1.3k) -> encode( -%  -%  -%  -%  -%  -%  -%  -%  -%  -%  -%) ->  (0/1.2k) -> write( -%)
    last tile: 14/16383/0 (z14 100%) https://onthegomap.github.io/planetiler-demo/#14.5/85.05018/179.98901
75:09:04 DEB [archive] - Tile stats:
75:09:04 DEB [archive] - Biggest tiles (gzipped)
1. 12/2074/1409 (2.3M) https://onthegomap.github.io/planetiler-demo/#12.5/48.83579/2.3291 (poi:4.6M)
2. 12/3638/1612 (2.2M) https://onthegomap.github.io/planetiler-demo/#12.5/35.71083/139.79004 (poi:4.4M)
3. 12/3637/1612 (2.1M) https://onthegomap.github.io/planetiler-demo/#12.5/35.71083/139.70215 (poi:4.2M)
4. 12/3430/1753 (2M) https://onthegomap.github.io/planetiler-demo/#12.5/25.04579/121.50879 (poi:4.1M)
5. 12/2074/1408 (2M) https://onthegomap.github.io/planetiler-demo/#12.5/48.89361/2.3291 (poi:3.9M)
6. 12/3589/1626 (1.7M) https://onthegomap.github.io/planetiler-demo/#12.5/34.70549/135.4834 (poi:3.4M)
7. 12/3637/1613 (1.6M) https://onthegomap.github.io/planetiler-demo/#12.5/35.63943/139.70215 (poi:3.1M)
8. 12/2200/1343 (1.5M) https://onthegomap.github.io/planetiler-demo/#12.5/52.50953/13.40332 (poi:3.1M)
9. 12/2072/1529 (1.5M) https://onthegomap.github.io/planetiler-demo/#12.5/41.40977/2.15332 (poi:2.7M)
10. 12/2046/1361 (1.4M) https://onthegomap.github.io/planetiler-demo/#12.5/51.53608/-0.13184 (poi:2.6M)
75:09:04 INF [archive] - Other tiles with large layers
14/6435/8361 (708k) https://onthegomap.github.io/planetiler-demo/#14.5/-3.72175/-38.59497 (building:708k)
14/8414/5384 (316k) https://onthegomap.github.io/planetiler-demo/#14.5/52.36889/4.88892 (housenumber:316k)
14/8094/5296 (352k) https://onthegomap.github.io/planetiler-demo/#14.5/53.53378/-2.14233 (poi:445k)
9/255/170 (119k) https://onthegomap.github.io/planetiler-demo/#9.5/51.39868/-0.35156 (landuse:119k)
10/199/394 (1M) https://onthegomap.github.io/planetiler-demo/#10.5/38.13443/-109.86328 (mountain_peak:1M)
5/5/11 (141k) https://onthegomap.github.io/planetiler-demo/#5.5/44.9512/-118.125 (park:141k)
3/5/3 (603k) https://onthegomap.github.io/planetiler-demo/#3.5/20.48995/67.5 (place:603k)
10/518/352 (461k) https://onthegomap.github.io/planetiler-demo/#10.5/48.80673/2.28516 (transportation:461k)
14/9618/6751 (178k) https://onthegomap.github.io/planetiler-demo/#14.5/30.15463/31.34399 (transportation_name:178k)
8/32/60 (170k) https://onthegomap.github.io/planetiler-demo/#8.5/68.3977/-134.29688 (water:170k)
75:09:04 DEB [archive] - Max tile sizes
                      z0    z1    z2    z3    z4    z5    z6    z7    z8    z9   z10   z11   z12   z13   z14   all
           boundary 4.6k   32k   38k   22k   20k   35k   29k   26k   23k   33k   35k   30k   27k   21k   17k   38k
          landcover 1.5k   990  8.1k  4.6k  3.2k   31k   17k  270k  334k  235k  153k  175k  166k  111k  352k  352k
              place  53k  146k  570k  603k  384k  205k  124k   67k   47k   59k   74k   69k   50k  123k  223k  603k
              water 8.7k  4.3k   11k  9.9k   17k   13k  130k  152k  170k  125k  132k   94k  167k  102k   91k  170k
         water_name 4.4k   16k   25k   15k   14k  7.9k  5.1k  5.3k   12k  8.5k  5.4k  3.8k  3.2k  9.6k   29k   29k
           waterway    0     0     0   549  3.7k  1.6k   16k   12k  9.3k   28k   19k   15k   59k   75k   88k   88k
            landuse    0     0     0     0  2.6k  3.6k   55k   79k  116k  119k  103k  116k   55k  115k   60k  119k
               park    0     0     0     0   58k  141k   96k   77k   72k   82k   90k   55k   47k   19k   50k  141k
     transportation    0     0     0     0   60k   47k   53k   73k   61k   90k  461k  299k  384k  216k  117k  461k
transportation_name    0     0     0     0     0     0   34k   19k   18k   15k   36k   23k   38k   28k  178k  178k
      mountain_peak    0     0     0     0     0     0     0   12k   13k  913k    1M  843k  623k  337k  234k    1M
    aerodrome_label    0     0     0     0     0     0     0     0  4.2k  3.4k  4.3k  4.1k  3.9k  3.9k  3.7k  4.3k
            aeroway    0     0     0     0     0     0     0     0     0     0   15k   23k   25k   24k   17k   25k
                poi    0     0     0     0     0     0     0     0     0     0     0   180  4.6M  2.3M    1M  4.6M
           building    0     0     0     0     0     0     0     0     0     0     0     0     0  141k  708k  708k
        housenumber    0     0     0     0     0     0     0     0     0     0     0     0     0     0  316k  316k
          full tile  72k  197k  647k  637k  449k  371k  314k  400k  374k  941k    1M  850k    5M  2.6M  1.4M    5M
            gzipped  44k  114k  342k  338k  258k  209k  206k  287k  271k  562k  643k  518k  2.3M  1.3M  799k  2.3M
75:09:04 DEB [archive] -    Max tile: 5M (gzipped: 2.3M)
75:09:04 DEB [archive] -    Avg tile: 160k (gzipped: 97k) using weighted average based on OSM traffic
75:09:04 DEB [archive] -     # tiles: 274,666,350
75:09:04 DEB [archive] -  # features: 4,274,262,395
75:09:04 INF [archive] - Finished in 19h52m44s cpu:11h20m12s gc:2m8s avg:0.6
75:09:04 INF [archive] -   read    1x(1% 12m13s wait:1m3s done:14s)
75:09:04 INF [archive] -   write   1x(0% 1m36s wait:19h11m23s done:14s)
75:09:04 INF [archive] -   encode 11x(5% 57m20s wait:18h43m56s done:14s)
75:09:04 INF [archive] - Finished in 75h9m5s cpu:29h18m59s gc:9m41s avg:0.4
75:09:04 INF [archive] - FINISHED!
75:09:04 INF [archive] - 
75:09:04 INF [archive] - ----------------------------------------
75:09:04 INF [archive] - data errors:
75:09:04 INF [archive] - 	render_snap_fix_input	24,562,825
75:09:04 INF [archive] - 	merge_snap_fix_input	23,127
75:09:04 INF [archive] - 	omt_fix_water_before_ne_intersect	1,934
75:09:04 INF [archive] - 	feature_polygon_osm_invalid_multipolygon_empty_after_fix	688
75:09:04 INF [archive] - 	osm_multipolygon_duplicate_member	369
75:09:04 INF [archive] - 	feature_centroid_if_convex_osm_invalid_multipolygon_empty_after_fix	68
75:09:04 INF [archive] - 	omt_park_area_osm_invalid_multipolygon_empty_after_fix	27
75:09:04 INF [archive] - 	osm_boundary_duplicate_member	15
75:09:04 INF [archive] - 	omt_boundary_poly_osm_invalid_multipolygon_empty_after_fix	8
75:09:04 INF [archive] - 	feature_centroid_osm_invalid_multipolygon_empty_after_fix	3
75:09:04 INF [archive] - 	feature_point_on_surface_osm_invalid_multipolygon_empty_after_fix	2
75:09:04 INF [archive] - 	render_snap_fix_input2	2
75:09:04 INF [archive] - 	omt_water_osm_invalid_multipolygon_empty_after_fix	2
75:09:04 INF [archive] - 	render_snap_fix_input3	1
75:09:04 INF [archive] - ----------------------------------------
75:09:04 INF [archive] - 	overall          75h9m5s cpu:29h18m59s gc:9m41s avg:0.4
75:09:04 INF [archive] - 	download         2m26s cpu:10s avg:0.1
75:09:04 INF [archive] - 	lake_centerlines 5s cpu:4s avg:0.9
75:09:04 INF [archive] - 	  read     1x(15% 0.7s done:4s)
75:09:04 INF [archive] - 	  process 11x(1% 0s done:4s)
75:09:04 INF [archive] - 	  write    1x(0% 0s done:4s)
75:09:04 INF [archive] - 	water_polygons   6m11s cpu:13m36s gc:3s avg:2.2
75:09:04 INF [archive] - 	  read     1x(3% 10s wait:4m43s done:1m15s)
75:09:04 INF [archive] - 	  process 11x(18% 1m8s wait:4m4s done:50s)
75:09:04 INF [archive] - 	  write    1x(4% 16s wait:1m3s done:26s)
75:09:04 INF [archive] - 	natural_earth    1m45s cpu:19s avg:0.2
75:09:04 INF [archive] - 	  read     1x(7% 7s done:55s)
75:09:04 INF [archive] - 	  process 11x(0% 0.4s wait:50s done:55s)
75:09:04 INF [archive] - 	  write    1x(0% 0.1s wait:48s done:55s)
75:09:04 INF [archive] - 	osm_pass1        47m35s cpu:1h17m7s gc:2m4s avg:1.6
75:09:04 INF [archive] - 	  read     1x(0% 4s wait:47m)
75:09:04 INF [archive] - 	  parse   10x(10% 4m46s wait:39m30s)
75:09:04 INF [archive] - 	  process  1x(15% 7m19s wait:3m33s)
75:09:04 INF [archive] - 	osm_pass2        48h55m30s cpu:15h30m38s gc:4m16s avg:0.3
75:09:04 INF [archive] - 	  read     1x(0% 5s wait:48h48m26s done:6m37s)
75:09:04 INF [archive] - 	  process 11x(2% 1h9m52s block:7s wait:24m11s done:2s)
75:09:04 INF [archive] - 	  write    1x(0% 12m34s wait:47h49m31s)
75:09:04 INF [archive] - 	ne_lakes         0.3s cpu:0.3s avg:1.2
75:09:04 INF [archive] - 	boundaries       7s cpu:12s avg:1.6
75:09:04 INF [archive] - 	agg_stop         0.3s cpu:0.4s avg:1.3
75:09:04 INF [archive] - 	sort             5h22m24s cpu:56m39s gc:1m10s avg:0.2
75:09:04 INF [archive] - 	  worker  4x(1% 2m27s wait:54s done:52s)
75:09:04 INF [archive] - 	archive          19h52m44s cpu:11h20m12s gc:2m8s avg:0.6
75:09:04 INF [archive] - 	  read    1x(1% 12m13s wait:1m3s done:14s)
75:09:04 INF [archive] - 	  write   1x(0% 1m36s wait:19h11m23s done:14s)
75:09:04 INF [archive] - 	  encode 11x(5% 57m20s wait:18h43m56s done:14s)
75:09:04 INF [archive] - ----------------------------------------
75:09:04 INF [archive] - 	archive	89GB
75:09:04 INF [archive] - 	features	317GB
75:09:04 INF [archive] - Acknowledgments
Generated vector tiles are produced work of OpenStreetMap data.
Such tiles are reusable under CC-BY license granted by OpenMapTiles team:
- https://github.com/openmaptiles/openmaptiles/#license
Maps made with these vector tiles must display a visible credit:
- © OpenMapTiles © OpenStreetMap contributors
Thanks to all free, open source software developers and Open Data Contributors!

# Outdoor POI inventory — 2026-10-25

Historical baseline before the outdoor-priority changes. The priority scores and missing-match findings below are superseded by [the implemented priority rules and audit](poi-audit-2026-10-25/README.md). This table retains the original mapping inventory for comparison. Includes all mapped sport/leisure/tourism/barrier/natural/waterway/landuse/historic values (some are urban or indoor), plus explicitly listed outdoor-support amenities, shops and offices. This broad definition avoids hiding marginal outdoor categories. Multi-tag objects can match another mapping first.

Priority is the class sort score: lower wins; ordinary unnamed features add 2000. It is NOT the tile `rank`, which is assigned per grid at each zoom. Client display is z12 for ranks 1–6, z16 for 7–19, z17 for >=20, subject to collisions and available data. A single rank cannot be assigned to a POI type.

| OSM tag | Emitted class | Named priority | Tile minzoom |
| --- | --- | ---: | ---: |
| `aerialway=station` | `aerialway` | 1000 | 12 |
| `amenity=bbq` | `park` | 120 | 12 |
| `amenity=bicycle_parking` | `bicycle_parking` | 1000 | 12 |
| `amenity=bicycle_rental` | `bicycle_rental` | 1000 | 12 |
| `amenity=drinking_water` | `drinking_water` | 1000 | 12 |
| `amenity=ferry_terminal` | `ferry_terminal` | 1000 | 12 |
| `amenity=fuel` | `fuel` | 1000 | 12 |
| `amenity=motorcycle_parking` | `motorcycle_parking` | 1000 | 12 |
| `amenity=parking` | `parking` | 1000 | 12 |
| `amenity=recycling` | `recycling` | 1000 | 12 |
| `amenity=shelter` | `shelter` | 1000 | 12 |
| `amenity=swimming_pool` | `swimming_pool` | 1000 | 12 |
| `amenity=telephone` | `telephone` | 1000 | 12 |
| `amenity=toilets` | `toilets` | 1000 | 12 |
| `amenity=waste_basket` | `waste_basket` | 1000 | 12 |
| `barrier=bollard` | `bollard` | 1000 | 12 |
| `barrier=border_control` | `border_control` | 1000 | 12 |
| `barrier=cycle_barrier` | `cycle_barrier` | 1000 | 12 |
| `barrier=gate` | `gate` | 1000 | 12 |
| `barrier=lift_gate` | `lift_gate` | 1000 | 12 |
| `barrier=sally_port` | `sally_port` | 1000 | 12 |
| `barrier=stile` | `stile` | 1000 | 12 |
| `barrier=toll_booth` | `toll_booth` | 1000 | 12 |
| `historic=monument` | `monument` | 1000 | 12 |
| `historic=castle` | `castle` | 1000 | 12 |
| `historic=ruins` | `castle` | 1000 | 12 |
| `landuse=basin` | `basin` | 1000 | 12 |
| `landuse=brownfield` | `brownfield` | 1000 | 12 |
| `landuse=cemetery` | `cemetery` | 115 | 12 |
| `landuse=reservoir` | `reservoir` | 1000 | 12 |
| `landuse=winter_sports` | `winter_sports` | 1000 | 12 |
| `leisure=dog_park` | `dog_park` | 1000 | 12 |
| `leisure=escape_game` | `escape_game` | 1000 | 12 |
| `leisure=garden` | `garden` | 1000 | 12 |
| `leisure=golf_course` | `golf` | 150 | 12 |
| `leisure=ice_rink` | `ice_rink` | 1000 | 12 |
| `leisure=hackerspace` | `hackerspace` | 1000 | 12 |
| `leisure=marina` | `harbor` | 75 | 12 |
| `leisure=miniature_golf` | `golf` | 150 | 12 |
| `leisure=park` | `park` | 120 | 12 |
| `leisure=pitch` | `pitch` | 1000 | 12 |
| `leisure=playground` | `playground` | 1000 | 12 |
| `leisure=sports_centre` | `sports_centre` | 1000 | 12 |
| `leisure=stadium` | `stadium` | 90 | 12 |
| `leisure=swimming_area` | `swimming` | 1000 | 12 |
| `leisure=swimming_pool` | `swimming_pool` | 1000 | 12 |
| `leisure=water_park` | `water_park` | 1000 | 12 |
| `office=forestry` | `office` | 1000 | 12 |
| `office=guide` | `office` | 1000 | 12 |
| `office=harbour_master` | `office` | 1000 | 12 |
| `shop=bicycle` | `bicycle` | 1000 | 12 |
| `shop=convenience` | `shop` | 400 | 12 |
| `shop=general` | `shop` | 400 | 12 |
| `shop=hardware` | `shop` | 400 | 12 |
| `shop=outdoor` | `shop` | 400 | 12 |
| `shop=sports` | `shop` | 400 | 12 |
| `shop=supermarket` | `grocery` | 500 | 12 |
| `sport=american_football` | `stadium` | 90 | 12 |
| `sport=archery` | `archery` | 1000 | 12 |
| `sport=athletics` | `athletics` | 1000 | 12 |
| `sport=australian_football` | `australian_football` | 1000 | 12 |
| `sport=badminton` | `badminton` | 1000 | 12 |
| `sport=baseball` | `baseball` | 1000 | 12 |
| `sport=basketball` | `basketball` | 1000 | 12 |
| `sport=beachvolleyball` | `beachvolleyball` | 1000 | 12 |
| `sport=billiards` | `billiards` | 1000 | 12 |
| `sport=bmx` | `bmx` | 1000 | 12 |
| `sport=boules` | `boules` | 1000 | 12 |
| `sport=bowls` | `bowls` | 1000 | 12 |
| `sport=boxing` | `boxing` | 1000 | 12 |
| `sport=canadian_football` | `canadian_football` | 1000 | 12 |
| `sport=canoe` | `canoe` | 1000 | 12 |
| `sport=chess` | `chess` | 1000 | 12 |
| `sport=climbing` | `climbing` | 1000 | 12 |
| `sport=climbing_adventure` | `climbing_adventure` | 1000 | 12 |
| `sport=cricket` | `cricket` | 1000 | 12 |
| `sport=cricket_nets` | `cricket_nets` | 1000 | 12 |
| `sport=croquet` | `croquet` | 1000 | 12 |
| `sport=curling` | `curling` | 1000 | 12 |
| `sport=cycling` | `cycling` | 1000 | 12 |
| `sport=disc_golf` | `disc_golf` | 1000 | 12 |
| `sport=diving` | `diving` | 1000 | 12 |
| `sport=dog_racing` | `dog_racing` | 1000 | 12 |
| `sport=equestrian` | `equestrian` | 1000 | 12 |
| `sport=fatsal` | `fatsal` | 1000 | 12 |
| `sport=field_hockey` | `field_hockey` | 1000 | 12 |
| `sport=free_flying` | `free_flying` | 1000 | 12 |
| `sport=gaelic_games` | `gaelic_games` | 1000 | 12 |
| `sport=golf` | `golf` | 150 | 12 |
| `sport=gymnastics` | `gymnastics` | 1000 | 12 |
| `sport=handball` | `handball` | 1000 | 12 |
| `sport=hockey` | `hockey` | 1000 | 12 |
| `sport=horse_racing` | `horse_racing` | 1000 | 12 |
| `sport=horseshoes` | `horseshoes` | 1000 | 12 |
| `sport=ice_hockey` | `ice_hockey` | 1000 | 12 |
| `sport=ice_stock` | `ice_stock` | 1000 | 12 |
| `sport=judo` | `judo` | 1000 | 12 |
| `sport=karting` | `karting` | 1000 | 12 |
| `sport=korfball` | `korfball` | 1000 | 12 |
| `sport=long_jump` | `long_jump` | 1000 | 12 |
| `sport=model_aerodrome` | `model_aerodrome` | 1000 | 12 |
| `sport=motocross` | `motocross` | 1000 | 12 |
| `sport=motor` | `motor` | 1000 | 12 |
| `sport=multi` | `multi` | 1000 | 12 |
| `sport=netball` | `netball` | 1000 | 12 |
| `sport=orienteering` | `orienteering` | 1000 | 12 |
| `sport=paddle_tennis` | `paddle_tennis` | 1000 | 12 |
| `sport=paintball` | `paintball` | 1000 | 12 |
| `sport=paragliding` | `paragliding` | 1000 | 12 |
| `sport=pelota` | `pelota` | 1000 | 12 |
| `sport=racquet` | `racquet` | 1000 | 12 |
| `sport=rc_car` | `rc_car` | 1000 | 12 |
| `sport=rowing` | `rowing` | 1000 | 12 |
| `sport=rugby` | `rugby` | 1000 | 12 |
| `sport=rugby_league` | `rugby_league` | 1000 | 12 |
| `sport=rugby_union` | `rugby_union` | 1000 | 12 |
| `sport=running` | `running` | 1000 | 12 |
| `sport=sailing` | `sailing` | 1000 | 12 |
| `sport=scuba_diving` | `scuba_diving` | 1000 | 12 |
| `sport=shooting` | `shooting` | 1000 | 12 |
| `sport=shooting_range` | `shooting_range` | 1000 | 12 |
| `sport=skateboard` | `skateboard` | 1000 | 12 |
| `sport=skating` | `skating` | 1000 | 12 |
| `sport=skiing` | `skiing` | 1000 | 12 |
| `sport=soccer` | `stadium` | 90 | 12 |
| `sport=surfing` | `surfing` | 1000 | 12 |
| `sport=swimming` | `swimming` | 1000 | 12 |
| `sport=table_soccer` | `table_soccer` | 1000 | 12 |
| `sport=table_tennis` | `table_tennis` | 1000 | 12 |
| `sport=team_handball` | `team_handball` | 1000 | 12 |
| `sport=tennis` | `tennis` | 1000 | 12 |
| `sport=toboggan` | `toboggan` | 1000 | 12 |
| `sport=volleyball` | `volleyball` | 1000 | 12 |
| `sport=water_ski` | `water_ski` | 1000 | 12 |
| `sport=yoga` | `yoga` | 1000 | 12 |
| `natural=stone` | `attraction` | Custom path; no class sort/grid setup | 12 |
| `waterway=waterfall` | `waterfall` | Custom path; no class sort/grid setup | 12 |
| `tourism=alpine_hut` | `lodging` | 1000 | 12 |
| `tourism=aquarium` | `aquarium` | 1000 | 12 |
| `tourism=artwork` | `art_gallery` | 1000 | 12 |
| `tourism=attraction` | `attraction` | 70 | 12 |
| `tourism=bed_and_breakfast` | `lodging` | 1000 | 12 |
| `tourism=camp_site` | `campsite` | 110 | 12 |
| `tourism=camp_pitch` | `campsite` | 110 | 14 |
| `tourism=caravan_site` | `campsite` | 110 | 12 |
| `tourism=chalet` | `lodging` | 1000 | 12 |
| `tourism=gallery` | `art_gallery` | 1000 | 12 |
| `tourism=guest_house` | `lodging` | 1000 | 12 |
| `tourism=hostel` | `lodging` | 1000 | 12 |
| `tourism=hotel` | `lodging` | 1000 | 12 |
| `tourism=information` | `information` | 1000 | 12 |
| `tourism=motel` | `lodging` | 1000 | 12 |
| `tourism=museum` | `museum` | 1000 | 12 |
| `tourism=picnic_site` | `picnic_site` | 1000 | 12 |
| `tourism=theme_park` | `theme_park` | 1000 | 12 |
| `tourism=viewpoint` | `attraction` | 70 | 12 |
| `tourism=zoo` | `zoo` | 95 | 12 |
| `waterway=dock` | `harbor` | 75 | 12 |

## Important exceptions and missing coverage

- `camp_pitch` maps to `campsite`: effective priority **110**, not the unused `camp_pitch` priority 111.
- Information POIs retain class `information`; output subclass comes from `information=*` (for example guidepost/map/board).
- Stones and waterfalls bypass the common priority/grid setup; post-processing still assigns `rank`, but they do not follow the ordinary class-priority policy.
- Natural stone and waterfall are mapped as points here, not as standalone polygon POIs.
- `natural=spring`, `natural=cave_entrance`, `highway=trailhead`, `tourism=wilderness_hut`, `amenity=water_point`, `amenity=bench`, `leisure=picnic_table`, `emergency=phone` and `amenity=bicycle_repair_station` are not standalone matches in these POI mappings. Other tags on the same feature may still admit it. Springs may participate in other layers; absence here is not absence everywhere.
- Peaks/saddles use `mountain_peak`, not `poi`: peaks begin z7, saddles z11; current saddle styles start z14. They use a separate name/Wikipedia/elevation sort and grid ranking.

158 source tag rows. Sources: `generated/Tables.java`, `generated/OpenMapTilesSchema.java`, `layers/Poi.java`, and the inspected web/mobile v7 styles.

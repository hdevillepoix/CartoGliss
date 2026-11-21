# CartoGliss

eventually in this brand new repo I want to make an Android app. I want to plan it fully first. App's main purpose is to cache and display raster maps (from various
  server), show the user's location and facing azimuth. let's plan. you are going to write every decision in a markdown file.

functionalities
- display a map using tile sources servers (main map screen).
- display one or more simple KMLs



## the main screen: map
  
  zooming: unlike most modern phone map apps I don't want the zoom to be continuous.
  Each zoom level should correspond to the map server's zoom level.
  if it's not loaded, don't try to infer what the raster looks like from another zoom level.
  Plus an Minus icons will be used in the top right of the map area.
  The zoom level will be displayed in a small rectangle between the zoom icons, and displayed in the form NxM.
  With N being the zoom level of the map currently displayed, and M being the overzoom factor
  This rectangle will be a button (red if activated), and when activated the plus and minus buttons act only as overzoom.
A target GPS button will center the map on current user device location.

> **Decisions (zoom):**
> - The map is always north-up. It never rotates.
> - A tile that isn't loaded is drawn as a neutral placeholder (light grey with a fine grid). It is never filled from another zoom level.
> - Overzoom factor M ∈ {1, 2, 4, 8} and is **manual only**: it changes only while the NxM toggle is red. In normal mode + stops at the source's max zoom.
> - Overzoom scales up the tiles of level N. No other level is fetched.
> - Pinch: once the pinch passes a threshold, exactly ±1 step is applied on release (zoom level, or overzoom when red mode is on). Nothing between levels is ever drawn. Double-tap = +1 step.
> - Tile size: each source declares 256 or 512 px. A tile is always drawn at 256 dp × M, so 512 px tiles act as high-DPI tiles.
  
panning: panning is precise: no inertia effect.
if users want to pan faster, they have to zoom out.

> **Decision:** dragging moves the map 1:1 with the finger. There is no fling.

GPS point: blue if there is a recent fix, grey if not.
a green dashed line links the position to the target point.
direction the device is facing is indicated by a view cone of +- 20 degrees (stop this display if phone is too vertical).
direction of movement is indicated by an arrow (triangle with no fill).

> **Decisions (overlays):**
> - "Recent fix" means the last fix is under 10 s old (a constant). Accuracy is drawn as a faint translucent disc.
> - Heading comes from `TYPE_ROTATION_VECTOR`, is corrected to true north with `GeomagneticField` and lightly smoothed. The cone is hidden when the device is tilted more than 60° from flat.
> - The movement arrow comes from `Location.bearing` and shows only when speed is above 0.5 m/s and the fix is recent.
> - Target: set from Pin → "Go To", or by tapping a waypoint (in Browse or on the map). It **persists across restarts**. Long-press on the target status clears it.

A scale is in the bottom right of the map. it is always the same size on the screen, but the number it corresponds to changes.

> **Decision:** the scale bar is a fixed 2 cm on screen. Its label is the ground distance at the centre latitude, rounded to 2 significant figures, in the chosen distance unit.

The center of the map is indicated by a thin black crosshair.

## state bar (bottom)
#### toggle map view/stats view
#### Target status
- Distance to target.
- Name of target (if from KML).

#### Pin icon
This triggers a modal popup, and serves for setting something about the center of the map: display how far and the bearing (both angle and ESE notation), and the coordinates (lat/long); "Go To" (set as new target), "Add waypoint".

> **Decision:** the bearing is true bearing in degrees plus a 16-point compass name (N, NNE, NE … ESE …).
#### GPS satellites
Indication of how many. Hitting this icon disables/enables GPS.

> **Decision:** location comes from the `LocationManager` GPS provider (no Google Play Services). `GnssStatus.Callback` gives the satellite count, shown as used/visible. The toggle stops and starts location updates.
#### Record button
Turns red when recording a track, and displays how far and how long the recorded track is so far.

> **Decision:** recording runs in a foreground service with a persistent notification, so it keeps going with the screen off. This needs background location permission.
#### Icon to access the menu


## Stats view
Coordinates (inc. altitude).
Last GPS fix.
Speed.
Sunrise/sunset time, 
Moonrise/Moonset.
UV index (find a formula that calculates it just from sun elevation angle, and altitude).
At what time sun will be next pass UV index=3.

> **Decisions (stats):**
> - Sun and moon rise/set and positions come from `commons-suncalc` (Apache-2.0).
> - UV index uses a clear-sky model: `UVI = 12.5 · cos(SZA)^2.42 · (1 + 0.10 · alt_km)`. This is Madronich's approximation with total ozone fixed at 300 DU, plus the WHO rule of +10 % per 1000 m. SZA is the solar zenith angle (90° − sun elevation); UVI = 0 when the sun is below the horizon.
> - "Next UVI = 3 crossing": step the sun position forward in 1-minute increments to find the next crossing (rising or falling), then refine by bisection. If there is none before the next sunset, show "—".



  
## Menu

### Maps
#### Download maps
This opens a special view of the map, where users can paint which areas they can cached.
The current view is split into small squares, and the user can either paint or draw a rectangle on the area to be downloaded (selected area will be slightly yellowed in transparency).
The zoom levels (1-19) are also display on the left, each can be selected/unselected, defining
The system makes an estimate of the number of tiles.
And of course a download button.

> **Decisions (download):**
> - Every zoom level the source offers (e.g. 1–19) appears as its own toggle on the left. Only the toggled-on levels are downloaded.
> - The painted squares only mark the **area** to download; they don't depend on which levels are toggled. Each square is a tile of the level 2 deeper than the zoom you're viewing at, so about 64 dp on screen: small enough to paint precisely. The squares resize if you zoom while in this view.
> - For each toggled-on level, every tile that touches the painted area is downloaded. The estimate is the sum of tile counts across those levels, with an approximate size in MB.
> - Downloads run as a WorkManager job with progress, per-source concurrency of 2, and an identifying User-Agent (`CartoGliss/<version>`).
> - **Bulk download is disabled for OSM Standard** (OSMF tile usage policy). Viewing it still caches tiles.
#### (multiple entries) List of starred map sources
#### More maps
This opens a list with all sources. Each source can be starred/unstarred so it appears in the main map menu.

### Waypoints
#### Browse
see waypoints from activated KMLs (their names shoud appear on the map).
#### Collections
List of source KMLs, with possibility to activate/deactivate them (star). Possibility to import one (from somewhere on the phone)

> **Decisions (KML):**
> - Supported: Placemark Points, LineStrings and Polygons, with basic Style/StyleMap (line colour, width, fill colour). Folders are flattened. KMZ can be imported.
> - **The KML files in app storage are the source of truth.** Importing copies the file in. Adding a waypoint inserts a Placemark through a DOM edit that keeps the rest of the file unchanged.
> - A default collection, `My waypoints.kml`, is created on first run.
#### Add manually
Screen to manually add a waypoint (lat/lon/name).
It should accept decimal degrees, degrees+decimal minutes, and degrees+minutes+decimal seconds.
The space bar in those field should convert to the appropriate next separator (degree sign, minute single quote...).
It should let the user select which KML the waypoint should be saved in.

> **Decision:** one parser accepts DD, DDM and DMS, with either N/S/E/W letters or a sign. Pressing space moves to the next separator (`°` → `'` → `"`).

### Tracks
#### Browse
See and select GPX tracks (previously saved)



### Settings
#### Map server keys 
This is where the user can input their keys for the various services.
Sources:
- Thunderforest
- Mapbox
- Tracestrack
- Ordnance Survey (OS Maps API)
- Azure Maps (Microsoft satellite, official)

#### Units
distance: m/km, miles, nautical miles
speed: km/h, mph, kt, m/s
angle format: decimal degrees, degrees+decimal minutes, degrees+minutes+decimal seconds.

#### Track recording
- Minimum time interval between points (seconds).
- Minimum distance between points (metres).
- Accuracy filter: drop fixes worse than N metres.

(No auto-pause.)

#### Tile cache
Per-source cache size, plus a "clear cache" button for each source.


# Tile sources
Organise into different categories: General, Hiking, Biking, Local (plus any categories the user creates).

All sources are raster XYZ in Web Mercator (EPSG:3857). Columns:
- **Size**: tile size in px.
- **Zoom**: min–max zoom offered in the app.
- **Bulk**: whether the download-area screen may fetch the source.

Placeholders are explained under "Catalog format" below. Rows marked ⚠ have something to check when implementing.

## General
| id | Name | URL template | Size | Zoom | Key | Bulk | Notes |
|---|---|---|---|---|---|---|---|
| `ms-satellite` | Microsoft satellite | `https://ecn.t{s}.tiles.virtualearth.net/tiles/a{q}.jpeg?g=1` (s = 0–3) | 256 | 1–19 | – | yes | ⚠ Unofficial endpoint. Bing Maps' public API is being retired, so this may stop working without notice. |
| `azure-imagery` | Microsoft satellite (Azure Maps) | `https://atlas.microsoft.com/map/tile?api-version=2024-04-01&tilesetId=microsoft.imagery&zoom={z}&x={x}&y={y}&tileSize=512` + header `subscription-key: {key}` | 512 | 1–19 | Azure Maps | yes | The official successor and the fallback if the Bing endpoint dies. Same Microsoft imagery. The docs put the key in a **header**, not the URL. Azure asks for attribution from its Get Map Attribution API to be displayed. Per the design, attribution appears only in the sources list, as a static "© Microsoft". |
| `mapbox-satellite` | Mapbox satellite | `https://api.mapbox.com/v4/mapbox.satellite/{z}/{x}/{y}@2x.jpg90?access_token={key}` | 512 | 0–19 | Mapbox | yes | Imagery past z19 is mostly upscaled, so the app caps at 19. |
| `osm-standard` | Standard OSM | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | 256 | 0–19 | – | **no** | OSMF tile policy: identifying User-Agent required, no bulk download. |
| `tracestrack-topo` | Tracestrack Topo | `https://tile.tracestrack.com/topo_auto/{z}/{x}/{y}.webp?key={key}` | 512? | 0–19? | Tracestrack | yes | ⚠ Tile size and max zoom aren't documented; check against a real tile. `topo_auto` = labels in local language; `topo_en` etc. also exist. WebP decodes natively on Android. |

## Hiking
| id | Name | URL template | Size | Zoom | Key | Bulk | Notes |
|---|---|---|---|---|---|---|---|
| `tf-outdoors` | Outdoors (Thunderforest) | `https://tile.thunderforest.com/outdoors/{z}/{x}/{y}@2x.png?apikey={key}` | 512 | 0–22 | Thunderforest | yes | Free tier is 150k tiles/month, so bulk downloads eat into it. |
| `tf-landscape` | Landscape (Thunderforest) | `https://tile.thunderforest.com/landscape/{z}/{x}/{y}@2x.png?apikey={key}` | 512 | 0–22 | Thunderforest | yes | Same quota as above. |
| `opentopomap` | OpenTopoMap | `https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png` (s = a,b,c) | 256 | 0–17 | – | yes | Volunteer-run: keep concurrency at 2. |

## Biking
| id | Name | URL template | Size | Zoom | Key | Bulk | Notes |
|---|---|---|---|---|---|---|---|
| `cyclosm` | CyclOSM | `https://{s}.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png` (s = a,b,c) | 256 | 0–20 | – | ⚠ | Hosted by OSM France. ⚠ Check their tile policy before allowing bulk; default **no** until checked. |
| `tf-cycle` | OpenCycleMap (Thunderforest) | `https://tile.thunderforest.com/cycle/{z}/{x}/{y}@2x.png?apikey={key}` | 512 | 0–22 | Thunderforest | yes | Same quota as above. |

## Local
| id | Name | URL template | Size | Zoom | Key | Bulk | Coverage | Notes |
|---|---|---|---|---|---|---|---|---|
| `os-outdoor` | Ordnance Survey Outdoor (UK) | `https://api.os.uk/maps/raster/v1/zxy/Outdoor_3857/{z}/{x}/{y}.png?key={key}` | 256? | 7–16 | OS | yes | Great Britain | z17–20 exist but need a paid OS plan: the user can raise max zoom per source. ⚠ Confirm tile size. Road_3857 and Light_3857 can be added through the custom-source form. |
| `ga-topo-au` | Australia Topographic (GA) | `https://services.ga.gov.au/gis/rest/services/Topographic_Base_Map/MapServer/WMTS/tile/1.0.0/Topographic_Base_Map/default/GoogleMapsCompatible/{z}/{y}/{x}.png` | 256 | 0–18 | – | yes | lon 100.1–169.2, lat −50.0 to −2.4 | WMTS `GoogleMapsCompatible` set, so it can be addressed as XYZ. **Note the `{z}/{y}/{x}` order** (WMTS TileRow/TileCol). Shows 250k topo at the matching zooms. |

> **Decisions (sources):**
> - **OS: `Outdoor_3857` only for now.** The classic Explorer/Landranger rasters (`Leisure`) exist only in British National Grid (EPSG:27700). Supporting a second projection is deferred (see "Later").
> - **No overlay layers in v1**: one base map at a time. Maverick's `overlays/openhiking` is not used.
> - The Maverick link is resolved: the repo holds only 3 tile sources (hikebike, openstreetmap, opentopomap). We already have OSM and OpenTopoMap, and hikebike was **dropped** (its Wikimedia Labs server is likely offline).

## Catalog format
How the list evolves: the curated list ships **built into the app**, and the user adds new sources through an **in-app "Add custom source" form**. There is no remote catalog and no catalog import/export (for now).

- **Source record** (the same schema for built-in and custom sources):
  - `id`: stable, never reused.
  - `name`, `category`, `urlTemplate`.
  - `subdomains`: list, optional.
  - `tileSize`: 256 or 512.
  - `minZoom`, `maxZoom`.
  - `keyProvider`: thunderforest / mapbox / tracestrack / os / azure / none / custom. `custom` means a key stored with the source.
  - `attribution`.
  - `bulkDownloadAllowed`.
  - `coverage`: optional lat/lon bounding box.
  - `headers`: optional HTTP headers sent with each request; `{key}` is substituted (needed for Azure Maps).
- **Placeholders**:
  - `{z}` `{x}` `{y}`: XYZ tile coordinates.
  - `{-y}`: TMS-flipped y.
  - `{q}`: Bing quadkey.
  - `{s}`: rotated over `subdomains`.
  - `{key}`: the API key (in the URL or in a header).
- **Built-in sources** come from a bundled `sources.json`:
  - They are read-only, but the user can override `maxZoom` (for example OS premium) and hide or star them.
  - Overrides and stars are stored by `id`, so they survive app updates that change the built-in list.
  - If a built-in source is removed in an update, its cache is kept and it is listed under "Retired" until the user clears it.
- **Custom sources** are stored in the app database:
  - They can be edited and deleted.
  - "Duplicate" on any source (built-in or custom) opens the form pre-filled, e.g. to make OS Road from OS Outdoor.
- **The "Add custom source" form** has these fields: name; category (an existing one or a new one, default "Custom"); URL template; subdomains; extra headers; tile size; min and max zoom; key (pick a known provider or enter one); attribution; whether bulk download is allowed (default **off**); and an optional coverage box. It also has a **Test** button that fetches the tile under the current map centre and shows it, or the HTTP error.
- **Coverage**: outside a source's bounding box, no requests are made and a distinct "out of coverage" placeholder is drawn.
- **Keys**: a source whose key provider has no key yet is greyed out in the source list until one is entered in Settings → Map server keys.
- **Attribution** is **not drawn on the map**. It is shown only in the sources list (under each source's name, in "More maps"), and in the source detail/edit screen.
- **Cache**: one SQLite file per source `id`.

Could it also do vector tiles?

> **Decision:** no. CartoGliss is raster only. We might revisit after v1, but only as server-rendered rasters, never as in-app vector rendering.

## Later
- EPSG:27700 (British National Grid) support, for OS Leisure 1:25k/1:50k.
- Overlay tile layers.
- Catalog import/export (JSON or Maverick `mapserver.txt`).

# Random thoughts
Should be compatible with both 256 and 512 px tiles.

> **Decision:** yes. See "Decisions (zoom)": every tile is drawn at 256 dp × M.

Does it make sense to use MapLibre Native?

> **Decision:** no. MapLibre is built around continuous zoom, inertia and parent-tile fallback, which go against the core UX rules (whole zoom levels, no inertia, no inferred tiles). We write a small custom raster renderer instead.


# Decisions summary (2026-11-21)
| Topic | Decision |
|---|---|
| Stack | Kotlin + Jetpack Compose. minSdk 26. No Google Play Services. Toolchain (M0): Gradle 8.7, AGP 8.4.2, Kotlin 1.9.24, Compose BOM 2024.06, compile/target SDK 34 (what the dev machine has cached). Moving to the latest SDK is a later step. |
| Distribution | Personal / sideload. Any source can ship; the user is responsible for each provider's terms. |
| Renderer | Custom Compose `Canvas` raster renderer. Web Mercator (EPSG:3857) XYZ, plus quadkey and WMTS-as-XYZ templates. |
| Vector tiles | Not supported. |
| Package | `app.cartogliss` (applicationId and Kotlin root package). |
| First launch | Whole world, OSM Standard, z2. From then on, the last viewport and source are restored. |
| Default source | OSM Standard. |
| Build | Built and unit-tested on the dev machine (Android SDK at `~/Android/Sdk`, JDK 21). The user installs the APK. |
| Git | Local repo, one commit per milestone, no remote. |
| Source list | Curated built-in list plus an in-app "Add custom source" form. Stable ids. No overlays in v1. OS = Outdoor_3857 only. |
| Orientation | Always north-up. |
| Zoom | Whole levels only. A missing tile shows a placeholder and is never inferred from another level. |
| Overzoom | M ∈ {1,2,4,8}, manual only through the red NxM toggle. |
| Gestures | Pan 1:1, no fling. Pinch snaps ±1 step on release. Double-tap = +1. |
| Tile cache | Kept forever: no automatic removal, no automatic refresh. Cache first, network only on a miss. Manual clear per source. |
| KML | Points + lines + polygons with basic styles. KMZ import. The files are the source of truth. |
| Target | From Pin "Go To" or a waypoint tap. Persists. Long-press clears it. |
| Tracks | Foreground service. Settings: time interval, distance, accuracy filter. Saved as GPX. |
| Location | `LocationManager` GPS + `GnssStatus`. Heading from the rotation vector sensor, true north. |


# Architecture
Single Gradle `:app` module to start, organised by package:

- `map.render`: viewport state (zoom N, overzoom M, centre in world pixel coordinates), tile math, and Canvas drawing. It has an overlay layer API used by the GPS point, cone, arrow, target line, KML features and labels, tracks, crosshair and scale bar.
- `tiles`: source model and catalog loader. `TileRepository` looks up a tile in memory (LRU bitmap cache), then on disk, then on the network (OkHttp). Disk storage is one SQLite file per source, with an MBTiles-like schema `tiles(z, x, y, data BLOB, fetched_at)`. Bulk download uses a WorkManager worker.
- `location`: GPS fixes, GNSS status and heading sensors, exposed as `StateFlow`s.
- `tracks`: recording foreground service. While recording, points go to a SQLite journal so a crash loses nothing; when recording stops, the track is exported to GPX. Saved tracks are GPX files in app storage, and Browse can show or hide each one on the map.
- `kml`: parser (XmlPullParser), DOM-based writer for adding waypoints, and a collections registry with active flags.
- `astro`: sun, moon and UV calculations.
- `geo`: distance/bearing (great circle), coordinate formatting and parsing, unit conversion.
- `settings`: DataStore (units, API keys, starred sources, track settings, current target, last viewport and source).
- `ui`: Compose screens: Map, Stats, Pin modal, Menu, Download, Sources, Waypoints (Browse/Collections/Add), Tracks, Settings.


# Roadmap
0. Project skeleton: Gradle, Compose navigation, settings store.
1. Renderer with a single source (OSM Standard): discrete zoom, pan, pinch snap, overzoom toggle, disk cache, placeholder tiles.
2. Overlays: GPS point, heading cone, movement arrow, crosshair, scale bar, target + dashed line.
3. State bar, Pin modal, Stats view (astro + UV).
4. Source catalog, categories, starring, API keys, "Add custom source" form with Test, coverage boxes, attribution in the sources list.
5. KML: import/display/labels, Browse, Collections, Add manually.
6. Track recording service, GPX, Tracks browse.
7. Download-area screen and WorkManager downloader.
8. Units everywhere, polish.


# Testing
- JVM unit tests for the pure logic: tile math and quadkeys, coordinate parser and formatter, distance/bearing, UV and astro, KML parsing and round-trip (add a waypoint, rest of the file unchanged), download tile counting, scale-bar rounding.
- On-device checks: zoom and overzoom behaviour, missing-tile placeholder (no fallback from other levels), no fling when panning, cone hidden when the phone is tilted, background recording with the screen off.


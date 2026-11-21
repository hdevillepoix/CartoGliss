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
> - **Change (2026-11-21): M can also be ½.** In red mode, − from M = 1 goes to M = ½: tiles of level N are drawn at 128 dp, showing four times the area with the same tiles and no zoom-level change (no inference from other levels). The toggle shows `Nx½`. Pinch and double-tap step through ½ → 1 → 2 → 4 → 8 the same way. Saved as a double (`viewport.overzoomFactor`); the old integer key is still read once.
> - Overzoom scales up the tiles of level N. No other level is fetched.
> - Pinch: once the pinch passes a threshold, exactly ±1 step is applied on release (zoom level, or overzoom when red mode is on). Nothing between levels is ever drawn. Double-tap = +1 step.
> - Tile size: each source declares 256 or 512 px. A tile is always drawn at 256 dp × M, so 512 px tiles act as high-DPI tiles.
> - Zoom focus (M1): +/− zoom around the map centre (the crosshair). Double-tap zooms around the tapped point. A pinch zooms around the point between the fingers.
> - Pinch threshold (M1): a ratio of 1.4 counts as one step. While pinching, a faint large "+" or "−" at the centre shows what releasing will do.
> - Overzoom mode (the red toggle) is not remembered across restarts; the factor M is.
> - The map wraps horizontally across the antimeridian. Beyond the poles a darker grey "void" is drawn, distinct from the light-grey tile placeholder with its fine grid.
> - Loading (M1): visible tiles are requested centre-first. Network concurrency is 2 per source. A failed tile is retried after 15 s while it stays visible; a 404 is not retried for the rest of the session. Tiles that scroll off screen before their turn are never fetched.
  
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
> - Overlay details (M2):
>   - The cone is a translucent blue ±20° wedge of radius 64 dp. It is shown only when there is a position, because converting to true north needs the local magnetic declination.
>   - The cone points where the **top of the screen** faces, so it also works in landscape.
>   - The movement arrow is an outlined dark-blue triangle (with a white halo) around the dot.
>   - The target is a green ring. The dashed line is clipped to the screen and takes the short way across the antimeridian.
>   - The crosshair has 1 dp black arms with a small central gap.
>   - The "centre on my position" button sits under the zoom controls. It is disabled until a fix exists, and stale fixes count.
> - State bar (M3), left to right: map/stats toggle · target status · Pin · satellites (used/visible) · record · menu.
>   - Tapping the target status centres the map on the target; a long-press clears it.
>   - The GPS on/off toggle is not remembered across restarts: GPS is on at launch.
> - Stats view (M3):
>   - Altitude is above mean sea level where the phone provides it (Android 14+), otherwise GPS ellipsoid height.
>   - Without a fix, the sun/moon/UV figures use the map centre and say so.
>   - Rise/set times are for today, local time.
>   - The UV figures are labelled as a clear-sky estimate.
> - Location (M2): the permission is requested the first time the map opens. GPS and the compass run only while the map screen is visible (track recording will hold GPS on in M6). Updates arrive every 1 s from the GPS provider.

A scale is in the bottom right of the map. it is always the same size on the screen, but the number it corresponds to changes.

> **Decision:** the scale bar is a fixed 2 cm on screen. Its label is the ground distance at the centre latitude, rounded to 2 significant figures, in the chosen distance unit.
> Short distances switch to a smaller unit: m below 1 km; ft below 0.1 mi; m below 0.1 NM. The bar is drawn black with a white halo, above the state-bar area (M2).

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

> **Decision:** recording runs in a foreground service with a persistent notification, so it keeps going with the screen off.
> **Correction (M6):** background location permission is *not* needed. A location foreground service started while the app is visible keeps "while in use" access, so the app never asks for "Allow all the time".
>
> **Implementation (M6):**
> - Points are filtered by the three Track recording settings. Only live fixes count; the last stale fix isn't recorded.
> - The notification shows distance and duration, and has "Pause" and "Finish and save" actions. On Android 13+ the notification permission is asked the first time you record; recording works without it.
> - Finishing writes `tracks/yyyy-MM-dd_HH-mm.gpx` in the storage folder (see Storage; GPX 1.1, named "Track yyyy-MM-dd HH:mm") and switches it on for the map. If the file can't be written, the track stays open so nothing is lost.
> - If Android kills the app mid-recording, the service restarts and continues the same track in a new `<trkseg>`. Opening the map also resumes an interrupted recording.
>
> **Change (2026-11-21): buffered writes.** Points are no longer written one by one. They are kept in memory and written to the SQLite journal in one transaction per batch: when N points are waiting (Settings → Track recording, default 10; 1 = every point), when the oldest has waited 2 minutes, and on pause, finish and service stop. If Android kills the process, at most the unwritten batch is lost.
>
> **Change (2026-11-21): pause.** Tapping the record button while recording asks: **Pause**, **Finish and save**, or Keep recording.
> - Paused: GPS is released (no notification), the track stays open in the journal and survives app restarts. The button shows red pause bars and "paused"; the live track stays on the map.
> - Tapping it while paused asks: **Continue this track** (in a new `<trkseg>`), **Finish it and start a new track**, **Finish it**, or Cancel.
> - The GPS on/off toggle also pauses recording (no fixes arrive).
> - On the map, the live track is red and saved tracks are blue, 3 dp.
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
> - **Implementation (M7):**
>   - The screen downloads from the map's current source. Modes are Move / **Paint** (default) / Rectangle / Erase. One finger draws in the drawing modes; two fingers always pan and pinch. Tapping paints one square.
>   - The selection is stored as grid cells of mixed levels (painting at different zooms combines exactly), so per-level tile counts are exact. It survives leaving the screen, but not an app restart.
>   - The level list shows a tile count per level, with all/none shortcuts. Only the view's zoom is selected at first.
>   - The size estimate uses the average size of this source's cached tiles, or 25 kB if fewer than 50 are cached.
>   - Above 50k tiles a warning about server quotas appears. Above 500k, downloading is refused.
>   - Download jobs queue up and run one after another (WorkManager, foreground "dataSync" with a progress notification and Cancel). A "Wi-Fi only" switch is offered.
>   - Already-cached and out-of-coverage tiles are skipped. HTTP 429/503 gets one retry after 10 s; other failures are counted and shown.
>   - Downloaded tiles go into the same permanent cache as browsed ones.
#### (multiple entries) List of starred map sources

> **Addition (2026-11-21): layers button on the map.** A layers button (two stacked diamonds) sits in the map's right-hand column, below centre-on-GPS. It opens a popup listing the starred, non-hidden sources by short name only (no categories), with a check on the current one. Sources whose key is missing are greyed out. A last entry, "More…", opens the More maps list. The starred list in the menu stays as well.
#### More maps
This opens a list with all sources. Each source can be starred/unstarred so it appears in the main map menu.

### Waypoints
#### Browse
see waypoints from activated KMLs (their names shoud appear on the map).
#### Collections
List of source KMLs, with possibility to activate/deactivate them (star). Possibility to import one (from somewhere on the phone)

> **Decisions (KML):**
> - Supported: Placemark Points, LineStrings and Polygons, with basic Style/StyleMap (line colour, width, fill colour). Folders are flattened. KMZ can be imported.
> - **The KML files in the storage folder are the source of truth.** Importing copies the file in. Adding a waypoint inserts a Placemark through a DOM edit that keeps the rest of the file unchanged.
> - A default collection, `My waypoints.kml`, is created on first run.
#### Add manually
Screen to manually add a waypoint (lat/lon/name).
It should accept decimal degrees, degrees+decimal minutes, and degrees+minutes+decimal seconds.
The space bar in those field should convert to the appropriate next separator (degree sign, minute single quote...).
It should let the user select which KML the waypoint should be saved in.

> **Decision:** one parser accepts DD, DDM and DMS, with either N/S/E/W letters or a sign. Pressing space moves to the next separator (`°` → `'` → `"`).
> Only the last component may have decimals, and minutes and seconds must be under 60. A space typed right after a digit becomes the next unused separator; anywhere else it stays a space. A preview line shows the parsed value in your angle format.
> **Change (2026-11-21):** coordinates are shown as **signed decimal degrees by default** (`45.12346, -6.12345`, 5 decimals). Settings → Units → Angle format can override this with decimal degrees with N/S/E/W, DDM or DMS; those use ASCII `'` and `"`, hemisphere letter first (e.g. `N 45°07.408'`).

> **Implementation (M5):**
> - Collections are `.kml` files in the `waypoints/` folder (see Storage). Files dropped into the folder by other apps appear when the app comes back to the front. Importing copies the file in; a KMZ is unpacked to its `doc.kml` (or its first `.kml`). Importing accepts any file type, because many file managers label KML as octet-stream, and the file must parse before it's kept.
> - Imported files are capped at 50 MB, and DTDs/external entities are refused.
> - Active collections are remembered in settings; new and imported collections start active.
> - Collections can also be created empty ("New") and deleted (with confirmation).
> - Adding a waypoint appends a `<Placemark>` to the first `<Document>` (or `<Folder>`) and rewrites the file through a temp file and rename, without re-indenting it.
> - On the map: lines and areas use the KML colour and width (defaults: magenta lines, translucent magenta fill, orange waypoints). Waypoint names are drawn with a white halo, but are skipped when more than 300 are on screen.
> - Tapping near a waypoint (within 24 dp) opens its details with "Go To". The tap is acted on only after the double-tap timeout, so double-tap zoom still works.
>
> **Addition (2026-11-21): tapping lines and areas, and deleting.**
> - A tap first looks for a waypoint within 24 dp. If there is none, it looks for the nearest line or area outline within 24 dp, and then for the top-most area containing the tap (holes don't count).
> - Lines and areas open a details dialog: name, collection, length (perimeter for areas), description, and Delete.
> - The waypoint dialog, on the map and in Browse, also has Delete. Every delete asks for confirmation.
> - Deleting removes the whole `<Placemark>` (all of its MultiGeometry parts) from the KML file, in place through the DOM, like adding a waypoint. The rest of the file is kept, and adding then deleting a placemark leaves the file byte-identical.
> - A placemark is identified by its position among the file's Placemarks plus its name. If the file changed meanwhile (for example edited by another app in the shared folder) and the name at that position no longer matches, nothing is deleted and a message says so.
> - Browse: search by name. Sorted by distance when there is a fix, by name otherwise. Each waypoint offers "Go To" or "Show on map".

### Tracks
#### Browse
See and select GPX tracks (previously saved)

> **Decision (M6):** the list is newest first, with date, distance, duration and point count. A checkbox shows or hides a track on the map. Tapping a track opens it on the map (centred on its start). Tracks can be shared (GPX via the share sheet) and deleted.



### Settings
#### Map server keys 
This is where the user can input their keys for the various services.
Sources:
- Thunderforest
- Mapbox
- Tracestrack
- Ordnance Survey (OS Maps API)
- Azure Maps (Microsoft satellite, official)

> **Built-in keys (2026-11-21):** keys can optionally be built into the APK. Put them in `keys.env` at the repo root (gitignored; template in `keys.env.example`, lines like `MAPBOX_KEY=…`), or set environment variables with the same names. Gradle turns them into `BuildConfig.KEY_*` fields. A key typed here overrides the built-in one, and clearing the field goes back to it. Built-in keys can be extracted from the APK, so don't share an APK that carries them.

#### Units
distance: m/km, miles, nautical miles
speed: km/h, mph, kt, m/s
angle format: signed decimal degrees (default), decimal degrees with N/S/E/W, degrees+decimal minutes, degrees+minutes+decimal seconds.

#### Track recording
- Minimum time interval between points (seconds).
- Minimum distance between points (metres).
- Accuracy filter: drop fixes worse than N metres.
- Points kept in memory before writing to storage (default 10).

(No auto-pause.)

#### Storage folder
Where GPX tracks and KML waypoints are kept.

> **Decision (2026-11-21):** tracks and waypoints can live in a shared folder that other apps can open and that survives uninstalling the app.
> - The user picks a folder once with the system folder picker (Storage Access Framework, e.g. a new `CartoGliss` folder). The app keeps a persistent permission on it, and creates `tracks/` (GPX) and `waypoints/` (KML) inside. No "All files access" permission is needed.
> - On first launch a dialog asks for the folder; "App storage" keeps the files private. This can be changed later in Settings → Storage folder.
> - Choosing a folder moves the files from app storage into it. A file with the same name that is already there is kept: an identical copy, or an empty collection like the first-run "My waypoints", is dropped; anything else is moved in under a new name, e.g. `name (2).kml`.
> - After a reinstall the first-launch dialog asks again; picking the same folder brings the files back. If settings were restored from a backup but the folder permission is gone, the map says access was lost and asks again (meanwhile app storage is used).
> - "Use app storage instead" leaves the files in the folder.
> - The tile cache, settings and the recording journal stay in app storage.
> - Replacing a file writes `name.tmp`, deletes the old file and renames the temp file, or copies it if the provider can't rename.

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
  - `shortName`: optional label for the map's layers popup (e.g. "OSM", "TF Outdoors"). Falls back to `name`; custom sources use their name.
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
- **Implementation (M4):**
  - The bundled list is `app/src/main/assets/sources.json`. Custom sources, max-zoom overrides and hidden flags live in `sources_user.json` in app storage. Custom ids look like `custom-1a2b3c4d`.
  - A custom source can't reuse a built-in id.
  - Menu: starred sources are listed under Maps. Tapping one switches the map (a check marks the current one). Sources whose key is missing are greyed out and can't be selected.
  - Default stars: OSM Standard and OpenTopoMap.
  - "More maps": grouped by category, with a star toggle per row, attribution under the name, and "needs X key" in red. Tapping a row opens its detail page (facts, notes, URL template, "Use this map", star, duplicate; plus max-zoom override and hide for built-ins, or edit and delete for custom sources). Hidden sources appear in a "Hidden" section at the end. A + button adds a custom source.
  - Editing a custom source keeps its cached tiles. Clear the cache by hand if the URL changed.
  - Entering an API key retries failed tiles straight away.
  - Out-of-coverage tiles are drawn pale with diagonal hatching, and are never requested.

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
| Stack | Kotlin + Jetpack Compose. minSdk 26, compileSdk 37.2, targetSdk 37. No Google Play Services. Toolchain (2026-11-21): Gradle 9.9.2, AGP 9.5.2 (built-in Kotlin), Kotlin 2.4.21 + Compose compiler plugin, Compose BOM 2026.11.01, OkHttp 5. |
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
| Overzoom | M ∈ {½,1,2,4,8}, manual only through the red NxM toggle. |
| Gestures | Pan 1:1, no fling. Pinch snaps ±1 step on release. Double-tap = +1. |
| Tile cache | Kept forever: no automatic removal, no automatic refresh. Cache first, network only on a miss. Manual clear per source. |
| KML | Points + lines + polygons with basic styles. KMZ import. The files are the source of truth. |
| Target | From Pin "Go To" or a waypoint tap. Persists. Long-press clears it. |
| Tracks | Foreground service. Settings: time interval, distance, accuracy filter, write batch size. Pause / continue (new segment) / finish. Saved as GPX. |
| Storage | GPX and KML in a user-picked shared folder (SAF): `tracks/`, `waypoints/`. App storage until one is picked. |
| Coordinates | Signed decimal degrees by default. |
| Built-in keys | Optional, from the gitignored `keys.env` into BuildConfig. Keys typed in Settings override them. |
| Location | `LocationManager` GPS + `GnssStatus`. Heading from the rotation vector sensor, true north. |


# Architecture
Single Gradle `:app` module to start, organised by package:

- `map.render`: viewport state (zoom N, overzoom M, centre in world pixel coordinates), tile math, and Canvas drawing. It has an overlay layer API used by the GPS point, cone, arrow, target line, KML features and labels, tracks, crosshair and scale bar.
- `tiles`: source model and catalog loader. `TileRepository` looks up a tile in memory (LRU bitmap cache), then on disk, then on the network (OkHttp). Disk storage is one SQLite file per source, with an MBTiles-like schema `tiles(z, x, y, data BLOB, fetched_at)`. Bulk download uses a WorkManager worker.
- `location`: GPS fixes, GNSS status and heading sensors, exposed as `StateFlow`s.
- `tracks`: recording foreground service. While recording, points are buffered and written in batches to a SQLite journal; finishing exports the track to GPX. Saved tracks are GPX files in the storage folder, and Browse can show or hide each one on the map.
- `storage`: `DocDir`, a flat folder of files, either app storage (`FileDocDir`) or a sub-folder of the picked SAF tree (`SafDocDir`). `Storage` resolves the current location, and handles migration and sharing URIs.
- `kml`: parser (XmlPullParser), DOM-based writer for adding waypoints, and a collections registry with active flags.
- `astro`: sun, moon and UV calculations.
- `geo`: distance/bearing (great circle), coordinate formatting and parsing, unit conversion.
- `settings`: DataStore (units, API keys, starred sources, track settings, current target, last viewport and source).
- `ui`: Compose screens: Map, Stats, Pin modal, Menu, Download, Sources, Waypoints (Browse/Collections/Add), Tracks, Settings.


# Roadmap
Status (2026-11-21): milestones 0–8 implemented and unit-tested; on-device testing pending. After M8: buffered track writes, pause/continue, signed-DD default, shared storage folder, built-in keys.

0. Project skeleton: Gradle, Compose navigation, settings store.
1. Renderer with a single source (OSM Standard): discrete zoom, pan, pinch snap, overzoom toggle, disk cache, placeholder tiles.
2. Overlays: GPS point, heading cone, movement arrow, crosshair, scale bar, target + dashed line.
3. State bar, Pin modal, Stats view (astro + UV).
4. Source catalog, categories, starring, API keys, "Add custom source" form with Test, coverage boxes, attribution in the sources list.
5. KML: import/display/labels, Browse, Collections, Add manually.
6. Track recording service, GPX, Tracks browse.
7. Download-area screen and WorkManager downloader.
8. Units everywhere, polish.
   - Done (M8): units and angle format are used everywhere distances, speeds, altitudes and coordinates appear. Adaptive launcher icon (folded map + crosshair, with a monochrome layer). Back closes the stats view first. Lint is clean apart from one informational note about the `-v26` icon folder, which AAPT still requires.

## Later (not in v1)
- EPSG:27700 (OS Leisure 1:25k/1:50k), overlay tile layers, catalog import/export.
- Importing GPX tracks (only recorded tracks are listed now).
- Remembering the painted download area across app restarts.
- Dynamic Azure Maps attribution.


# Testing
Current suite (84 JVM tests): settings round-trip, built-in keys and the storage folder; tile maths and quadkeys; viewport stepping/panning/visible tiles; URL templates; catalog validity, coverage and registry persistence; distance/bearing/compass; unit and coordinate formatting; coordinate parsing and the space-bar rule; UV formula, threshold crossing and real sun data (Geneva/London); KML parse/styles/KMZ/in-place edit (no whitespace growth), placemark delete (refused if the file changed; add + delete is byte-identical); GPX write/parse, segments and the track filter; area-selection maths (splits, dedup, exact counts); point buffer; file folder writes (a failed write keeps the old file), unique names and migration clashes. The SAF folder needs a device.

- JVM unit tests for the pure logic: tile math and quadkeys, coordinate parser and formatter, distance/bearing, UV and astro, KML parsing and round-trip (add a waypoint, rest of the file unchanged), download tile counting, scale-bar rounding.
- On-device checks: zoom and overzoom behaviour, missing-tile placeholder (no fallback from other levels), no fling when panning, cone hidden when the phone is tilted, background recording with the screen off.


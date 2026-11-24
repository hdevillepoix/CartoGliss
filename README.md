# CartoGliss

A simple Android raster map app.

This is meant to be general purpose, but was designed with these activities in mind: bush hiking, sailing, 4WDriving in the outback... basically in areas where tracks are not well mapped, and where the user may want to go off the track anyway.
There is no turn by turn navigation (not planning on adding this in the future), the idea is to display good maps to the user and let them make their own choices and do their own navigation.
Hence by design it has a old feel to it, and departs from the modern design philosophy that maps should be vector-rendered, fluid, and 3D.


- Background layers: satellite, and various great map styles derived from the OpenStreetMap database (OpenTopoMap, CyclOSM, OpenCyleMap, Tracestrack, Mapbox...), and customisable to point to any raster tile server.
- Predictable gestures: the map is always north-up, panning has no fling, and a pinch or double-tap moves exactly one zoom level.
- Offline tiles cache: Every tile viewed or downloaded is kept until the user decides to clear it. An area can be batch-downloaded by painting an area of interest and choosing zoom levels.
- Tile sources that need API keys, the keys have to be provided by the users.
- Waypoints (KML) and GPS tracks (GPX): Kept in a user defined folder outside of the app's storage, to facilitate work with other apps, and also to survive app deletion.
- Navigation: no turn by turn navigation (not planning on adding this in the future). The only navigation feature is that a waypoint/target can be set and the app will show the straight line to it.
- Track recording: runs in the background, can be paused and continued, and is saved as it goes to a GPX file, so a crash keeps the track.
- No Google Play Services, no account, no tracking (no user data is sent anywhere).

## Building

```sh
./gradlew assembleRelease
```

To build your map API keys into the APK, copy `keys.env.example` to `keys.env` and fill it in. `keys.env` is never committed. Anyone with the APK can extract those keys, so don't share an APK that has them.

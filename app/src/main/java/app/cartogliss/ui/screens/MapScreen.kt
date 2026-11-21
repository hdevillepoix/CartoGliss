package app.cartogliss.ui.screens

import android.Manifest
import android.hardware.GeomagneticField
import android.os.SystemClock
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cartogliss.appContainer
import app.cartogliss.geo.Geo
import app.cartogliss.geo.Units
import app.cartogliss.location.Fix
import app.cartogliss.location.Heading
import app.cartogliss.map.MapViewport
import app.cartogliss.settings.Settings
import app.cartogliss.settings.Target
import app.cartogliss.ui.map.CentreOnGpsButton
import app.cartogliss.ui.map.MapOverlay
import app.cartogliss.ui.map.MapView
import app.cartogliss.ui.map.PinDialog
import app.cartogliss.ui.map.ScaleBar
import app.cartogliss.ui.map.StateBar
import app.cartogliss.ui.map.StateBarActions
import app.cartogliss.ui.map.StateBarState
import app.cartogliss.ui.map.TILE_DP
import app.cartogliss.ui.map.ZoomControls
import app.cartogliss.ui.stats.StatsView
import app.cartogliss.ui.waypoints.WaypointDialog
import app.cartogliss.ui.map.KmlLayerData
import app.cartogliss.ui.map.hitTest
import app.cartogliss.kml.Waypoint
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Minimum speed for the movement arrow (DESIGN.md). */
private const val MIN_ARROW_SPEED_MPS = 0.5f

private fun mapOverlay(fix: Fix?, heading: Heading?, target: Target?, nowNanos: Long, displayRotationDeg: Float): MapOverlay {
    val recent = fix != null && fix.ageMs(nowNanos) < Fix.RECENT_MS
    val cone = if (fix != null && heading != null && heading.tiltDeg <= Heading.MAX_TILT_DEG) {
        val declination = GeomagneticField(
            fix.lat.toFloat(), fix.lon.toFloat(), (fix.altitudeM ?: 0.0).toFloat(), fix.timeMs,
        ).declination
        (heading.magneticDeg + declination + displayRotationDeg + 360f) % 360f
    } else null
    val movement = fix?.bearingDeg?.takeIf { recent && (fix.speedMps ?: 0f) > MIN_ARROW_SPEED_MPS }
    return MapOverlay(fix = fix, fixRecent = recent, coneDeg = cone, movementDeg = movement, target = target)
}

@Composable
fun MapScreen(onOpenMenu: () -> Unit, onAddWaypoint: (lat: Double, lon: Double) -> Unit) {
    val repo = LocalContext.current.appContainer.settings
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    // Wait for stored settings so the map opens on the last viewport, not on a default.
    settings?.let { MapContent(it, onOpenMenu, onAddWaypoint) }
}

@OptIn(FlowPreview::class)
@Composable
private fun MapContent(initial: Settings, onOpenMenu: () -> Unit, onAddWaypoint: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val tilePx = with(LocalDensity.current) { TILE_DP.toPx() }
    val live by container.settings.settings.collectAsStateWithLifecycle(initial)
    val allSources by container.sources.sources.collectAsStateWithLifecycle()
    val source = remember(allSources, live.viewport.sourceId) { container.sources.resolve(live.viewport.sourceId) }

    var vp by remember { mutableStateOf(MapViewport.from(initial.viewport).clampZoom(source.minZoom, source.maxZoom)) }
    var overzoomMode by remember { mutableStateOf(false) }
    var pinchPending by remember { mutableIntStateOf(0) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var tappedWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    val collections by container.kml.collections.collectAsStateWithLifecycle()
    val kmlLayer = remember(collections) { KmlLayerData.from(collections) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val tapRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    // A different source may have a narrower zoom range.
    LaunchedEffect(source.minZoom, source.maxZoom) { vp = vp.clampZoom(source.minZoom, source.maxZoom) }

    // Location + heading run while this screen is visible.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.location.onPermissionChanged()
    }
    LaunchedEffect(Unit) {
        if (!container.location.hasPermission()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    LifecycleStartEffect(Unit) {
        container.location.acquire()
        container.heading.acquire()
        onStopOrDispose {
            container.location.release()
            container.heading.release()
        }
    }
    val fix by container.location.fix.collectAsStateWithLifecycle()
    val gnss by container.location.gnss.collectAsStateWithLifecycle()
    val gpsOn by container.location.enabled.collectAsStateWithLifecycle()
    val heading by container.heading.heading.collectAsStateWithLifecycle()
    var nowNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowNanos = SystemClock.elapsedRealtimeNanos()
            delay(1000)
        }
    }
    val displayRotationDeg = (LocalView.current.display?.rotation ?: Surface.ROTATION_0) * 90f
    val target = live.target
    val overlay = mapOverlay(fix, heading, target, nowNanos, displayRotationDeg)

    fun step(steps: Int, fx: Float = 0f, fy: Float = 0f) {
        vp = vp.stepped(steps, overzoomMode, tilePx, source.minZoom, source.maxZoom, fx, fy)
    }

    // Persist the viewport shortly after it stops changing, and when leaving the screen.
    val currentSourceId by rememberUpdatedState(source.id)
    LaunchedEffect(Unit) {
        snapshotFlow { vp }.debounce(500).collect { container.settings.setViewport(it.toViewport(currentSourceId)) }
    }
    DisposableEffect(Unit) {
        onDispose { container.appScope.launch { container.settings.setViewport(vp.toViewport(currentSourceId)) } }
    }

    val barState = StateBarState(
        showingStats = showStats,
        targetDistance = target?.let { t -> fix?.let { Units.distance(Geo.distance(it.lat, it.lon, t.lat, t.lon), live.distanceUnit) } ?: "—" },
        targetName = target?.name,
        gpsOn = gpsOn,
        satsUsed = gnss.used,
        satsVisible = gnss.visible,
        recording = false,
        recordSummary = null,
        recordAvailable = false, // milestone 6
    )
    val barActions = StateBarActions(
        onToggleStats = { showStats = !showStats },
        onTargetTap = { target?.let { vp = vp.centredOn(it.lat, it.lon); showStats = false } },
        onTargetLongPress = {
            scope.launch { container.settings.setTarget(null) }
            Toast.makeText(context, "Target cleared", Toast.LENGTH_SHORT).show()
        },
        onPin = { showPin = true },
        onToggleGps = { container.location.setEnabled(!gpsOn) },
        onRecord = {},
        onMenu = onOpenMenu,
    )

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            MapView(
                viewport = vp,
                source = source,
                loader = container.tileLoader,
                onPan = { dx, dy -> vp = vp.panned(dx, dy, tilePx) },
                onStep = { s, f -> step(s, f.x, f.y) },
                onPinchPending = { pinchPending = it },
                overlay = overlay,
                kml = kmlLayer,
                onTap = { at ->
                    kmlLayer.hitTest(vp, tilePx, mapSize.width, mapSize.height, at, tapRadiusPx)?.let { tappedWaypoint = it }
                },
                modifier = Modifier.fillMaxSize().onSizeChanged { mapSize = it },
            )

            if (pinchPending != 0) {
                Text(
                    if (pinchPending > 0) "+" else "−",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Column(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ZoomControls(
                    vp = vp,
                    overzoomMode = overzoomMode,
                    canIn = vp.canStep(1, overzoomMode, source.minZoom, source.maxZoom),
                    canOut = vp.canStep(-1, overzoomMode, source.minZoom, source.maxZoom),
                    onIn = { step(1) },
                    onOut = { step(-1) },
                    onToggle = { overzoomMode = !overzoomMode },
                )
                Spacer(Modifier.height(12.dp))
                CentreOnGpsButton(enabled = fix != null) {
                    fix?.let { vp = vp.centredOn(it.lat, it.lon) }
                }
            }

            ScaleBar(
                vp = vp,
                tilePx = tilePx,
                unit = live.distanceUnit,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp),
            )

            if (showStats) {
                StatsView(
                    fix = fix,
                    nowNanos = nowNanos,
                    mapCentreLat = vp.lat,
                    mapCentreLon = vp.lon,
                    settings = live,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        StateBar(barState, barActions)
    }

    tappedWaypoint?.let { wp ->
        WaypointDialog(
            wp = wp,
            fix = fix,
            angleFormat = live.angleFormat,
            distanceUnit = live.distanceUnit,
            onGoTo = {
                tappedWaypoint = null
                scope.launch { container.settings.setTarget(Target(wp.lat, wp.lon, wp.name)) }
            },
            onDismiss = { tappedWaypoint = null },
        )
    }

    if (showPin) {
        PinDialog(
            lat = vp.lat,
            lon = vp.lon,
            fix = fix,
            angleFormat = live.angleFormat,
            distanceUnit = live.distanceUnit,
            onGoTo = {
                showPin = false
                scope.launch { container.settings.setTarget(Target(vp.lat, vp.lon)) }
            },
            onAddWaypoint = {
                showPin = false
                onAddWaypoint(vp.lat, vp.lon)
            },
            onDismiss = { showPin = false },
        )
    }
}

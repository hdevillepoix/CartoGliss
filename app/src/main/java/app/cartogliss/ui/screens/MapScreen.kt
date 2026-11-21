package app.cartogliss.ui.screens

import android.Manifest
import android.hardware.GeomagneticField
import android.os.SystemClock
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cartogliss.geo.Geo
import app.cartogliss.geo.Units
import app.cartogliss.location.Fix
import app.cartogliss.location.Heading
import app.cartogliss.settings.DistanceUnit
import app.cartogliss.settings.Target
import app.cartogliss.ui.map.MapOverlay
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cartogliss.appContainer
import app.cartogliss.map.MapViewport
import app.cartogliss.settings.Settings
import app.cartogliss.tiles.BuiltInSources
import app.cartogliss.ui.map.MapView
import app.cartogliss.ui.map.TILE_DP
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private val OVERZOOM_RED = Color(0xFFD32F2F)

/** Scale bar length on screen: 2 cm ≈ 126 dp. */
private val SCALE_BAR_DP = 126.dp

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
fun MapScreen(onOpenMenu: () -> Unit) {
    val repo = LocalContext.current.appContainer.settings
    val settings by repo.settings.collectAsState(initial = null)
    // Wait for stored settings so the map opens on the last viewport, not on a default.
    settings?.let { MapContent(it, onOpenMenu) }
}

@OptIn(FlowPreview::class)
@Composable
private fun MapContent(initial: Settings, onOpenMenu: () -> Unit) {
    val container = LocalContext.current.appContainer
    val source = BuiltInSources.byId(initial.viewport.sourceId)
    val tilePx = with(LocalDensity.current) { TILE_DP.toPx() }

    var vp by remember { mutableStateOf(MapViewport.from(initial.viewport).clampZoom(source.minZoom, source.maxZoom)) }
    var overzoomMode by remember { mutableStateOf(false) }
    var pinchPending by remember { mutableIntStateOf(0) }
    val live by container.settings.settings.collectAsStateWithLifecycle(initial)

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
    val heading by container.heading.heading.collectAsStateWithLifecycle()
    var nowNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowNanos = SystemClock.elapsedRealtimeNanos()
            delay(1000)
        }
    }
    val displayRotationDeg = (LocalView.current.display?.rotation ?: Surface.ROTATION_0) * 90f
    val overlay = mapOverlay(fix, heading, live.target, nowNanos, displayRotationDeg)

    fun step(steps: Int, fx: Float = 0f, fy: Float = 0f) {
        vp = vp.stepped(steps, overzoomMode, tilePx, source.minZoom, source.maxZoom, fx, fy)
    }

    // Persist the viewport shortly after it stops changing, and when leaving the screen.
    LaunchedEffect(Unit) {
        snapshotFlow { vp }.debounce(500).collect { container.settings.setViewport(it.toViewport(source.id)) }
    }
    DisposableEffect(Unit) {
        onDispose { container.appScope.launch { container.settings.setViewport(vp.toViewport(source.id)) } }
    }

    Box(Modifier.fillMaxSize()) {
        MapView(
            viewport = vp,
            source = source,
            loader = container.tileLoader,
            onPan = { dx, dy -> vp = vp.panned(dx, dy, tilePx) },
            onStep = { s, f -> step(s, f.x, f.y) },
            onPinchPending = { pinchPending = it },
            overlay = overlay,
            modifier = Modifier.fillMaxSize(),
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
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 80.dp),
        )

        FilledIconButton(
            onClick = onOpenMenu,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu")
        }
    }
}

/** + / NxM / − stack. The NxM box toggles overzoom mode (red when on). */
@Composable
private fun ZoomControls(
    vp: MapViewport,
    overzoomMode: Boolean,
    canIn: Boolean,
    canOut: Boolean,
    onIn: () -> Unit,
    onOut: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onIn, enabled = canIn, modifier = Modifier.size(56.dp)) {
                Text("+", fontSize = 26.sp)
            }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .background(
                        if (overzoomMode) OVERZOOM_RED else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp),
                    ),
            ) {
                TextButton(onClick = onToggle, modifier = Modifier.size(width = 56.dp, height = 36.dp)) {
                    Text(
                        "${vp.zoom}x${vp.overzoom}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (overzoomMode) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            TextButton(onClick = onOut, enabled = canOut, modifier = Modifier.size(56.dp)) {
                Text("−", fontSize = 26.sp)
            }
        }
    }
}

/** "Centre on my position" button: a small target symbol. */
@Composable
private fun CentreOnGpsButton(enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
            Canvas(Modifier.size(26.dp).semantics { contentDescription = "Centre on my position" }) {
                val r = size.minDimension * 0.32f
                val sw = 2.dp.toPx()
                drawCircle(tint, r, style = Stroke(sw))
                drawCircle(tint, 3.dp.toPx())
                val c = center
                val outer = size.minDimension / 2f
                drawLine(tint, Offset(c.x, c.y - outer), Offset(c.x, c.y - r), sw)
                drawLine(tint, Offset(c.x, c.y + r), Offset(c.x, c.y + outer), sw)
                drawLine(tint, Offset(c.x - outer, c.y), Offset(c.x - r, c.y), sw)
                drawLine(tint, Offset(c.x + r, c.y), Offset(c.x + outer, c.y), sw)
            }
        }
    }
}

/** Fixed-length scale bar; its label changes with zoom and latitude (2 significant figures). */
@Composable
private fun ScaleBar(vp: MapViewport, tilePx: Float, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val barPx = with(LocalDensity.current) { SCALE_BAR_DP.toPx() }
    val meters = Geo.metersPerPixel(vp.lat, vp.scale(tilePx)) * barPx
    val halo = Shadow(Color.White, Offset.Zero, blurRadius = 6f)
    Column(modifier.width(SCALE_BAR_DP), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            Units.scaleLabel(meters, unit),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            style = TextStyle(shadow = halo),
        )
        Canvas(Modifier.width(SCALE_BAR_DP).height(8.dp)) {
            val y = size.height - 1.5.dp.toPx()
            val tick = size.height
            for ((color, sw) in listOf(Color.White to 4.dp.toPx(), Color.Black to 2.dp.toPx())) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), sw, cap = StrokeCap.Square)
                drawLine(color, Offset(0f, y), Offset(0f, y - tick), sw, cap = StrokeCap.Square)
                drawLine(color, Offset(size.width, y), Offset(size.width, y - tick), sw, cap = StrokeCap.Square)
            }
        }
    }
}

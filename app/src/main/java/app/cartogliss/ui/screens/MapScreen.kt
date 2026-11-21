package app.cartogliss.ui.screens

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

        ZoomControls(
            vp = vp,
            overzoomMode = overzoomMode,
            canIn = vp.canStep(1, overzoomMode, source.minZoom, source.maxZoom),
            canOut = vp.canStep(-1, overzoomMode, source.minZoom, source.maxZoom),
            onIn = { step(1) },
            onOut = { step(-1) },
            onToggle = { overzoomMode = !overzoomMode },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
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

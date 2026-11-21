package app.cartogliss.ui.download

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.cartogliss.appContainer
import app.cartogliss.map.MapViewport
import app.cartogliss.tiles.AreaSelection
import app.cartogliss.tiles.Cell
import app.cartogliss.tiles.DownloadJob
import app.cartogliss.tiles.DownloadWorker
import app.cartogliss.ui.map.DragHandler
import app.cartogliss.ui.map.MapOverlay
import app.cartogliss.ui.map.MapView
import app.cartogliss.ui.map.TILE_DP
import app.cartogliss.ui.map.ZoomControls
import app.cartogliss.ui.screens.ScreenScaffold
import app.cartogliss.ui.screens.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private enum class Mode(val label: String) { PAN("Move"), PAINT("Paint"), RECT("Rectangle"), ERASE("Erase") }

private val SELECTED = Color(0x59FFEB3B)
private val GRID = Color(0x33000000)
private val RECT_PREVIEW = Color(0xCCF9A825)

/** Above this, the estimate shows a warning; above [MAX_TILES], downloading is refused. */
private const val WARN_TILES = 50_000L
private const val MAX_TILES = 500_000L

/** Fallback when the source has no cached tiles to average over. */
private const val DEFAULT_TILE_BYTES = 25_000L

/** The painted area survives leaving and re-opening the screen (but not an app restart). */
object DownloadSelection {
    val area = AreaSelection()
}

@Composable
fun DownloadScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    val source = remember(s.viewport.sourceId) { container.sources.resolve(s.viewport.sourceId) }
    val keys by container.apiKeys.collectAsStateWithLifecycle()
    val tilePx = with(LocalDensity.current) { TILE_DP.toPx() }

    var vp by remember { mutableStateOf(MapViewport.from(s.viewport).copy(overzoom = 1.0).clampZoom(source.minZoom, source.maxZoom)) }
    var mode by remember { mutableStateOf(Mode.PAINT) }
    val area = DownloadSelection.area
    var version by remember { mutableIntStateOf(0) } // bumped on every selection change
    var levels by remember { mutableStateOf(setOf(vp.zoom)) }
    var wifiOnly by remember { mutableStateOf(false) }
    var rect by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var avgTileBytes by remember { mutableLongStateOf(DEFAULT_TILE_BYTES) }
    LaunchedEffect(source.id) {
        avgTileBytes = withContext(Dispatchers.IO) {
            val n = container.tileStore.tileCount(source.id)
            if (n > 50) container.tileStore.sizeBytes(source.id) / n else DEFAULT_TILE_BYTES
        }
    }

    // Grid: tiles two levels deeper than the view (~64 dp squares).
    val gridZ = min(vp.zoom + 2, 22)
    var mapW by remember { mutableIntStateOf(0) }
    var mapH by remember { mutableIntStateOf(0) }

    fun cellAt(p: Offset): Cell? {
        val sc = vp.scale(tilePx)
        val nx = vp.mx + (p.x - mapW / 2.0) / sc
        val ny = vp.my + (p.y - mapH / 2.0) / sc
        if (ny < 0 || ny >= 1) return null
        val n = 1 shl gridZ
        val x = Math.floorMod(floor(nx * n).toInt(), n)
        return Cell(gridZ, x, floor(ny * n).toInt())
    }

    val handler = remember(mode, vp, mapW, mapH) {
        if (mode == Mode.PAN) null else object : DragHandler {
            override fun start(at: Offset) {
                when (mode) {
                    Mode.RECT -> rect = at to at
                    else -> move(at)
                }
            }
            override fun move(at: Offset) {
                when (mode) {
                    Mode.PAINT -> cellAt(at)?.let { if (area.add(it)) version++ }
                    Mode.ERASE -> cellAt(at)?.let { if (area.remove(it)) version++ }
                    Mode.RECT -> rect = rect?.copy(second = at)
                    Mode.PAN -> Unit
                }
            }
            override fun end() {
                val r = rect ?: return
                rect = null
                val a = cellAt(Offset(min(r.first.x, r.second.x), min(r.first.y, r.second.y)))
                val b = cellAt(Offset(max(r.first.x, r.second.x), max(r.first.y, r.second.y)))
                if (a != null && b != null) {
                    val n = 1 shl gridZ
                    var x = a.x
                    while (true) {
                        for (y in a.y..b.y) area.add(Cell(gridZ, x, y))
                        if (x == b.x) break
                        x = (x + 1) % n
                    }
                    version++
                }
            }
            override fun cancel() {
                rect = null
            }
        }
    }

    val counts = remember(version, source.id) { (source.minZoom..source.maxZoom).associateWith { area.tileCount(it) } }
    val total = levels.filter { it in counts }.sumOf { counts[it] ?: 0 }
    val missingKey = source.missingKey(keys)

    // Running / queued downloads.
    val work by WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(DownloadWorker.UNIQUE_NAME)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val running = work.firstOrNull { it.state == WorkInfo.State.RUNNING }
    val queued = work.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    ScreenScaffold("Download maps · ${source.name}", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) { Text(m.label, fontSize = 12.sp) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { mapW = it.width; mapH = it.height }) {
                MapView(
                    viewport = vp,
                    source = source,
                    loader = container.tileLoader,
                    onPan = { dx, dy -> vp = vp.panned(dx, dy, tilePx) },
                    onStep = { st, f -> vp = vp.stepped(st, false, tilePx, source.minZoom, source.maxZoom, f.x, f.y) },
                    onPinchPending = {},
                    overlay = MapOverlay(),
                    dragHandler = handler,
                    modifier = Modifier.fillMaxSize(),
                )
                // Selection and grid on top (draw only; touches go to the map).
                Canvas(Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_EXPRESSION") version
                    val sc = vp.scale(tilePx)
                    val cx = size.width / 2.0
                    val cy = size.height / 2.0
                    fun sx(nx: Double) = ((nx - vp.mx) * sc + cx).toFloat()
                    fun sy(ny: Double) = ((ny - vp.my) * sc + cy).toFloat()
                    for (c in area.cells()) {
                        val n = (1 shl c.z).toDouble()
                        var x0 = c.x / n
                        x0 += kotlin.math.round(vp.mx - (x0 + 0.5 / n)) // nearest world copy
                        val l = sx(x0); val t = sy(c.y / n); val r = sx(x0 + 1 / n); val b = sy((c.y + 1) / n)
                        if (r < 0 || b < 0 || l > size.width || t > size.height) continue
                        drawRect(SELECTED, Offset(l, t), Size(r - l, b - t))
                    }
                    // Grid lines of the paint level.
                    val n = 1 shl gridZ
                    val step = sc / n
                    if (step >= 12) {
                        val firstX = floor((vp.mx - cx / sc) * n).toInt()
                        val lastX = floor((vp.mx + cx / sc) * n).toInt() + 1
                        for (i in firstX..lastX) { val x = sx(i.toDouble() / n); drawLine(GRID, Offset(x, 0f), Offset(x, size.height), 1f) }
                        val firstY = max(0, floor((vp.my - cy / sc) * n).toInt())
                        val lastY = min(n, floor((vp.my + cy / sc) * n).toInt() + 1)
                        for (j in firstY..lastY) { val y = sy(j.toDouble() / n); drawLine(GRID, Offset(0f, y), Offset(size.width, y), 1f) }
                    }
                    rect?.let { (a, b) ->
                        drawRect(RECT_PREVIEW, Offset(min(a.x, b.x), min(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(2.dp.toPx()))
                    }
                }

                // Zoom levels to download.
                Surface(
                    Modifier.align(Alignment.TopStart).padding(8.dp).width(84.dp).fillMaxHeight(0.92f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row {
                            TextButton(onClick = { levels = (source.minZoom..source.maxZoom).toSet() }, modifier = Modifier.weight(1f)) { Text("all", fontSize = 11.sp) }
                            TextButton(onClick = { levels = emptySet() }, modifier = Modifier.weight(1f)) { Text("none", fontSize = 11.sp) }
                        }
                        for (z in source.minZoom..source.maxZoom) {
                            FilterChip(
                                selected = z in levels,
                                onClick = { levels = if (z in levels) levels - z else levels + z },
                                label = {
                                    Column {
                                        Text("z$z", fontSize = 12.sp)
                                        if (!area.isEmpty()) Text(compact(counts[z] ?: 0), fontSize = 9.sp, lineHeight = 10.sp)
                                    }
                                },
                            )
                        }
                    }
                }
                ZoomControls(
                    vp = vp,
                    overzoomMode = false,
                    canIn = vp.canStep(1, false, source.minZoom, source.maxZoom),
                    canOut = vp.canStep(-1, false, source.minZoom, source.maxZoom),
                    onIn = { vp = vp.stepped(1, false, tilePx, source.minZoom, source.maxZoom) },
                    onOut = { vp = vp.stepped(-1, false, tilePx, source.minZoom, source.maxZoom) },
                    onToggle = {},
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }

            // Estimate and actions.
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(12.dp)) {
                val bytes = total * avgTileBytes
                Text(
                    if (area.isEmpty()) "Paint the area to download (squares are selected as you drag)."
                    else "${String.format(java.util.Locale.ROOT, "%,d", total)} tiles · about ${formatBytes(bytes)} (already cached tiles are skipped)",
                )
                when {
                    !source.bulkDownloadAllowed -> Text("${source.name} does not allow bulk downloading. Pick another map in the menu.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    missingKey != null -> Text("Needs a ${missingKey.label} key (Settings → Map server keys).", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    total > MAX_TILES -> Text("Too many tiles (limit ${compact(MAX_TILES)}): unselect the deepest levels or paint a smaller area.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    total > WARN_TILES -> Text("That's a lot of tiles: mind the server's limits (e.g. Thunderforest's free tier is 150k tiles/month).", color = MaterialTheme.colorScheme.tertiary, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi only", modifier = Modifier.weight(1f))
                    Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { area.clear(); version++ }, enabled = !area.isEmpty()) { Text("Clear area") }
                    Button(
                        enabled = total in 1..MAX_TILES && source.bulkDownloadAllowed && missingKey == null,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            DownloadWorker.enqueue(
                                context,
                                DownloadJob(source.id, source.name, levels.sorted(), area.cells().toList()),
                                wifiOnly,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Download") }
                }
                running?.let { w ->
                    val done = w.progress.getInt(DownloadWorker.KEY_DONE, 0)
                    val tot = w.progress.getInt(DownloadWorker.KEY_TOTAL, 0)
                    val failed = w.progress.getInt(DownloadWorker.KEY_FAILED, 0)
                    Text("Downloading: $done / $tot" + (if (failed > 0) " · $failed failed" else "") + (if (queued > 0) " · $queued queued" else ""), fontSize = 13.sp)
                    LinearProgressIndicator(progress = { if (tot > 0) done / tot.toFloat() else 0f }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    TextButton(onClick = { WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.UNIQUE_NAME) }) { Text("Cancel downloads") }
                }
                if (running == null && queued > 0) Text("$queued download(s) waiting for network", fontSize = 13.sp)
            }
        }
    }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1fM", n / 1e6)
    n >= 10_000 -> "${n / 1000}k"
    n >= 1000 -> String.format(java.util.Locale.ROOT, "%.1fk", n / 1e3)
    else -> n.toString()
}

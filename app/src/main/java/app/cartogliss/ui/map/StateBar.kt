package app.cartogliss.ui.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val STATE_BAR_HEIGHT = 56.dp

data class StateBarState(
    val showingStats: Boolean,
    /** "1.23 km", or null when there is no target. "—" when there is a target but no position. */
    val targetDistance: String?,
    val targetName: String?,
    val gpsOn: Boolean,
    val satsUsed: Int,
    val satsVisible: Int,
    val recording: Boolean,
    /** "1.2 km · 0:42" while recording. */
    val recordSummary: String?,
    val recordAvailable: Boolean,
)

data class StateBarActions(
    val onToggleStats: () -> Unit,
    val onTargetTap: () -> Unit,
    val onTargetLongPress: () -> Unit,
    val onPin: () -> Unit,
    val onToggleGps: () -> Unit,
    val onRecord: () -> Unit,
    val onMenu: () -> Unit,
)

@Composable
fun StateBar(s: StateBarState, a: StateBarActions, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            Modifier.navigationBarsPadding().height(STATE_BAR_HEIGHT).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = MaterialTheme.colorScheme.onSurface
            BarCell(onClick = a.onToggleStats, label = if (s.showingStats) "Show map" else "Show stats") {
                if (s.showingStats) MapIcon(tint) else StatsIcon(tint)
            }
            TargetCell(s, a, Modifier.weight(1f))
            BarCell(onClick = a.onPin, label = "Map centre") {
                Icon(Icons.Filled.Place, contentDescription = null)
            }
            BarCell(onClick = a.onToggleGps, label = if (s.gpsOn) "Turn GPS off" else "Turn GPS on") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SatelliteIcon(tint, off = !s.gpsOn)
                    Text(if (s.gpsOn) "${s.satsUsed}/${s.satsVisible}" else "off", fontSize = 10.sp, lineHeight = 11.sp)
                }
            }
            BarCell(onClick = a.onRecord, label = if (s.recording) "Stop recording" else "Record track", enabled = s.recordAvailable) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RecordIcon(s.recording, if (s.recordAvailable) tint else tint.copy(alpha = 0.38f))
                    if (s.recording && s.recordSummary != null) {
                        Text(s.recordSummary, fontSize = 10.sp, lineHeight = 11.sp, color = RECORD_RED, maxLines = 1)
                    }
                }
            }
            BarCell(onClick = a.onMenu, label = "Menu") {
                Icon(Icons.Filled.Menu, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.TargetCell(s: StateBarState, a: StateBarActions, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .combinedClickable(
                enabled = s.targetDistance != null,
                onClickLabel = "Centre on target",
                onLongClickLabel = "Clear target",
                onClick = a.onTargetTap,
                onLongClick = a.onTargetLongPress,
            )
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (s.targetDistance == null) {
            Text("No target", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
        } else {
            Text(s.targetDistance, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TARGET_GREEN_UI, maxLines = 1)
            s.targetName?.let {
                Text(it, fontSize = 11.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val TARGET_GREEN_UI = Color(0xFF2E7D32)

@Composable
private fun BarCell(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxHeight()
            .widthIn(min = 48.dp)
            .combinedClickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

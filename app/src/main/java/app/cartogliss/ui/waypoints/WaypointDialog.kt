package app.cartogliss.ui.waypoints

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.cartogliss.geo.Coords
import app.cartogliss.geo.Geo
import app.cartogliss.geo.Units
import app.cartogliss.kml.Waypoint
import app.cartogliss.location.Fix
import app.cartogliss.settings.AngleFormat
import app.cartogliss.settings.DistanceUnit
import java.util.Locale

/** A waypoint's details with "Go To" (set as target) and optionally "Show on map". */
@Composable
fun WaypointDialog(
    wp: Waypoint,
    fix: Fix?,
    angleFormat: AngleFormat,
    distanceUnit: DistanceUnit,
    onGoTo: () -> Unit,
    onDismiss: () -> Unit,
    onShowOnMap: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(wp.name) },
        text = {
            Column {
                Text(wp.collectionTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text(Coords.formatLat(wp.lat, angleFormat), fontFamily = FontFamily.Monospace)
                Text(Coords.formatLon(wp.lon, angleFormat), fontFamily = FontFamily.Monospace)
                wp.description?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, maxLines = 6)
                }
                fix?.let {
                    val d = Geo.distance(it.lat, it.lon, wp.lat, wp.lon)
                    val b = Geo.bearing(it.lat, it.lon, wp.lat, wp.lon)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${Units.distance(d, distanceUnit)} · ${String.format(Locale.ROOT, "%03.0f°", b)} ${Geo.compass16(b)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onGoTo) { Text("Go To") } },
        dismissButton = {
            Row {
                onShowOnMap?.let { TextButton(onClick = it) { Text("Show on map") } }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

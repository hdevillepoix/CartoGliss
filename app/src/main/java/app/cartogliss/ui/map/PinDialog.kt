package app.cartogliss.ui.map

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
import app.cartogliss.location.Fix
import app.cartogliss.settings.AngleFormat
import app.cartogliss.settings.DistanceUnit
import java.util.Locale

/** Pin popup: facts about the map centre, and actions on it. */
@Composable
fun PinDialog(
    lat: Double,
    lon: Double,
    fix: Fix?,
    angleFormat: AngleFormat,
    distanceUnit: DistanceUnit,
    onGoTo: () -> Unit,
    onAddWaypoint: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map centre") },
        text = {
            Column {
                Text(Coords.formatLat(lat, angleFormat), fontFamily = FontFamily.Monospace)
                Text(Coords.formatLon(lon, angleFormat), fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(12.dp))
                if (fix == null) {
                    Text("No GPS position", color = MaterialTheme.colorScheme.outline)
                } else {
                    val d = Geo.distance(fix.lat, fix.lon, lat, lon)
                    val b = Geo.bearing(fix.lat, fix.lon, lat, lon)
                    Text("From your position:")
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
                TextButton(onClick = onAddWaypoint) { Text("Add waypoint") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

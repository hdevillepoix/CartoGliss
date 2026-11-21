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
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.cartogliss.appContainer
import app.cartogliss.kml.PlacemarkRef
import app.cartogliss.ui.map.KmlFeature
import kotlinx.coroutines.launch
import java.util.Locale

/** A waypoint's details with "Go To" (set as target), and optionally "Show on map" and "Delete". */
@Composable
fun WaypointDialog(
    wp: Waypoint,
    fix: Fix?,
    angleFormat: AngleFormat,
    distanceUnit: DistanceUnit,
    onGoTo: () -> Unit,
    onDismiss: () -> Unit,
    onShowOnMap: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
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
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                onShowOnMap?.let { TextButton(onClick = it) { Text("Show on map") } }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

/** Details of a tapped line or area, with "Delete". */
@Composable
fun FeatureDialog(f: KmlFeature, distanceUnit: DistanceUnit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(f.name) },
        text = {
            Column {
                Text(f.collectionTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text(if (f.isArea) "Area · perimeter ${Units.distance(f.lengthM, distanceUnit)}" else "Line · ${Units.distance(f.lengthM, distanceUnit)}")
                f.description?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, maxLines = 6)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
    )
}

/**
 * Confirms, then deletes the placemark [ref] from its KML file. A placemark with several geometries
 * (MultiGeometry) is deleted as a whole.
 */
@Composable
fun DeletePlacemarkDialog(name: String, collectionTitle: String, ref: PlacemarkRef, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Delete $name?") },
        text = { Text("It is removed from $collectionTitle (the KML file is edited).") },
        confirmButton = {
            TextButton(onClick = {
                onDone()
                container.appScope.launch {
                    val ok = runCatching { container.kml.deletePlacemark(ref) }.getOrElse { false }
                    if (!ok) scope.launch {
                        Toast.makeText(context, "Not deleted: the file changed or can't be written", Toast.LENGTH_LONG).show()
                    }
                }
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

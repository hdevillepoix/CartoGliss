package app.cartogliss.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cartogliss.astro.Astro
import app.cartogliss.astro.AstroInfo
import app.cartogliss.geo.Coords
import app.cartogliss.geo.Units
import app.cartogliss.location.Fix
import app.cartogliss.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val HMS = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Stats view. Uses the GPS position when there is one, otherwise the map centre (labelled as such)
 * for the sun/moon/UV figures.
 */
@Composable
fun StatsView(
    fix: Fix?,
    nowNanos: Long,
    mapCentreLat: Double,
    mapCentreLon: Double,
    settings: Settings,
    modifier: Modifier = Modifier,
) {
    val lat = fix?.lat ?: mapCentreLat
    val lon = fix?.lon ?: mapCentreLon
    val alt = fix?.altitudeM ?: 0.0
    var astro by remember { mutableStateOf<AstroInfo?>(null) }

    // Recompute every minute, and when the position moves by ~1 km (0.01°).
    val key = Triple((lat * 100).roundToInt(), (lon * 100).roundToInt(), (alt / 100).roundToInt())
    LaunchedEffect(key) {
        while (true) {
            astro = withContext(Dispatchers.Default) { Astro.compute(lat, lon, alt, ZonedDateTime.now()) }
            delay(60_000)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Section("Position")
        if (fix == null) {
            Row2("GPS", "no fix yet")
        } else {
            Row2("Latitude", Coords.formatLat(fix.lat, settings.angleFormat), mono = true)
            Row2("Longitude", Coords.formatLon(fix.lon, settings.angleFormat), mono = true)
            Row2("Altitude", fix.altitudeM?.let { Units.altitude(it, settings.distanceUnit) } ?: "—")
            Row2("Accuracy", fix.accuracyM?.let { Units.distance(it.toDouble(), settings.distanceUnit) } ?: "—")
            val ageS = fix.ageMs(nowNanos) / 1000
            val at = Instant.ofEpochMilli(fix.timeMs).atZone(ZoneId.systemDefault())
            Row2("Last fix", "${HMS.format(at)}  (${ageLabel(ageS)})")
            Row2("Speed", fix.speedMps?.let { Units.speed(it.toDouble(), settings.speedUnit) } ?: "—")
        }

        Section(if (fix == null) "Sun & Moon (at map centre)" else "Sun & Moon")
        val a = astro
        if (a == null) {
            Row2("", "…")
        } else {
            Row2("Sunrise", a.sunRise?.let(HM::format) ?: if (a.sunAlwaysUp) "sun up all day" else if (a.sunAlwaysDown) "sun down all day" else "—")
            Row2("Sunset", a.sunSet?.let(HM::format) ?: "—")
            Row2("Moonrise", a.moonRise?.let(HM::format) ?: "—")
            Row2("Moonset", a.moonSet?.let(HM::format) ?: "—")
            Row2("Sun elevation", String.format(Locale.ROOT, "%.1f°", a.sunElevationDeg))
            Row2("UV index now", String.format(Locale.ROOT, "%.1f", a.uvNow))
            Row2(
                "UV index 3",
                a.nextUv3?.let { c -> "${if (c.rising) "rises past" else "drops below"} 3 at ${HM.format(c.time.atZone(ZoneId.systemDefault()))}" }
                    ?: "—",
            )
            Text(
                "Clear-sky estimate from sun elevation and altitude; clouds and ozone are not taken into account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun ageLabel(s: Long): String = when {
    s < 60 -> "${s} s ago"
    s < 3600 -> "${s / 60} min ago"
    else -> "${s / 3600} h ago"
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun Row2(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            modifier = Modifier.weight(0.6f),
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

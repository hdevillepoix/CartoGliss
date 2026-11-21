package app.cartogliss.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cartogliss.tiles.TileSource

/** One entry of the layers popup. [enabled] is false while the source's API key is missing. */
data class LayerChoice(val source: TileSource, val enabled: Boolean)

/**
 * Map button that opens a short list of the starred base maps (short names, no categories), plus
 * "More…" for the full list.
 */
@Composable
fun LayersButton(choices: List<LayerChoice>, currentId: String, onSelect: (String) -> Unit, onMore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
        ) {
            IconButton(onClick = { open = true }, modifier = Modifier.size(56.dp)) {
                LayersIcon(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surface, Modifier.semantics { contentDescription = "Base map" })
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (src, enabled) ->
                val selected = src.id == currentId
                DropdownMenuItem(
                    text = { Text(src.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = "Current map") }) else null,
                    enabled = enabled,
                    onClick = {
                        open = false
                        onSelect(src.id)
                    },
                )
            }
            if (choices.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(text = { Text("More…") }, onClick = { open = false; onMore() })
        }
    }
}

/** Two stacked diamonds. */
@Composable
private fun LayersIcon(tint: Color, fill: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        fun diamond(cy: Float) = Path().apply {
            moveTo(w * 0.5f, cy - h * 0.2f)
            lineTo(w * 0.92f, cy)
            lineTo(w * 0.5f, cy + h * 0.2f)
            lineTo(w * 0.08f, cy)
            close()
        }
        val stroke = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round)
        drawPath(diamond(h * 0.6f), tint, style = stroke)
        drawPath(diamond(h * 0.4f), fill, style = Fill)
        drawPath(diamond(h * 0.4f), tint, style = stroke)
    }
}

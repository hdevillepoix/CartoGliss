package app.cartogliss.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cartogliss.appContainer

/** Main map screen. Milestone 0: placeholder showing the persisted viewport. */
@Composable
fun MapScreen(onOpenMenu: () -> Unit) {
    val repo = LocalContext.current.appContainer.settings
    val settings by repo.settings.collectAsState(initial = null)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        val vp = settings?.viewport
        Text(
            if (vp == null) "…" else "Map (milestone 1)\n${vp.sourceId} · ${vp.zoom}x${vp.overzoom}\n%.5f, %.5f".format(vp.lat, vp.lon),
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colorScheme.outline,
        )
        FilledIconButton(
            onClick = onOpenMenu,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu")
        }
    }
}

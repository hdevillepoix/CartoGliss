package app.cartogliss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.cartogliss.ui.nav.CartoGlissNavHost
import app.cartogliss.ui.theme.CartoGlissTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CartoGlissTheme {
                CartoGlissNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Tracks and waypoints may live in a shared folder that other apps change.
        appContainer.rescanFiles()
    }
}

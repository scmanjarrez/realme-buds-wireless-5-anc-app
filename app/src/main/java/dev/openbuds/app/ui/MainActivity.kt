package dev.openbuds.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openbuds.app.OpenBudsApp
import dev.openbuds.app.ui.theme.OpenBudsTheme
import dev.openbuds.app.ui.theme.isDarkTheme

/**
 * Single activity hosting the Compose UI.
 *
 * On creation, it enables edge-to-edge drawing and sets the [OpenBudsTheme] wrapping the root
 * [BudsApp] composable as the content. The theme follows the user's stored [ThemeMode]; the system
 * bar style is re-applied whenever the resolved dark/light appearance changes.
 */
class MainActivity : ComponentActivity() {
    /**
     * Enable edge-to-edge drawing and compose the root UI. Runs on the main thread.
     *
     * @param savedInstanceState state saved by a previous instance, or null on a fresh launch.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as OpenBudsApp
            val mode by app.themePreferences.mode.collectAsStateWithLifecycle()
            val dark = isDarkTheme(mode)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                )
                onDispose {}
            }
            OpenBudsTheme(themeMode = mode) {
                BudsApp()
            }
        }
    }
}

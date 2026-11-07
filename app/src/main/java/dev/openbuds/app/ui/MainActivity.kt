package dev.openbuds.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.openbuds.app.ui.theme.OpenBudsTheme

/**
 * Single activity hosting the Compose UI.
 *
 * On creation, it enables edge-to-edge drawing and sets the [OpenBudsTheme] wrapping the root
 * [BudsApp] composable as the content.
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
            OpenBudsTheme {
                BudsApp()
            }
        }
    }
}

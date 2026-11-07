package dev.openbuds.app.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-selectable application theme.
 *
 * - [SYSTEM] follows the system dark mode setting and uses dynamic color on Android 12+.
 * - [LIGHT] always uses the fixed light palette.
 * - [DARK] always uses the pure black OLED palette.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persists the selected [ThemeMode] in the "settings" SharedPreferences file.
 *
 * The initial value is read synchronously on construction so the first frame already uses the
 * stored theme. A missing or invalid stored value falls back to [ThemeMode.SYSTEM].
 *
 * @param context any context; only its application context is retained by SharedPreferences.
 */
class ThemePreferences(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modeState = MutableStateFlow(readStoredMode())

    /** The currently selected theme mode. Emits the new value after [setMode] is called. */
    val mode: StateFlow<ThemeMode> = modeState.asStateFlow()

    /**
     * Stores [mode] asynchronously and publishes it through [mode].
     *
     * @param mode the theme mode to select.
     */
    fun setMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        modeState.value = mode
    }

    private fun readStoredMode(): ThemeMode {
        val stored = runCatching { prefs.getString(KEY_THEME_MODE, null) }.getOrNull()
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_THEME_MODE = "theme_mode"
    }
}

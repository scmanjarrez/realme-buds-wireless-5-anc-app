package dev.openbuds.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Applies the OpenBuds Material 3 theme to [content].
 *
 * The color scheme depends on [themeMode]:
 * - [ThemeMode.SYSTEM]: on Android 12 (API 31) and newer, the wallpaper-based dynamic scheme
 *   matching the system dark setting; on older versions, [FallbackDarkColorScheme] or
 *   [FallbackLightColorScheme] depending on the system dark setting.
 * - [ThemeMode.LIGHT]: always [FallbackLightColorScheme].
 * - [ThemeMode.DARK]: always [OledDarkColorScheme].
 *
 * @param themeMode the selected theme mode. Defaults to [ThemeMode.SYSTEM].
 * @param content the composable content that reads the theme through [MaterialTheme].
 */
@Composable
fun OpenBudsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = isDarkTheme(themeMode)
    val colorScheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        val context = LocalContext.current
                        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }

                    darkTheme -> {
                        FallbackDarkColorScheme
                    }

                    else -> {
                        FallbackLightColorScheme
                    }
                }
            }

            ThemeMode.LIGHT -> {
                FallbackLightColorScheme
            }

            ThemeMode.DARK -> {
                OledDarkColorScheme
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/**
 * Returns whether [themeMode] resolves to a dark appearance.
 *
 * [ThemeMode.SYSTEM] follows the system dark mode setting; [ThemeMode.LIGHT] and
 * [ThemeMode.DARK] are fixed. Used by [OpenBudsTheme] and by the activity to configure the
 * system bars so they stay legible.
 *
 * @param themeMode the selected theme mode.
 * @return `true` if the resolved appearance is dark.
 */
@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean =
    when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

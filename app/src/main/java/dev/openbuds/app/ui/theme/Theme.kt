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
 * The color scheme is chosen in this order: on Android 12 (API 31) and newer with [dynamicColor]
 * enabled, the wallpaper-based dynamic scheme; otherwise [FallbackDarkColorScheme] or
 * [FallbackLightColorScheme], depending on [darkTheme]. Runs on the composition thread and has
 * no side effects beyond providing the theme.
 *
 * @param darkTheme whether to use dark colors. Defaults to the system dark mode setting.
 * @param dynamicColor whether to prefer dynamic colors when the device supports them.
 * @param content the composable content that reads the theme through [MaterialTheme].
 */
@Composable
fun OpenBudsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

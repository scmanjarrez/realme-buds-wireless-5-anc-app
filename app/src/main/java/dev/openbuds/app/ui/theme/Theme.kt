package dev.openbuds.app.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Visual style applied to cards, resolved from the active [ThemeMode].
 *
 * @property containerColor the fill color of a card.
 * @property border the optional outline of a card, or `null` for no border.
 */
data class CardStyle(
    val containerColor: Color,
    val border: BorderStroke?,
)

/**
 * The [CardStyle] provided to the composition by [OpenBudsTheme]. The default is unspecified and
 * has no border, so cards fall back to their own defaults outside the theme.
 */
val LocalCardStyle = staticCompositionLocalOf { CardStyle(Color.Unspecified, null) }

/**
 * Applies the OpenBuds Material 3 Expressive theme to [content].
 *
 * Motion uses [MotionScheme.expressive]. The color scheme depends on the device API level:
 * - API 31 (Android 12) and newer: wallpaper-based dynamic color in every [ThemeMode]. The mode
 *   only decides light or dark. [ThemeMode.LIGHT] and [ThemeMode.SYSTEM] in light use
 *   [withSettingsSurfaces]; [ThemeMode.DARK] uses the standard dynamic dark scheme; and
 *   [ThemeMode.BLACK] uses [withOledSurfaces] so the base surfaces are pure black.
 * - API below 31: the fallback schemes, since dynamic color is unavailable. [ThemeMode.SYSTEM]
 *   uses [FallbackDarkColorScheme] or [FallbackLightColorScheme] depending on the system dark
 *   setting, [ThemeMode.LIGHT] uses [FallbackLightColorScheme], [ThemeMode.DARK] uses
 *   [FallbackDarkColorScheme], and [ThemeMode.BLACK] uses [OledDarkColorScheme].
 *
 * The card style provided through [LocalCardStyle] also depends on the mode: black cards have a
 * border, light cards use the lowest container tier, and the other dark modes use the default
 * container tier.
 *
 * @param themeMode the selected theme mode. Defaults to [ThemeMode.SYSTEM].
 * @param content the composable content that reads the theme through `MaterialTheme`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenBudsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = isDarkTheme(themeMode)
    val colorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            val dynamicDark = dynamicDarkColorScheme(context)
            when {
                !darkTheme -> dynamicLightColorScheme(context).withSettingsSurfaces()
                themeMode == ThemeMode.BLACK -> dynamicDark.withOledSurfaces()
                else -> dynamicDark
            }
        } else {
            val lightFallback = FallbackLightColorScheme.withSettingsSurfaces()
            when (themeMode) {
                ThemeMode.SYSTEM -> if (darkTheme) FallbackDarkColorScheme else lightFallback
                ThemeMode.LIGHT -> lightFallback
                ThemeMode.DARK -> FallbackDarkColorScheme
                ThemeMode.BLACK -> OledDarkColorScheme
            }
        }

    val blackBorder = BorderStroke(1.dp, colorScheme.outlineVariant)
    val cardStyle =
        when {
            themeMode == ThemeMode.BLACK -> CardStyle(colorScheme.background, blackBorder)
            !darkTheme -> CardStyle(colorScheme.surfaceContainerLowest, null)
            else -> CardStyle(colorScheme.surfaceContainer, null)
        }

    CompositionLocalProvider(LocalCardStyle provides cardStyle) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

/**
 * Sets the base surfaces of a light scheme to the settings-style tinted background, so cards
 * (`surfaceContainerLowest`) stand out lighter than the page, as in the Android settings app.
 *
 * Only `background` and `surface` are overridden.
 *
 * @return a copy of this scheme with the settings-style base surfaces.
 */
private fun ColorScheme.withSettingsSurfaces(): ColorScheme =
    copy(
        background = surfaceContainer,
        surface = surfaceContainer,
    )

/**
 * Replaces the base surfaces of a dynamic dark scheme with pure black for OLED screens. Used only
 * by [ThemeMode.BLACK].
 *
 * Only `background`, `surface`, `surfaceDim` and `surfaceContainerLowest` are overridden. The
 * rest of the scheme stays dynamic so the bordered cards stand out from the black base.
 *
 * @return a copy of this scheme with pure-black base surfaces.
 */
private fun ColorScheme.withOledSurfaces(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
    )

/**
 * Returns whether [themeMode] resolves to a dark appearance.
 *
 * [ThemeMode.SYSTEM] follows the system dark mode setting; [ThemeMode.LIGHT] is fixed light;
 * [ThemeMode.DARK] and [ThemeMode.BLACK] are fixed dark. Used by [OpenBudsTheme] and by the
 * activity to configure the system bars so they stay legible.
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
        ThemeMode.BLACK -> true
    }

package dev.openbuds.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Light fallback color scheme for the OpenBuds Material 3 theme.
 *
 * Used by [OpenBudsTheme] in [ThemeMode.Light], and in [ThemeMode.System] when dynamic color is
 * unavailable (Android below 12, API 31). Token roles, grouped by Material 3 family:
 *
 * - Primary: [ColorScheme.primary] and its on/container pair. Main brand accent for filled
 *   buttons and selected states. [ColorScheme.inversePrimary] is the accent shown on inverse surfaces.
 * - Secondary: [ColorScheme.secondary] family. Less prominent accents such as chips and
 *   secondary buttons.
 * - Tertiary: [ColorScheme.tertiary] family. Contrasting accent for highlights.
 * - Background and surface: [ColorScheme.background], [ColorScheme.surface] and the
 *   [ColorScheme.surfaceContainerLowest] to [ColorScheme.surfaceContainerHighest] ladder.
 *   Higher container tiers are more prominent.
 * - Surface variant: [ColorScheme.surfaceVariant] and [ColorScheme.onSurfaceVariant] for
 *   secondary text and icons.
 * - Surface tones: [ColorScheme.surfaceTint] tints elevated surfaces. [ColorScheme.surfaceBright]
 *   and [ColorScheme.surfaceDim] are the lightest and darkest surface tones.
 * - Inverse: [ColorScheme.inverseSurface] and [ColorScheme.inverseOnSurface] for high-contrast
 *   surfaces such as snack bars.
 * - Error: [ColorScheme.error] family for error text and destructive states.
 * - Outline: [ColorScheme.outline] for emphasized borders, [ColorScheme.outlineVariant] for
 *   decorative dividers.
 * - Scrim: [ColorScheme.scrim] is the overlay behind modal content.
 */
internal val FallbackLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF3F51B5),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDE1FF),
        onPrimaryContainer = Color(0xFF001257),
        inversePrimary = Color(0xFFBAC3FF),
        secondary = Color(0xFF5B5D72),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E1F9),
        onSecondaryContainer = Color(0xFF181A2C),
        tertiary = Color(0xFF78536B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD7F0),
        onTertiaryContainer = Color(0xFF2D1228),
        background = Color(0xFFFBF8FF),
        onBackground = Color(0xFF1B1B21),
        surface = Color(0xFFFBF8FF),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE3E1EC),
        onSurfaceVariant = Color(0xFF46464F),
        surfaceTint = Color(0xFF3F51B5),
        inverseSurface = Color(0xFF303036),
        inverseOnSurface = Color(0xFFF2EFF7),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF767680),
        outlineVariant = Color(0xFFC6C5D0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFBF8FF),
        surfaceDim = Color(0xFFDBD9E0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F2FA),
        surfaceContainer = Color(0xFFEFEDF4),
        surfaceContainerHigh = Color(0xFFE9E7EF),
        surfaceContainerHighest = Color(0xFFE3E1E9),
    )

/**
 * Dark fallback color scheme for the OpenBuds Material 3 theme.
 *
 * Used by [OpenBudsTheme] under the same conditions as [FallbackLightColorScheme], when the app
 * is in dark mode. Each token has the same role as in [FallbackLightColorScheme]; the tones are
 * adjusted for dark backgrounds, so containers are darker and on-colors are lighter.
 */
internal val FallbackDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFBAC3FF),
        onPrimary = Color(0xFF062189),
        primaryContainer = Color(0xFF243A9C),
        onPrimaryContainer = Color(0xFFDDE1FF),
        inversePrimary = Color(0xFF3F51B5),
        secondary = Color(0xFFC4C5DD),
        onSecondary = Color(0xFF2D2F42),
        secondaryContainer = Color(0xFF434559),
        onSecondaryContainer = Color(0xFFE0E1F9),
        tertiary = Color(0xFFE4B9D3),
        onTertiary = Color(0xFF442537),
        tertiaryContainer = Color(0xFF5D3C52),
        onTertiaryContainer = Color(0xFFFFD7F0),
        background = Color(0xFF121318),
        onBackground = Color(0xFFE4E1E9),
        surface = Color(0xFF121318),
        onSurface = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF46464F),
        onSurfaceVariant = Color(0xFFC6C5D0),
        surfaceTint = Color(0xFFBAC3FF),
        inverseSurface = Color(0xFFE4E1E9),
        inverseOnSurface = Color(0xFF303036),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF90909A),
        outlineVariant = Color(0xFF46464F),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF38393F),
        surfaceDim = Color(0xFF121318),
        surfaceContainerLowest = Color(0xFF0D0E13),
        surfaceContainerLow = Color(0xFF1B1B21),
        surfaceContainer = Color(0xFF1F1F25),
        surfaceContainerHigh = Color(0xFF292A2F),
        surfaceContainerHighest = Color(0xFF34343A),
    )

/**
 * Pure-black dark color scheme for the OpenBuds Material 3 theme.
 *
 * Derived from [FallbackDarkColorScheme]. Background and base surfaces are pure black so that
 * OLED pixels stay off, and the container tiers sit just above black so cards remain distinct
 * without lighting many pixels. Only the Dark theme mode uses this scheme; System and Light
 * keep using the fallback schemes.
 */
internal val OledDarkColorScheme: ColorScheme =
    FallbackDarkColorScheme.copy(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceDim = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0C0C0E),
        surfaceContainer = Color(0xFF141416),
        surfaceContainerHigh = Color(0xFF1C1C1F),
        surfaceContainerHighest = Color(0xFF26262A),
        surfaceBright = Color(0xFF2C2C30),
        surfaceVariant = Color(0xFF26262A),
        outlineVariant = Color(0xFF2E2E33),
    )

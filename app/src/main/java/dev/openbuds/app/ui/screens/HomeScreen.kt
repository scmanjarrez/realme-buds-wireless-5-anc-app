package dev.openbuds.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Earbuds
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NoiseAware
import androidx.compose.material.icons.outlined.NoiseControlOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.PhonelinkRing
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SpatialAudio
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openbuds.app.R
import dev.openbuds.app.data.BudsState
import dev.openbuds.app.protocol.Eq
import dev.openbuds.app.protocol.Feature
import dev.openbuds.app.protocol.Noise
import dev.openbuds.app.ui.BudsViewModel
import dev.openbuds.app.ui.Routes
import dev.openbuds.app.ui.components.BudsScaffold
import dev.openbuds.app.ui.components.DeviceStatusCard
import dev.openbuds.app.ui.components.InsetDivider
import dev.openbuds.app.ui.components.LabeledSlider
import dev.openbuds.app.ui.components.NavRow
import dev.openbuds.app.ui.components.NoiseModeOption
import dev.openbuds.app.ui.components.NoiseModeSelector
import dev.openbuds.app.ui.components.SettingRow
import dev.openbuds.app.ui.components.SettingsCard
import dev.openbuds.app.ui.components.SwitchRow
import dev.openbuds.app.ui.components.ThemeDialog
import dev.openbuds.app.ui.theme.ThemeMode

/**
 * Active EQ id of the custom equalizer, as reported by the earbuds. Wire value 0x04.
 *
 * See 'Queries (0x01xx)' in docs/PROTOCOL.md for the active-EQ query.
 */
private const val EQ_CUSTOM = 0x04

/** Project repository opened by the About row of the app card. */
private const val REPO_URL = "https://github.com/scmanjarrez/realme-buds-wireless-5-anc-app"

/** Dynamic audio sliders: protocol type to label, in display order. */
private val DYNAMIC_SLIDERS =
    listOf(
        1 to R.string.home_dynamic_low,
        2 to R.string.home_dynamic_mid,
        3 to R.string.home_dynamic_high,
    )

/** ANC levels in chip order, with their labels. */
private val ANC_LEVELS =
    listOf(
        Noise.SMART to R.string.home_anc_smart,
        Noise.MAX to R.string.home_anc_max,
        Noise.MODERATE to R.string.home_anc_moderate,
        Noise.MILD to R.string.home_anc_mild,
    )

/** The three segments of the noise-control selector. */
private enum class NoiseSegment { ANC, OFF, TRANSPARENCY }

/**
 * Show the home screen with battery, firmware and setting cards.
 *
 * Pull to refresh reloads the earbud state through [BudsViewModel.reload].
 *
 * @param buds current earbud state; null values fall back to defaults or hide their row.
 * @param viewModel receives every setting change made from the cards.
 * @param onNavigate called with the route to open when a row that opens a sub-screen is tapped.
 */
@Composable
fun HomeScreen(
    buds: BudsState,
    viewModel: BudsViewModel,
    onNavigate: (String) -> Unit,
) {
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    BudsScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        refreshing = refreshing,
        onRefresh = viewModel::reload,
    ) {
        DeviceStatusCard(
            name = stringResource(R.string.home_title),
            battery = buds.battery,
        )
        NoiseCard(buds = buds, viewModel = viewModel)
        SoundCard(buds = buds, viewModel = viewModel, onNavigate = onNavigate)
        ConnectivityCard(buds = buds, onNavigate = onNavigate)
        ControlsCard(buds = buds, viewModel = viewModel, onNavigate = onNavigate)
        AppCard(viewModel = viewModel, firmware = buds.firmware)
    }
}

/** Map a noise-mode value to its segment; null when unknown. */
private fun segmentOf(noise: Int?): NoiseSegment? =
    when (noise) {
        Noise.NORMAL -> NoiseSegment.OFF
        Noise.TRANSPARENT -> NoiseSegment.TRANSPARENCY
        Noise.MILD, Noise.MAX, Noise.MODERATE, Noise.SMART -> NoiseSegment.ANC
        else -> null
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NoiseCard(
    buds: BudsState,
    viewModel: BudsViewModel,
) {
    var ancLevel by rememberSaveable { mutableIntStateOf(Noise.SMART) }
    val noise = buds.noise
    LaunchedEffect(noise) {
        if (noise != null && segmentOf(noise) == NoiseSegment.ANC) ancLevel = noise
    }

    val selected = segmentOf(noise)
    val options =
        listOf(
            NoiseModeOption(NoiseSegment.ANC, R.string.home_noise_anc_short, Icons.Outlined.NoiseAware),
            NoiseModeOption(NoiseSegment.OFF, R.string.home_noise_off, Icons.Outlined.NoiseControlOff),
            NoiseModeOption(NoiseSegment.TRANSPARENCY, R.string.home_noise_transparency, Icons.Outlined.Hearing),
        )

    SettingsCard(
        title = stringResource(R.string.home_noise_control),
        icon = Icons.Outlined.NoiseAware,
    ) {
        NoiseModeSelector(
            options = options,
            selected = selected,
            onSelect = { segment ->
                if (selected != segment) {
                    when (segment) {
                        NoiseSegment.ANC -> viewModel.setNoiseMode(ancLevel)
                        NoiseSegment.OFF -> viewModel.setNoiseMode(Noise.NORMAL)
                        NoiseSegment.TRANSPARENCY -> viewModel.setNoiseMode(Noise.TRANSPARENT)
                    }
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp),
        )

        AnimatedVisibility(visible = selected == NoiseSegment.ANC) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ANC_LEVELS.forEach { (level, labelRes) ->
                    FilterChip(
                        selected = noise == level,
                        onClick = {
                            ancLevel = level
                            viewModel.setNoiseMode(level)
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }

        AnimatedVisibility(visible = noise == Noise.TRANSPARENT) {
            Column {
                InsetDivider()
                FeatureSwitchRow(
                    title = stringResource(R.string.home_enhance_voices),
                    icon = Icons.Outlined.RecordVoiceOver,
                    featureId = Feature.ENHANCE_VOICES,
                    buds = buds,
                    viewModel = viewModel,
                )
            }
        }
        AnimatedVisibility(visible = noise != null && noise != Noise.NORMAL) {
            Column {
                InsetDivider()
                FeatureSwitchRow(
                    title = stringResource(R.string.home_smart_dewind),
                    icon = Icons.Outlined.Air,
                    featureId = Feature.SMART_DEWIND,
                    buds = buds,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun SoundCard(
    buds: BudsState,
    viewModel: BudsViewModel,
    onNavigate: (String) -> Unit,
) {
    val eqLabel = eqPresetLabel(buds.eq)
    val dynamicOn = buds.features[Feature.DYNAMIC_AUDIO] ?: false

    SettingsCard(
        title = stringResource(R.string.home_sound),
        icon = Icons.Outlined.GraphicEq,
    ) {
        NavRow(
            title = stringResource(R.string.home_eq_mode),
            subtitle = eqLabel?.let { stringResource(it) },
            icon = Icons.Outlined.Equalizer,
            onClick = { onNavigate(Routes.EQ) },
        )
        InsetDivider()
        FeatureSwitchRow(
            title = stringResource(R.string.home_spatial_audio),
            icon = Icons.Outlined.SpatialAudio,
            featureId = Feature.SPATIAL_AUDIO,
            buds = buds,
            viewModel = viewModel,
        )
        InsetDivider()
        FeatureSwitchRow(
            title = stringResource(R.string.home_dynamic_audio),
            icon = Icons.Outlined.AutoAwesome,
            featureId = Feature.DYNAMIC_AUDIO,
            buds = buds,
            viewModel = viewModel,
        )
        AnimatedVisibility(visible = dynamicOn) {
            Column {
                InsetDivider()
                DYNAMIC_SLIDERS.forEach { (type, labelRes) ->
                    LabeledSlider(
                        title = stringResource(labelRes),
                        value = buds.dynamic[type] ?: 0,
                        range = -5..5,
                        onCommit = { viewModel.setDynamicSlider(type, it) },
                    )
                }
            }
        }
        InsetDivider()
        SwitchRow(
            title = stringResource(R.string.home_volume_enhancer),
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            checked = buds.volumeEnhancer ?: false,
            onCheckedChange = { viewModel.setHighVolume(it) },
        )
    }
}

/** Show the connectivity card with the dual-device and find-my-phone rows. */
@Composable
private fun ConnectivityCard(
    buds: BudsState,
    onNavigate: (String) -> Unit,
) {
    val findPhoneState = buds.features[Feature.FIND_MY_PHONE]?.let { stringResource(stateLabelOf(it)) }
    val dualState = buds.features[Feature.DUAL_CONNECTION]?.let { stringResource(stateLabelOf(it)) }

    SettingsCard(
        title = stringResource(R.string.home_connectivity),
        icon = Icons.Outlined.Devices,
    ) {
        NavRow(
            title = stringResource(R.string.home_dual_device),
            subtitle = dualState,
            icon = Icons.Outlined.Devices,
            onClick = { onNavigate(Routes.DUAL) },
        )
        InsetDivider()
        NavRow(
            title = stringResource(R.string.home_find_my_phone),
            subtitle = findPhoneState,
            icon = Icons.Outlined.PhonelinkRing,
            onClick = { onNavigate(Routes.FIND_PHONE) },
        )
    }
}

/** Show the controls card with the button, feature and fit-test rows. */
@Composable
private fun ControlsCard(
    buds: BudsState,
    viewModel: BudsViewModel,
    onNavigate: (String) -> Unit,
) {
    SettingsCard(
        title = stringResource(R.string.home_controls),
        icon = Icons.Outlined.Tune,
    ) {
        NavRow(
            title = stringResource(R.string.home_button_settings),
            icon = Icons.Outlined.TouchApp,
            onClick = { onNavigate(Routes.BUTTONS) },
        )
        InsetDivider()
        FeatureSwitchRow(
            title = stringResource(R.string.home_game_mode),
            icon = Icons.Outlined.SportsEsports,
            featureId = Feature.GAME_MODE,
            buds = buds,
            viewModel = viewModel,
        )
        InsetDivider()
        FeatureSwitchRow(
            title = stringResource(R.string.home_auto_answer),
            icon = Icons.Outlined.PhoneInTalk,
            featureId = Feature.AUTO_ANSWER,
            buds = buds,
            viewModel = viewModel,
        )
        InsetDivider()
        NavRow(
            title = stringResource(R.string.home_fit_test),
            icon = Icons.Outlined.Earbuds,
            onClick = { onNavigate(Routes.FIT) },
        )
    }
}

/**
 * Show the app settings card with the theme selector.
 *
 * Tapping the theme row opens [ThemeDialog]; the choice is applied and persisted by the view model.
 *
 * The firmware row is shown last, only when [firmware] is known.
 *
 * @param viewModel provides the current theme mode and receives the selected one
 * @param firmware raw firmware text from the earbuds, or null when not read yet
 */
@Composable
private fun AppCard(
    viewModel: BudsViewModel,
    firmware: String?,
) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    SettingsCard(
        title = stringResource(R.string.home_app),
        icon = Icons.Outlined.Palette,
    ) {
        NavRow(
            title = stringResource(R.string.theme_title),
            subtitle = stringResource(themeLabelOf(mode)),
            icon = Icons.Outlined.Palette,
            onClick = { showDialog = true },
        )
        InsetDivider()
        NavRow(
            title = stringResource(R.string.home_about),
            subtitle = stringResource(R.string.home_about_subtitle),
            icon = Icons.Outlined.Info,
            onClick = { uriHandler.openUri(REPO_URL) },
        )
        if (firmware != null) {
            InsetDivider()
            SettingRow(
                title = stringResource(R.string.home_firmware),
                subtitle = firmwareVersion(firmware),
                icon = Icons.Outlined.Memory,
            )
        }
    }

    if (showDialog) {
        ThemeDialog(
            current = mode,
            onSelect = viewModel::setThemeMode,
            onDismiss = { showDialog = false },
        )
    }
}

/**
 * Extract the version from the raw firmware text reported by the earbuds (query 0x0105).
 *
 * The raw text is comma-separated. Its first two fields are not documented in docs/PROTOCOL.md,
 * so only the text after the second comma is shown. When there are fewer than two commas, or
 * nothing follows the second comma, the full raw text is returned.
 *
 * Example: "1,2,1.1.0.56" becomes "1.1.0.56".
 *
 * @param raw raw firmware text as received from the earbuds
 * @return the version part of [raw], or [raw] itself when no version part is found
 */
private fun firmwareVersion(raw: String): String {
    val version =
        raw
            .split(',', limit = 3)
            .getOrNull(2)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    return version ?: raw
}

@Composable
private fun FeatureSwitchRow(
    title: String,
    icon: ImageVector,
    featureId: Int,
    buds: BudsState,
    viewModel: BudsViewModel,
) {
    SwitchRow(
        title = title,
        icon = icon,
        checked = buds.features[featureId] ?: false,
        onCheckedChange = { viewModel.setFeature(featureId, it) },
    )
}

/** Return the label for EQ [preset], or null when unknown or unreadable. */
private fun eqPresetLabel(preset: Int?): Int? =
    when (preset) {
        Eq.NATURE_BALANCE -> R.string.eq_preset_nature_balance
        Eq.BASS_BOOST -> R.string.eq_preset_bass_boost
        Eq.CLEAR_VOCALS -> R.string.eq_preset_clear_vocals
        Eq.CLEAR_BASS -> R.string.eq_preset_clear_bass
        EQ_CUSTOM -> R.string.eq_preset_custom
        else -> null
    }

/** Return the On/Off label for a feature state. */
private fun stateLabelOf(on: Boolean): Int = if (on) R.string.home_state_on else R.string.home_noise_off

/** Return the label for theme [mode]. */
private fun themeLabelOf(mode: ThemeMode): Int =
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

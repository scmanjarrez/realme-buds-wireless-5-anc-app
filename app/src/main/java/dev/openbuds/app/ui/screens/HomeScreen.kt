package dev.openbuds.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.openbuds.app.ui.components.LabeledSlider
import dev.openbuds.app.ui.components.NavRow
import dev.openbuds.app.ui.components.SettingsCard
import dev.openbuds.app.ui.components.SwitchRow

/**
 * Active EQ id of the custom equalizer, as reported by the earbuds. Wire value 0x04.
 *
 * See 'Queries (0x01xx)' in docs/PROTOCOL.md for the active-EQ query.
 */
private const val EQ_CUSTOM = 0x04

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
        Header(buds = buds)
        NoiseCard(buds = buds, viewModel = viewModel)
        SoundCard(buds = buds, viewModel = viewModel, onNavigate = onNavigate)
        OtherCard(buds = buds, viewModel = viewModel, onNavigate = onNavigate)
    }
}

@Composable
private fun Header(buds: BudsState) {
    val battery = buds.battery
    val firmware = buds.firmware
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (battery != null || firmware != null) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (battery != null) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.home_battery, battery),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (firmware != null) {
                    Text(
                        text = stringResource(R.string.home_firmware, firmware),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
    val segments =
        listOf(
            NoiseSegment.ANC to R.string.home_noise_anc_short,
            NoiseSegment.OFF to R.string.home_noise_off,
            NoiseSegment.TRANSPARENCY to R.string.home_noise_transparency,
        )

    SettingsCard(title = stringResource(R.string.home_noise_control)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            segments.forEachIndexed { index, (segment, labelRes) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = segments.size),
                    selected = selected == segment,
                    onClick = {
                        if (selected != segment) {
                            when (segment) {
                                NoiseSegment.ANC -> viewModel.setNoiseMode(ancLevel)
                                NoiseSegment.OFF -> viewModel.setNoiseMode(Noise.NORMAL)
                                NoiseSegment.TRANSPARENCY -> viewModel.setNoiseMode(Noise.TRANSPARENT)
                            }
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(labelRes),
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                )
            }
        }

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
            FeatureSwitchRow(
                title = stringResource(R.string.home_enhance_voices),
                featureId = Feature.ENHANCE_VOICES,
                buds = buds,
                viewModel = viewModel,
            )
        }
        AnimatedVisibility(visible = noise != null && noise != Noise.NORMAL) {
            FeatureSwitchRow(
                title = stringResource(R.string.home_smart_dewind),
                featureId = Feature.SMART_DEWIND,
                buds = buds,
                viewModel = viewModel,
            )
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

    SettingsCard(title = stringResource(R.string.home_sound)) {
        NavRow(
            title = stringResource(R.string.home_eq_mode),
            value = eqLabel?.let { stringResource(it) },
            onClick = { onNavigate(Routes.EQ) },
        )
        FeatureSwitchRow(
            title = stringResource(R.string.home_spatial_audio),
            featureId = Feature.SPATIAL_AUDIO,
            buds = buds,
            viewModel = viewModel,
        )
        FeatureSwitchRow(
            title = stringResource(R.string.home_dynamic_audio),
            featureId = Feature.DYNAMIC_AUDIO,
            buds = buds,
            viewModel = viewModel,
        )
        AnimatedVisibility(visible = dynamicOn) {
            Column {
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
        SwitchRow(
            title = stringResource(R.string.home_volume_enhancer),
            checked = buds.volumeEnhancer ?: false,
            onCheckedChange = { viewModel.setHighVolume(it) },
        )
    }
}

@Composable
private fun OtherCard(
    buds: BudsState,
    viewModel: BudsViewModel,
    onNavigate: (String) -> Unit,
) {
    SettingsCard(title = stringResource(R.string.home_other)) {
        FeatureSwitchRow(
            title = stringResource(R.string.home_game_mode),
            featureId = Feature.GAME_MODE,
            buds = buds,
            viewModel = viewModel,
        )
        NavRow(
            title = stringResource(R.string.home_fit_test),
            onClick = { onNavigate(Routes.FIT) },
        )
        NavRow(
            title = stringResource(R.string.home_find_my_phone),
            onClick = { onNavigate(Routes.FIND_PHONE) },
        )
        NavRow(
            title = stringResource(R.string.home_dual_device),
            onClick = { onNavigate(Routes.DUAL) },
        )
        NavRow(
            title = stringResource(R.string.home_button_settings),
            onClick = { onNavigate(Routes.BUTTONS) },
        )
        FeatureSwitchRow(
            title = stringResource(R.string.home_auto_answer),
            featureId = Feature.AUTO_ANSWER,
            buds = buds,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun FeatureSwitchRow(
    title: String,
    featureId: Int,
    buds: BudsState,
    viewModel: BudsViewModel,
) {
    SwitchRow(
        title = title,
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

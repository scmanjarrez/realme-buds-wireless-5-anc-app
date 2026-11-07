package dev.openbuds.app.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openbuds.app.R
import dev.openbuds.app.data.BudsState
import dev.openbuds.app.protocol.Eq
import dev.openbuds.app.protocol.Protocol
import dev.openbuds.app.ui.BudsViewModel
import dev.openbuds.app.ui.components.BudsScaffold
import dev.openbuds.app.ui.components.InsetDivider
import dev.openbuds.app.ui.components.LabeledSlider
import dev.openbuds.app.ui.components.SettingRow
import dev.openbuds.app.ui.components.SettingsCard
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** EQ id the earbuds report while the custom EQ is active. Wire value 0x04. */
private const val EQ_CUSTOM = 0x04

/** Custom band gain range in dB, lower bound (0x0418 `min`); upper bound is [GAIN_MAX]. */
private const val GAIN_MIN = -6
private const val GAIN_MAX = 6

/** Quiet period in milliseconds before device EQ state overwrites the local draft. */
private const val QUIET_MS = 2000L

/**
 * EQ mode screen: four presets plus a custom six-band equalizer.
 *
 * Presets are sent with 0x0406. Custom gains are sent with 0x0418 (create first if the earbuds
 * have no custom EQ, then update). Related command 0x041b (dynamic sliders) is not used here.
 * Device state is copied into the local draft only after [QUIET_MS] without local changes, so
 * slider drags are not overwritten.
 *
 * @param buds current earbud state; `eq` selects the highlighted row and `customEq` seeds the draft.
 * @param viewModel source of the EQ commands (`setEq`, `setCustomEq`, `resetCustomEq`) and of
 * the refresh state and `reload` action.
 * @param onBack called when the back navigation is used.
 */
@Composable
fun EqScreen(
    buds: BudsState,
    viewModel: BudsViewModel,
    onBack: () -> Unit,
) {
    val resources = LocalResources.current
    val draft =
        remember {
            mutableStateListOf<Int>().apply {
                addAll(List(Protocol.EQ_FREQS.size) { buds.customEq?.getOrNull(it) ?: 0 })
            }
        }
    val lastCommitMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(buds.customEq) {
        val device = buds.customEq ?: return@LaunchedEffect
        while (true) {
            val remaining = lastCommitMs.longValue + QUIET_MS - SystemClock.elapsedRealtime()
            if (remaining <= 0L) break
            delay(remaining.milliseconds)
        }
        if (device != draft.toList()) {
            draft.indices.forEach { draft[it] = device.getOrElse(it) { 0 } }
        }
    }
    val gainLabel: (Int) -> String = { db ->
        if (db == 0) {
            resources.getString(R.string.eq_gain_zero)
        } else {
            resources.getString(R.string.eq_gain_db, db)
        }
    }

    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    BudsScaffold(
        title = stringResource(R.string.home_eq_mode),
        onBack = onBack,
        refreshing = refreshing,
        onRefresh = viewModel::reload,
    ) {
        SettingsCard(
            title = stringResource(R.string.eq_presets),
            icon = Icons.Outlined.Equalizer,
        ) {
            EqPresetRow(
                title = stringResource(R.string.eq_preset_nature_balance),
                selected = buds.eq == Eq.NATURE_BALANCE,
                onClick = { viewModel.setEq(Eq.NATURE_BALANCE) },
            )
            InsetDivider()
            EqPresetRow(
                title = stringResource(R.string.eq_preset_bass_boost),
                selected = buds.eq == Eq.BASS_BOOST,
                onClick = { viewModel.setEq(Eq.BASS_BOOST) },
            )
            InsetDivider()
            EqPresetRow(
                title = stringResource(R.string.eq_preset_clear_vocals),
                selected = buds.eq == Eq.CLEAR_VOCALS,
                onClick = { viewModel.setEq(Eq.CLEAR_VOCALS) },
            )
            InsetDivider()
            EqPresetRow(
                title = stringResource(R.string.eq_preset_clear_bass),
                selected = buds.eq == Eq.CLEAR_BASS,
                onClick = { viewModel.setEq(Eq.CLEAR_BASS) },
            )
            InsetDivider()
            EqPresetRow(
                title = stringResource(R.string.eq_preset_custom),
                selected = buds.eq == EQ_CUSTOM,
                onClick = { viewModel.setCustomEq(draft.toList()) },
            )
        }

        AnimatedVisibility(visible = buds.eq == EQ_CUSTOM) {
            SettingsCard(
                title = stringResource(R.string.eq_custom_title),
                icon = Icons.Outlined.Tune,
            ) {
                Protocol.EQ_FREQS.forEachIndexed { index, hz ->
                    LabeledSlider(
                        title =
                            if (hz >= 1000) {
                                stringResource(R.string.eq_band_khz, hz / 1000)
                            } else {
                                stringResource(R.string.eq_band_hz, hz)
                            },
                        value = draft[index],
                        range = GAIN_MIN..GAIN_MAX,
                        onCommit = { value ->
                            draft[index] = value
                            lastCommitMs.longValue = SystemClock.elapsedRealtime()
                            viewModel.setCustomEq(draft.toList())
                        },
                        valueLabel = gainLabel,
                    )
                }
                TextButton(
                    onClick = {
                        draft.indices.forEach { draft[it] = 0 }
                        lastCommitMs.longValue = SystemClock.elapsedRealtime()
                        viewModel.resetCustomEq()
                    },
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 8.dp),
                ) {
                    Text(text = stringResource(R.string.eq_reset))
                }
            }
        }
    }
}

/** One EQ preset as a radio-style row; the whole row is selectable with radio semantics. */
@Composable
private fun EqPresetRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SettingRow(
        title = title,
        modifier = Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        leading = { RadioButton(selected = selected, onClick = null) },
    )
}

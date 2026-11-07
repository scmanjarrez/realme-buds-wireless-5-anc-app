package dev.openbuds.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.NoiseAware
import androidx.compose.material.icons.outlined.NoiseControlOff
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openbuds.app.R
import dev.openbuds.app.data.BudsApi
import dev.openbuds.app.data.BudsState
import dev.openbuds.app.protocol.CycleBit
import dev.openbuds.app.protocol.KeyFn
import dev.openbuds.app.ui.BudsViewModel
import dev.openbuds.app.ui.components.BudsScaffold
import dev.openbuds.app.ui.components.InsetDivider
import dev.openbuds.app.ui.components.NavRow
import dev.openbuds.app.ui.components.SettingRow
import dev.openbuds.app.ui.components.SettingsCard

/** Earbud multifunction button id (legacy id 1). Wire value 0x01. */
private const val EARBUD_BUTTON = 1

/** The noise-control cycle must keep at least this many modes selected. */
private const val MIN_CYCLE_MODES = 2

/** Press gestures on the earbud button; [action] is the 0x0401 action code. */
private enum class Gesture(
    val action: Int,
    @param:StringRes val label: Int,
) {
    SINGLE(1, R.string.buttons_single_press),
    DOUBLE(2, R.string.buttons_double_press),
    TRIPLE(3, R.string.buttons_triple_press),
    HOLD(4, R.string.buttons_hold),
}

private data class FunctionOption(
    val fn: Int,
    @param:StringRes val label: Int,
)

private data class CycleOption(
    val bit: Int,
    @param:StringRes val label: Int,
    val icon: ImageVector,
)

private val functionOptions =
    listOf(
        FunctionOption(KeyFn.NONE, R.string.buttons_fn_none),
        FunctionOption(KeyFn.PLAY_PAUSE, R.string.buttons_fn_play_pause),
        FunctionOption(KeyFn.NEXT, R.string.buttons_fn_next),
        FunctionOption(KeyFn.PREVIOUS, R.string.buttons_fn_previous),
        FunctionOption(KeyFn.VOICE_ASSISTANT, R.string.buttons_fn_voice_assistant),
        FunctionOption(KeyFn.GAME_MODE, R.string.home_game_mode),
        FunctionOption(KeyFn.NOISE_CONTROL, R.string.home_noise_control),
    )

/** Functions offered for the earbud gestures; noise control is reserved for the noise button. */
private val gestureOptions = functionOptions.filter { it.fn != KeyFn.NOISE_CONTROL }

private val cycleOptions =
    listOf(
        CycleOption(CycleBit.NOISE_CANCELLATION, R.string.home_noise_anc, Icons.Outlined.NoiseAware),
        CycleOption(CycleBit.OFF, R.string.home_noise_off, Icons.Outlined.NoiseControlOff),
        CycleOption(CycleBit.TRANSPARENCY, R.string.home_noise_transparency, Icons.Outlined.Hearing),
    )

private fun functionLabel(fn: Int?): Int? = functionOptions.firstOrNull { it.fn == fn }?.label

/**
 * Button settings screen: earbud gesture functions and the noise-control cycle.
 *
 * Gesture functions are read from and written to 0x0401 (button functions). The noise-control
 * cycle is written through `setNoiseCycle`, which uses 0x0404 (noise control). Tapping a gesture
 * opens a bottom sheet with the assignable functions.
 *
 * @param buds current earbud state; `keys` provides the assigned functions and `noiseCycle` the
 * selected noise modes.
 * @param viewModel source of the `setKey` and `setNoiseCycle` commands and of the refresh state
 * and `reload` action.
 * @param onBack called when the back navigation is used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonsScreen(
    buds: BudsState,
    viewModel: BudsViewModel,
    onBack: () -> Unit,
) {
    var openGesture by remember { mutableStateOf<Gesture?>(null) }
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    BudsScaffold(
        title = stringResource(R.string.home_button_settings),
        onBack = onBack,
        refreshing = refreshing,
        onRefresh = viewModel::reload,
    ) {
        SettingsCard(
            title = stringResource(R.string.buttons_earbud_controls),
            icon = Icons.Outlined.TouchApp,
        ) {
            Gesture.entries.forEachIndexed { index, gesture ->
                if (index > 0) InsetDivider()
                val fn = buds.keys[BudsApi.keyId(EARBUD_BUTTON, gesture.action)]
                NavRow(
                    title = stringResource(gesture.label),
                    subtitle = functionLabel(fn)?.let { stringResource(it) },
                    icon = Icons.Outlined.TouchApp,
                    onClick = { openGesture = gesture },
                )
            }
        }

        SettingsCard(
            title = stringResource(R.string.buttons_noise_title),
            icon = Icons.Outlined.NoiseAware,
            description = stringResource(R.string.buttons_noise_hint),
        ) {
            val mask = buds.noiseCycle ?: 0
            val locked = mask.countOneBits() == MIN_CYCLE_MODES
            cycleOptions.forEachIndexed { index, option ->
                if (index > 0) InsetDivider()
                val checked = (mask and option.bit) != 0
                val enabled = !(locked && checked)
                val toggle: () -> Unit = {
                    val newMask = mask xor option.bit
                    if (newMask.countOneBits() >= MIN_CYCLE_MODES) {
                        viewModel.setNoiseCycle(newMask)
                    }
                }
                SettingRow(
                    title = stringResource(option.label),
                    icon = option.icon,
                    onClick = if (enabled) toggle else null,
                    content = {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    },
                )
            }
        }
    }

    openGesture?.let { gesture ->
        val current = buds.keys[BudsApi.keyId(EARBUD_BUTTON, gesture.action)]
        ModalBottomSheet(
            onDismissRequest = { openGesture = null },
            sheetState = sheetState,
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(gesture.label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                gestureOptions.forEachIndexed { index, option ->
                    if (index > 0) InsetDivider()
                    val selected = current == option.fn
                    SettingRow(
                        title = stringResource(option.label),
                        modifier =
                            Modifier.selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = {
                                    viewModel.setKey(EARBUD_BUTTON, gesture.action, option.fn)
                                    openGesture = null
                                },
                            ),
                        leading = {
                            RadioButton(selected = selected, onClick = null)
                        },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

package dev.openbuds.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openbuds.app.R
import dev.openbuds.app.data.BudsState
import dev.openbuds.app.data.PairedDevice
import dev.openbuds.app.protocol.Feature
import dev.openbuds.app.ui.BudsViewModel
import dev.openbuds.app.ui.components.BudsScaffold
import dev.openbuds.app.ui.components.InsetDivider
import dev.openbuds.app.ui.components.SettingRow
import dev.openbuds.app.ui.components.SettingsCard
import dev.openbuds.app.ui.components.StatusDot
import dev.openbuds.app.ui.components.SwitchRow

/** Device action ids for the multi-connect command (0x040b). */
private const val ACTION_DISCONNECT = 0
private const val ACTION_CONNECT = 1
private const val ACTION_REMOVE = 2

/** Device state values reported for paired devices. */
private const val STATE_CONNECTING = 1
private const val STATE_CONNECTED = 2

/**
 * Show dual-device settings: the feature switch and the paired devices with their actions.
 *
 * The switch turns [Feature.DUAL_CONNECTION] on or off through [BudsViewModel.setFeature]. Tapping
 * another device opens a sheet with connect, disconnect or remove actions. These are sent as the
 * multi-connect command `0x040b` through [BudsViewModel.multiConnect]. The earbuds may take more
 * than 2 s to answer `0x040b` (see 'Settings (0x04xx)' in docs/PROTOCOL.md). The row for this phone is not
 * actionable.
 *
 * @param buds Current earbud state, including feature flags and the paired device list.
 * @param viewModel ViewModel for refresh state, feature switch and multi-connect commands.
 * @param onBack Called when the back button in the scaffold is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualDeviceScreen(
    buds: BudsState,
    viewModel: BudsViewModel,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf<PairedDevice?>(null) }
    var pendingRemoval by remember { mutableStateOf<PairedDevice?>(null) }
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    BudsScaffold(
        title = stringResource(R.string.home_dual_device),
        onBack = onBack,
        refreshing = refreshing,
        onRefresh = viewModel::reload,
    ) {
        SettingsCard(
            title = null,
            description = stringResource(R.string.dual_description),
        ) {
            SwitchRow(
                title = stringResource(R.string.home_dual_device),
                checked = buds.features[Feature.DUAL_CONNECTION] == true,
                onCheckedChange = { viewModel.setFeature(Feature.DUAL_CONNECTION, it) },
                icon = Icons.Outlined.Devices,
            )
        }
        SettingsCard(title = stringResource(R.string.dual_your_devices)) {
            if (buds.devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.dual_no_other_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            buds.devices.forEachIndexed { index, device ->
                if (index > 0) {
                    InsetDivider()
                }
                if (device.isThisDevice) {
                    SettingRow(
                        title = device.name,
                        subtitle = deviceStateText(device),
                        icon = Icons.Outlined.Devices,
                        content = { StatusDot(color = deviceStatusColor(device)) },
                    )
                } else {
                    SettingRow(
                        title = device.name,
                        subtitle = deviceStateText(device),
                        icon = Icons.Outlined.Devices,
                        onClick = { selected = device },
                        content = { StatusDot(color = deviceStatusColor(device)) },
                    )
                }
            }
        }
    }

    selected?.let { device ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (device.state == STATE_CONNECTED) {
                SettingRow(
                    title = stringResource(R.string.dual_disconnect),
                    icon = Icons.Outlined.LinkOff,
                    onClick = {
                        viewModel.multiConnect(device.mac, ACTION_DISCONNECT)
                        selected = null
                    },
                )
            } else {
                SettingRow(
                    title = stringResource(R.string.dual_connect),
                    icon = Icons.Outlined.Link,
                    onClick = {
                        viewModel.multiConnect(device.mac, ACTION_CONNECT)
                        selected = null
                    },
                )
            }
            InsetDivider()
            SettingRow(
                title = stringResource(R.string.dual_remove),
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    pendingRemoval = device
                    selected = null
                },
            )
        }
    }

    pendingRemoval?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(text = stringResource(R.string.dual_remove_title, device.name)) },
            text = { Text(text = stringResource(R.string.dual_remove_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.multiConnect(device.mac, ACTION_REMOVE)
                        pendingRemoval = null
                    },
                ) {
                    Text(text = stringResource(R.string.dual_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(text = stringResource(R.string.dual_cancel))
                }
            },
        )
    }
}

@Composable
private fun deviceStateText(device: PairedDevice): String =
    when {
        device.isThisDevice -> stringResource(R.string.dual_state_this_phone)
        device.state == STATE_CONNECTED -> stringResource(R.string.dual_state_connected)
        device.state == STATE_CONNECTING -> stringResource(R.string.dual_state_connecting)
        else -> stringResource(R.string.dual_state_not_connected)
    }

@Composable
private fun deviceStatusColor(device: PairedDevice): Color =
    when {
        device.isThisDevice || device.state == STATE_CONNECTED -> MaterialTheme.colorScheme.primary
        device.state == STATE_CONNECTING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }

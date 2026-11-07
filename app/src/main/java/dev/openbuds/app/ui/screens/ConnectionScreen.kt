package dev.openbuds.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HeadsetOff
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openbuds.app.R
import dev.openbuds.app.data.ConnectionState
import dev.openbuds.app.ui.components.EmptyState
import dev.openbuds.app.ui.components.InsetDivider
import dev.openbuds.app.ui.components.NavRow
import dev.openbuds.app.ui.components.SettingsCard

/**
 * Show the full-screen connection flow until the earbuds are ready.
 *
 * Renders the prompt, device list or spinner that matches [state], and stays until the earbuds
 * are connected and their state is loaded.
 *
 * @param state current connection step to render.
 * @param onGrantPermission called when the user taps the permission action.
 * @param onEnableBluetooth called when the user taps the Bluetooth-off action.
 * @param onOpenBluetoothSettings called when the user taps the no-devices action.
 * @param onSelect called with the address of the device the user picks.
 * @param onReconnect called when the user taps the reconnect action.
 */
@Composable
fun ConnectionScreen(
    state: ConnectionState,
    onGrantPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onSelect: (String) -> Unit,
    onReconnect: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ConnectionState.NeedsPermission -> {
                EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.connection_permission_title),
                    message = stringResource(R.string.connection_permission_message),
                    actionLabel = stringResource(R.string.connection_permission_action),
                    onAction = onGrantPermission,
                )
            }

            ConnectionState.BluetoothOff -> {
                EmptyState(
                    icon = Icons.Outlined.BluetoothDisabled,
                    title = stringResource(R.string.connection_bluetooth_off_title),
                    message = stringResource(R.string.connection_bluetooth_off_message),
                    actionLabel = stringResource(R.string.connection_bluetooth_off_action),
                    onAction = onEnableBluetooth,
                )
            }

            ConnectionState.NoDevices -> {
                EmptyState(
                    icon = Icons.Outlined.HeadsetOff,
                    title = stringResource(R.string.connection_no_devices_title),
                    message = stringResource(R.string.connection_no_devices_message),
                    actionLabel = stringResource(R.string.connection_no_devices_action),
                    onAction = onOpenBluetoothSettings,
                )
            }

            is ConnectionState.ChooseDevice -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    SettingsCard(
                        title = stringResource(R.string.connection_choose_title),
                        icon = Icons.Outlined.Bluetooth,
                    ) {
                        state.devices.forEachIndexed { index, device ->
                            if (index > 0) InsetDivider()
                            NavRow(
                                title = device.name.ifEmpty { stringResource(R.string.connection_unnamed_device) },
                                subtitle = device.address,
                                icon = Icons.Outlined.Headphones,
                                onClick = { onSelect(device.address) },
                            )
                        }
                    }
                }
            }

            ConnectionState.Connecting, ConnectionState.Connected -> {
                EmptyState(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(R.string.connection_connecting_title),
                    message = stringResource(R.string.connection_connecting_message),
                    loading = true,
                )
            }

            ConnectionState.Disconnected -> {
                EmptyState(
                    icon = Icons.Outlined.LinkOff,
                    title = stringResource(R.string.connection_disconnected_title),
                    message = stringResource(R.string.connection_disconnected_message),
                    actionLabel = stringResource(R.string.connection_disconnected_action),
                    onAction = onReconnect,
                )
            }
        }
    }
}

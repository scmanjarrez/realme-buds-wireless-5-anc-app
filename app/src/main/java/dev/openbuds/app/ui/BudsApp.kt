package dev.openbuds.app.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.openbuds.app.data.ConnectionState
import dev.openbuds.app.ui.screens.ButtonsScreen
import dev.openbuds.app.ui.screens.ConnectionScreen
import dev.openbuds.app.ui.screens.DualDeviceScreen
import dev.openbuds.app.ui.screens.EqScreen
import dev.openbuds.app.ui.screens.FindPhoneScreen
import dev.openbuds.app.ui.screens.FitTestScreen
import dev.openbuds.app.ui.screens.HomeScreen
import dev.openbuds.app.util.BLUETOOTH_CONNECT_PERMISSION

private const val TAG = "BudsApp"

/** Navigation routes for the connected screens. */
object Routes {
    const val HOME = "home"
    const val EQ = "eq"
    const val BUTTONS = "buttons"
    const val DUAL = "dual"
    const val FIT = "fit"
    const val FIND_PHONE = "find_phone"
}

/** Root of the UI: shows the connection flow until earbuds are connected, then the screens. */
@Composable
fun BudsApp(viewModel: BudsViewModel = viewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.refresh()
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refresh()
        }
    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refresh()
        }

    val grantPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(BLUETOOTH_CONNECT_PERMISSION)
        } else {
            viewModel.refresh()
        }
    }
    val enableBluetooth: () -> Unit = {
        try {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot request Bluetooth enable", e)
        }
    }
    val openBluetoothSettings: () -> Unit = {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    val buds = ui.buds
    if (ui.connection is ConnectionState.Connected && buds != null) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(buds = buds, viewModel = viewModel) { route ->
                    navController.navigate(route)
                }
            }
            composable(Routes.EQ) {
                EqScreen(
                    buds = buds,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.BUTTONS) {
                ButtonsScreen(
                    buds = buds,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.DUAL) {
                DualDeviceScreen(
                    buds = buds,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.FIT) {
                FitTestScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.FIND_PHONE) {
                FindPhoneScreen(
                    buds = buds,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    } else {
        ConnectionScreen(
            state = ui.connection,
            onGrantPermission = grantPermission,
            onEnableBluetooth = enableBluetooth,
            onOpenBluetoothSettings = openBluetoothSettings,
            onSelect = { address -> viewModel.selectDevice(address) },
            onReconnect = { viewModel.reconnect() },
        )
    }
}

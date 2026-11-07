package dev.openbuds.app.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhonelinkRing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openbuds.app.R
import dev.openbuds.app.data.BudsState
import dev.openbuds.app.protocol.Feature
import dev.openbuds.app.ui.BudsViewModel
import dev.openbuds.app.ui.components.ActionRow
import dev.openbuds.app.ui.components.BudsScaffold
import dev.openbuds.app.ui.components.SettingsCard
import dev.openbuds.app.ui.components.SwitchRow

/** Runtime permission needed to show the Stop notification on Android 13 and later. */
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

/**
 * Show find-my-phone settings: the feature switch, a test ring action and a battery hint.
 *
 * The switch sets feature `0x26` (Find my phone) through command `0x0403`, via
 * [BudsViewModel.setFindMyPhone]. While it is on, a button press on the earbuds makes the app ring
 * the phone (see 'Unsolicited frames (0x05xx)' in docs/PROTOCOL.md). The test ring action
 * starts or stops ringing through [BudsViewModel.toggleTestRing].
 *
 * Enabling the feature on Android 13 or later asks for the notification permission once, so the
 * Stop notification can be shown. Denying it does not disable the feature.
 *
 * @param buds Current earbud state, including feature flags.
 * @param viewModel ViewModel for refresh state, ringing state and find-my-phone commands.
 * @param onBack Called when the back button in the scaffold is pressed.
 */
@Composable
fun FindPhoneScreen(
    buds: BudsState,
    viewModel: BudsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isRinging by viewModel.isRinging.collectAsStateWithLifecycle()
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    BudsScaffold(
        title = stringResource(R.string.home_find_my_phone),
        onBack = onBack,
        refreshing = refreshing,
        onRefresh = viewModel::reload,
    ) {
        SettingsCard(
            title = null,
            description = stringResource(R.string.findphone_description),
        ) {
            SwitchRow(
                title = stringResource(R.string.home_find_my_phone),
                checked = buds.features[Feature.FIND_MY_PHONE] == true,
                onCheckedChange = { enabled ->
                    viewModel.setFindMyPhone(enabled)
                    if (enabled && isNotificationPermissionMissing(context)) {
                        permissionLauncher.launch(POST_NOTIFICATIONS)
                    }
                },
                icon = Icons.Outlined.PhonelinkRing,
            )
        }
        SettingsCard(
            title = null,
            description = stringResource(R.string.findphone_battery_hint),
        ) {
            ActionRow(
                title = stringResource(R.string.findphone_test_ring),
                actionLabel =
                    if (isRinging) {
                        stringResource(R.string.findphone_stop)
                    } else {
                        stringResource(R.string.findphone_ring)
                    },
                onAction = { viewModel.toggleTestRing() },
                icon = Icons.Outlined.NotificationsActive,
            )
        }
    }
}

/**
 * Returns true when the notification permission is required (Android 13+) and not yet granted.
 *
 * @param context Context used to check the permission.
 * @return True if the permission should be requested.
 */
private fun isNotificationPermissionMissing(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

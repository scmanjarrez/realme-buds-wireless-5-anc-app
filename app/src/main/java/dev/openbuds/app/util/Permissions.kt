package dev.openbuds.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Bluetooth connect permission name.
 *
 * Declared as a literal on purpose: referencing the platform constant directly
 * triggers lint InlinedApi because minSdk is 24.
 */
const val BLUETOOTH_CONNECT_PERMISSION = "android.permission.BLUETOOTH_CONNECT"

/**
 * Return true when Bluetooth connect access is granted.
 *
 * Runtime permission only exists on Android 12 (API 31) and later; older
 * versions always return true.
 *
 * @return true if the permission is granted or not required on this API level.
 */
fun Context.hasBluetoothConnect(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, BLUETOOTH_CONNECT_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

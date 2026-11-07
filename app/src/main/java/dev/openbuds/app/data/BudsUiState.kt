package dev.openbuds.app.data

/**
 * A bonded earbud the user can pick from.
 *
 * @property name Display name reported by the Bluetooth stack.
 * @property address Bluetooth hardware address of the device.
 */
data class BondedDevice(
    val name: String,
    val address: String,
)

/** Where the app is in the connection lifecycle. */
sealed interface ConnectionState {
    /** Initial state: preconditions have not been checked yet. */
    data object Checking : ConnectionState

    /** Bluetooth connect permission has not been granted. */
    data object NeedsPermission : ConnectionState

    /** Bluetooth is off or not available on this device. */
    data object BluetoothOff : ConnectionState

    /** No bonded realme Buds device was found. */
    data object NoDevices : ConnectionState

    /**
     * More than one bonded device matches; the user must pick one.
     *
     * @property devices Candidate devices to show in the picker.
     */
    data class ChooseDevice(
        val devices: List<BondedDevice>,
    ) : ConnectionState

    /** A connection attempt is in progress. */
    data object Connecting : ConnectionState

    /** Connected and reading or writing earbud state. */
    data object Connected : ConnectionState

    /** Not connected; the link dropped or the attempt failed. */
    data object Disconnected : ConnectionState
}

/**
 * Full UI state: the connection lifecycle plus the last state read from the earbuds.
 *
 * @property connection Current position in the connection lifecycle. Starts as
 *   [ConnectionState.Checking] until the preconditions are evaluated.
 * @property buds Last earbud state read over the link, or null before the first successful read.
 */
data class BudsUiState(
    val connection: ConnectionState = ConnectionState.Checking,
    val buds: BudsState? = null,
)

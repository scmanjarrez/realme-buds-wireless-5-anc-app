package dev.openbuds.app.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.annotation.RequiresPermission
import dev.openbuds.app.protocol.BudsEvent
import dev.openbuds.app.protocol.EventId
import dev.openbuds.app.protocol.Events
import dev.openbuds.app.util.BLUETOOTH_CONNECT_PERMISSION
import dev.openbuds.app.util.Log
import dev.openbuds.app.util.hasBluetoothConnect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns the single [BudsConnection] and publishes [BudsUiState].
 *
 * Commands are applied optimistically, then the real state is re-read from the earbuds. Errors
 * are never shown to the user; failed commands simply restore the real value on reload.
 *
 * @param context Android context; only its application context is kept.
 */
class BudsRepository(
    private val context: Context,
) {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    /** Strict FIFO of command applies; a single consumer runs them in submission order. */
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    /** Number of applies submitted but not yet finished. */
    private val pending = AtomicInteger(0)
    private val _state = MutableStateFlow(BudsUiState())

    /**
     * Current connection lifecycle and earbud state.
     *
     * The `buds` field is null while no earbud state has been loaded.
     */
    val state: StateFlow<BudsUiState> = _state.asStateFlow()

    private val _events =
        MutableSharedFlow<BudsEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Decoded earbud events such as fit test results and find-phone requests.
     *
     * Buffers 8 events without replay; the oldest is dropped on overflow.
     */
    val events: SharedFlow<BudsEvent> = _events.asSharedFlow()

    @Volatile
    private var connection: BudsConnection? = null

    @Volatile
    private var connectJob: Job? = null

    @Volatile
    private var watchJob: Job? = null

    @Volatile
    private var eventJob: Job? = null

    @Volatile
    private var reloadJob: Job? = null

    @Volatile
    private var lastAddress: String? = null

    init {
        scope.launch {
            for (job in queue) {
                try {
                    job()
                } catch (e: Exception) {
                    Log.w({ "Queued command failed" }, e)
                }
            }
        }
    }

    /**
     * Look for bonded earbuds and connect to the only one.
     *
     * Publishes [ConnectionState.ChooseDevice] when several match. Does nothing while already connecting
     * or connected. Publishes [ConnectionState.NeedsPermission], [ConnectionState.BluetoothOff] or
     * [ConnectionState.NoDevices] when a precondition fails.
     */
    fun refresh() {
        val current = _state.value.connection
        if (current is ConnectionState.Connecting || current is ConnectionState.Connected) return
        if (!appContext.hasBluetoothConnect()) {
            publish(ConnectionState.NeedsPermission)
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            publish(ConnectionState.BluetoothOff)
            return
        }
        try {
            val devices = bondedBuds(adapter)
            when {
                devices.isEmpty() -> publish(ConnectionState.NoDevices)
                devices.size == 1 -> connectTo(devices.first())
                else -> publish(ConnectionState.ChooseDevice(describe(devices)))
            }
        } catch (e: SecurityException) {
            Log.w({ "Bluetooth permission revoked" }, e)
            publish(ConnectionState.NeedsPermission)
        }
    }

    /**
     * Connect to the bonded earbuds at [address].
     *
     * Replaces any current connection. Falls back to [refresh] when no bonded device has that address.
     * Publishes [ConnectionState.NeedsPermission] or [ConnectionState.BluetoothOff] when a precondition fails.
     *
     * @param address Bluetooth address of a bonded earbud device.
     */
    fun select(address: String) {
        if (!appContext.hasBluetoothConnect()) {
            publish(ConnectionState.NeedsPermission)
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            publish(ConnectionState.BluetoothOff)
            return
        }
        try {
            val device = bondedBuds(adapter).firstOrNull { it.address == address }
            if (device == null) {
                refresh()
            } else {
                connectTo(device)
            }
        } catch (e: SecurityException) {
            Log.w({ "Bluetooth permission revoked" }, e)
            publish(ConnectionState.NeedsPermission)
        }
    }

    /** Reconnect to the last selected device, or [refresh] if none was selected. */
    fun reconnect() {
        val address = lastAddress
        if (address == null) {
            refresh()
        } else {
            select(address)
        }
    }

    /**
     * Re-read the full earbud state and publish it.
     *
     * Suspends while an apply holds the internal mutex. Does nothing without a connection, and the
     * result is dropped if applies are pending.
     */
    suspend fun reload() {
        mutex.withLock { reloadLocked() }
    }

    /**
     * Apply [command] to the earbuds after showing [optimistic] in the state.
     *
     * Returns immediately; the command runs on a background queue. Applies run one at a time, in
     * submission order. The real state is re-read once the queue drains. [optimistic] is skipped while
     * no earbud state is loaded.
     *
     * @param command Command to send to the earbuds.
     * @param optimistic Transform applied to the current earbud state before sending.
     */
    fun apply(
        command: Command,
        optimistic: (BudsState) -> BudsState,
    ) {
        applyAll(listOf(command), optimistic)
    }

    /**
     * Send [commands] in order as one batch, then re-read the real state once.
     *
     * Like [apply], but all commands run in one mutex section. Sending stops at the first command that
     * fails; the remaining commands are skipped and the reload restores the real values.
     *
     * @param commands Commands to send to the earbuds, in order.
     * @param optimistic Transform applied to the current earbud state before sending.
     */
    fun applyAll(
        commands: List<Command>,
        optimistic: (BudsState) -> BudsState,
    ) {
        pending.incrementAndGet()
        _state.update { s -> s.copy(buds = s.buds?.let(optimistic)) }
        queue.trySend {
            mutex.withLock {
                try {
                    val conn = connection
                    if (conn != null) {
                        for (command in commands) {
                            if (!BudsApi.send(conn, command)) break
                        }
                    }
                } finally {
                    // Intermediate reloads are skipped; the last queued apply restores the real state.
                    if (pending.decrementAndGet() == 0) reloadLocked()
                }
            }
        }
    }

    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    private fun connectTo(device: BluetoothDevice) {
        releaseConnection()
        lastAddress = device.address
        publish(ConnectionState.Connecting)
        val conn = BudsConnection(device, scope)
        connection = conn
        connectJob =
            scope.launch {
                val connected =
                    try {
                        conn.connect()
                    } catch (e: SecurityException) {
                        Log.w({ "Bluetooth permission missing while connecting" }, e)
                        false
                    }
                if (connection !== conn) {
                    conn.close()
                    return@launch
                }
                if (!connected) {
                    connection = null
                    publish(ConnectionState.Disconnected)
                    return@launch
                }
                mutex.withLock {
                    val buds = BudsApi.readState(conn)
                    if (connection === conn) {
                        publish(ConnectionState.Connected, buds)
                        val registered = BudsApi.registerEvents(conn)
                        Log.d { "Event registration acknowledged: $registered" }
                    }
                }
                watch(conn)
                listen(conn)
            }
    }

    /** Route spontaneous events from [conn] to [handle] until the connection is replaced or released. */
    private fun listen(conn: BudsConnection) {
        eventJob?.cancel()
        eventJob =
            scope.launch {
                conn.notifications.collect { frame ->
                    val event = Events.parse(frame)
                    if (event != null) {
                        handle(conn, event)
                    } else {
                        Log.d { "Unhandled frame: $frame" }
                    }
                }
            }
    }

    /**
     * Apply [event] to the state of [conn]. Battery and noise values are patched in place; other
     * changes schedule a debounced reload. Events from a stale connection are ignored.
     */
    private fun handle(
        conn: BudsConnection,
        event: BudsEvent,
    ) {
        Log.d { "Event from earbuds: $event" }
        if (connection !== conn) return
        when (event) {
            is BudsEvent.Battery -> {
                _state.update { s -> s.copy(buds = s.buds?.copy(battery = event.level)) }
            }

            is BudsEvent.Noise -> {
                val mode = event.values[NOISE_MODE_TYPE]
                when {
                    mode == null -> scheduleReload()
                    pending.get() == 0 -> _state.update { s -> s.copy(buds = s.buds?.copy(noise = mode)) }
                }
            }

            is BudsEvent.State, BudsEvent.DualDevice, is BudsEvent.Interaction -> {
                scheduleReload()
            }

            is BudsEvent.FitTest -> {
                _events.tryEmit(event)
            }

            is BudsEvent.FindPhoneRequest -> {
                _events.tryEmit(event)
            }

            is BudsEvent.Unknown -> {
                val hex = event.payload.joinToString("") { "%02x".format(it) }
                Log.d { "Unknown event 0x%02x payload=%s".format(event.id, hex) }
                if (event.id in STATE_RELOAD_EVENT_IDS) scheduleReload()
            }
        }
    }

    /** Restart the reload timer; the reload runs once no other event arrives for [RELOAD_DEBOUNCE_MS]. */
    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob =
            scope.launch {
                delay(RELOAD_DEBOUNCE_MS.milliseconds)
                reload()
            }
    }

    /** Publish [ConnectionState.Disconnected] when [conn] drops while it is the active connection. */
    private fun watch(conn: BudsConnection) {
        watchJob?.cancel()
        watchJob =
            scope.launch {
                conn.isConnected.first { !it }
                if (connection === conn) {
                    connection = null
                    publish(ConnectionState.Disconnected)
                }
            }
    }

    /** Cancel pending work and close the active connection without publishing. */
    private fun releaseConnection() {
        connectJob?.cancel()
        connectJob = null
        watchJob?.cancel()
        watchJob = null
        eventJob?.cancel()
        eventJob = null
        reloadJob?.cancel()
        reloadJob = null
        val old = connection
        connection = null
        old?.close()
    }

    /**
     * Re-read the earbud state. Caller must hold [mutex].
     *
     * The read is dropped if applies are pending, because it would be stale.
     */
    private suspend fun reloadLocked() {
        val conn = connection ?: return
        val buds = BudsApi.readState(conn)
        if (pending.get() != 0) return
        _state.update { s -> if (connection === conn) s.copy(buds = buds) else s }
    }

    private fun publish(
        conn: ConnectionState,
        buds: BudsState? = null,
    ) {
        _state.value = BudsUiState(conn, buds)
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    /** Return bonded devices whose name contains "buds", as in the legacy device selection. */
    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    private fun bondedBuds(adapter: BluetoothAdapter): List<BluetoothDevice> =
        adapter.bondedDevices.filter { it.name?.contains("buds", ignoreCase = true) == true }

    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    private fun describe(devices: List<BluetoothDevice>): List<BondedDevice> =
        devices.map { BondedDevice(name = it.name.orEmpty(), address = it.address) }

    private companion object {
        /** Quiet period, in milliseconds, after the last state-changing event before the earbud state is re-read. */
        const val RELOAD_DEBOUNCE_MS = 300L

        /** Key in [BudsEvent.Noise.values] that holds the noise control mode. */
        const val NOISE_MODE_TYPE = 1

        /** Event ids that signal a state change even when the payload is not decoded. */
        val STATE_RELOAD_EVENT_IDS = setOf(EventId.STATE, EventId.NOISE, EventId.DUAL_DEVICE, EventId.INTERACTION)
    }
}

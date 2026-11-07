package dev.openbuds.app.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.annotation.RequiresPermission
import dev.openbuds.app.protocol.BudsEvent
import dev.openbuds.app.protocol.Cmd
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

/**
 * Owns the single [BudsConnection] and publishes [BudsUiState].
 *
 * Commands are applied optimistically, then the real state is re-read from the earbuds. Errors
 * are never shown to the user; failed commands simply restore the real value on reload.
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

    /** Current connection lifecycle and earbud state. */
    val state: StateFlow<BudsUiState> = _state.asStateFlow()

    private val _events =
        MutableSharedFlow<BudsEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Decoded earbud events the UI or service may react to, such as fit test results and find-phone requests. */
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

    /** Looks for bonded earbuds and connects to the only one, or asks the user to choose. */
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

    /** Connects to the bonded earbuds with [address], replacing any current connection. */
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

    /** Closes the current connection and connects again to the last selected device. */
    fun reconnect() {
        val address = lastAddress
        if (address == null) {
            refresh()
        } else {
            select(address)
        }
    }

    /** Re-reads the full earbud state and publishes it. */
    suspend fun reload() {
        mutex.withLock { reloadLocked() }
    }

    /**
     * Applies [command] to the earbuds after updating the state with [optimistic], then reloads the
     * real state. Applies run one at a time.
     */
    fun apply(
        command: Command,
        optimistic: (BudsState) -> BudsState,
    ) {
        applyAll(listOf(command), optimistic)
    }

    /**
     * Like [apply], but sends all [commands] in order inside one mutex section and reloads the real
     * state once at the end. Sending stops at the first command that fails; the remaining commands
     * are skipped and the reload restores the real values.
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

    /** Disconnects and publishes [ConnectionState.Disconnected]. */
    fun close() {
        releaseConnection()
        publish(ConnectionState.Disconnected)
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
                        Log.d({ "Event registration acknowledged: $registered" })
                    }
                }
                watch(conn)
                listen(conn)
            }
    }

    /** Routes spontaneous events from [conn] to [handle] until the connection is replaced or released. */
    private fun listen(conn: BudsConnection) {
        eventJob?.cancel()
        eventJob =
            scope.launch {
                conn.notifications.collect { frame ->
                    val event = Events.parse(frame)
                    if (event != null) {
                        handle(conn, event)
                    } else if (frame.cmd == Cmd.FIND_PHONE_REQUEST) {
                        Log.d({ "Find phone request: action=${frame.data.firstOrNull()}" })
                        _events.tryEmit(
                            BudsEvent.FindPhoneRequest(
                                frame.data
                                    .firstOrNull()
                                    ?.toInt()
                                    ?.and(0xff) ?: -1,
                            ),
                        )
                    } else {
                        Log.d({ "Unhandled frame: $frame" })
                    }
                }
            }
    }

    /**
     * Applies [event] to the state of [conn]. Battery and noise values are patched in place; other
     * changes schedule a debounced reload. Events from a stale connection are ignored.
     */
    private suspend fun handle(
        conn: BudsConnection,
        event: BudsEvent,
    ) {
        Log.d({ "Event from earbuds: $event" })
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

            // Emitted from listen() (cmd 0x0502), not from 0x0204 events.
            is BudsEvent.FindPhoneRequest -> {
                Unit
            }

            is BudsEvent.Unknown -> {
                val hex = event.payload.joinToString("") { "%02x".format(it) }
                Log.d({ "Unknown event 0x%02x payload=%s".format(event.id, hex) })
                if (event.id in STATE_RELOAD_EVENT_IDS) scheduleReload()
            }
        }
    }

    /** Restarts the reload timer; the reload runs once no other event arrives for [RELOAD_DEBOUNCE_MS]. */
    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob =
            scope.launch {
                delay(RELOAD_DEBOUNCE_MS)
                reload()
            }
    }

    /** Publishes [ConnectionState.Disconnected] when [conn] drops while it is the active connection. */
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

    /** Cancels pending work and closes the active connection without publishing. */
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

    /** Re-reads the earbud state. Caller must hold [mutex]; a read is stale if applies are pending, so it is dropped. */
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

    /** Bonded devices whose name contains "buds", as in the legacy device selection. */
    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    private fun bondedBuds(adapter: BluetoothAdapter): List<BluetoothDevice> =
        adapter.bondedDevices.filter { it.name?.contains("buds", ignoreCase = true) == true }

    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    private fun describe(devices: List<BluetoothDevice>): List<BondedDevice> =
        devices.map { BondedDevice(name = it.name.orEmpty(), address = it.address) }

    private companion object {
        /** Quiet period after the last state-changing event before the earbud state is re-read. */
        const val RELOAD_DEBOUNCE_MS = 300L

        /** Noise event type that carries the noise control mode. */
        const val NOISE_MODE_TYPE = 1

        /** Event ids that signal a state change even when the payload is not decoded. */
        val STATE_RELOAD_EVENT_IDS = setOf(EventId.STATE, EventId.NOISE, EventId.DUAL_DEVICE, EventId.INTERACTION)
    }
}

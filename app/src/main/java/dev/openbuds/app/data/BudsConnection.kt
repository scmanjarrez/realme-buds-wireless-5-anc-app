package dev.openbuds.app.data

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import dev.openbuds.app.protocol.Cmd
import dev.openbuds.app.protocol.Frame
import dev.openbuds.app.protocol.Protocol
import dev.openbuds.app.util.BLUETOOTH_CONNECT_PERMISSION
import dev.openbuds.app.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * RFCOMM connection to the earbuds.
 *
 * Lifecycle: [connect] tries each strategy in order: standard SPP UUID, Qualcomm UUID (used by
 * realme Link) and, as a last resort, direct RFCOMM channel 1. A strategy succeeds when the socket
 * connects and the status query ([Cmd.QUERY_STATUS]) receives a protocol response. A socket that
 * connects without a protocol response is closed before the next strategy starts. On success,
 * [isConnected] becomes true
 * and a reader coroutine is launched in [scope]. [close], or the reader hitting end-of-stream or an
 * IO error, closes the socket and sets [isConnected] back to false.
 *
 * Threading: [connect] and [request] switch to [Dispatchers.IO] internally, so they are main-safe.
 * The reader runs on [Dispatchers.IO] in [scope]. Writes are serialized by an internal [Mutex].
 * Responses are matched to waiters through a concurrent map keyed by sequence number. All other
 * frames are emitted through [notifications] without suspending the reader.
 *
 * Timeouts: a request waits up to 2 s for its response. Multi-connect requests wait up to 10 s,
 * because the earbuds answer them only after the peer disconnects. A timeout or an IO error makes
 * [request] return null.
 *
 * @param device Bonded Bluetooth device to connect to.
 * @param scope Scope that owns the reader coroutine. It must outlive the connection.
 */
class BudsConnection(
    private val device: BluetoothDevice,
    private val scope: CoroutineScope,
) : Requester {
    private val _notifications =
        MutableSharedFlow<Frame>(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * Unsolicited frames: non-responses, or responses with no pending request.
     *
     * Hot flow with no replay. Holds up to 16 frames for slow collectors; when the buffer is full
     * the oldest frame is dropped.
     */
    val notifications: SharedFlow<Frame> = _notifications.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)

    /** True after a successful [connect], false after [close] or when the socket drops. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    @Volatile
    private var socket: BluetoothSocket? = null
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Frame>>()
    private val writeLock = Mutex()
    private val seq = AtomicInteger(SEQ_START)

    /**
     * Tries each connection strategy in order and stops at the first one that answers the protocol.
     *
     * Suspends while sockets connect and the status query runs. Runs on [Dispatchers.IO].
     *
     * @return True if a strategy answered the protocol, false if all of them failed.
     */
    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    suspend fun connect(): Boolean =
        withContext(Dispatchers.IO) {
            val strategies: List<Pair<String, () -> BluetoothSocket>> =
                listOf(
                    "SPP 00001101" to { createSppSocket() },
                    "Qualcomm 079A" to { createQualcommSocket() },
                    "direct channel 1" to { createChannelOneSocket() },
                )
            for ((name, make) in strategies) {
                if (tryStrategy(name, make)) return@withContext true
            }
            Log.w({ "No connection strategy succeeded" })
            false
        }

    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    private suspend fun tryStrategy(
        name: String,
        make: () -> BluetoothSocket,
    ): Boolean {
        Log.d { "Trying strategy $name" }
        val candidate =
            try {
                make().also { it.connect() }
            } catch (e: Exception) {
                Log.w({ "Strategy $name failed to connect" }, e)
                return false
            }
        socket = candidate
        startReader(candidate)
        val response = request(Cmd.QUERY_STATUS)
        if (response?.isResponse == true) {
            _isConnected.value = true
            Log.d { "Connected using strategy $name" }
            return true
        }
        Log.w({ "Strategy $name connected but got no protocol response" })
        close()
        return false
    }

    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    private fun createSppSocket(): BluetoothSocket = device.createRfcommSocketToServiceRecord(SPP)

    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    private fun createQualcommSocket(): BluetoothSocket = device.createRfcommSocketToServiceRecord(QUALCOMM)

    @RequiresPermission(BLUETOOTH_CONNECT_PERMISSION)
    private fun createChannelOneSocket(): BluetoothSocket {
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, 1) as BluetoothSocket
    }

    private fun startReader(s: BluetoothSocket) {
        scope.launch(Dispatchers.IO) {
            val parser = Protocol.Parser()
            val buf = ByteArray(512)
            try {
                val input = s.inputStream
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    for (frame in parser.feed(buf, n)) {
                        route(frame)
                    }
                }
                Log.d { "Input stream ended" }
            } catch (e: IOException) {
                Log.w({ "Read failed, connection dropped" }, e)
            } finally {
                dropSocket(s)
            }
        }
    }

    private fun route(frame: Frame) {
        val waiter = if (frame.isResponse) pending[frame.seq] else null
        if (waiter != null) {
            waiter.complete(frame)
        } else {
            _notifications.tryEmit(frame)
        }
    }

    /**
     * Sends a command and waits for the response with the same sequence number.
     *
     * Suspends until the response arrives or the timeout expires: 2 s by default, 10 s for
     * [Cmd.SET_MULTI_CONNECT]. Writes are serialized through an internal [Mutex] and run on
     * [Dispatchers.IO]. Sequence numbers are one byte, taken from a counter that starts at 0x40.
     *
     * @param cmd Command identifier, as defined in [Cmd].
     * @param data Payload bytes that follow the frame header.
     * @return The matching response frame, or null if the socket is not open, the request times
     *   out, or the write fails with an IO error.
     */
    override suspend fun request(
        cmd: Int,
        data: ByteArray,
    ): Frame? {
        val s = socket ?: return null
        val n = seq.incrementAndGet() and 0xff
        val deferred = CompletableDeferred<Frame>()
        pending[n] = deferred
        val timeoutMs = if (cmd == Cmd.SET_MULTI_CONNECT) SLOW_REQUEST_TIMEOUT_MS else REQUEST_TIMEOUT_MS
        try {
            writeLock.withLock {
                withContext(Dispatchers.IO) {
                    s.outputStream.write(Protocol.build(cmd, n, data))
                    s.outputStream.flush()
                }
            }
            return withTimeoutOrNull(timeoutMs.milliseconds) { deferred.await() }
        } catch (e: IOException) {
            Log.w({ "Send failed" }, e)
            return null
        } finally {
            pending.remove(n)
        }
    }

    /**
     * Closes the active socket and sets [isConnected] to false.
     *
     * Safe to call when not connected. Pending requests are not canceled. They fail when their
     * timeout expires.
     */
    fun close() {
        val s = socket
        socket = null
        _isConnected.value = false
        if (s != null) closeQuietly(s)
    }

    /** Called by the reader when its stream ends. Only acts if [s] is still the active socket. */
    private fun dropSocket(s: BluetoothSocket) {
        closeQuietly(s)
        if (socket === s) {
            socket = null
            _isConnected.value = false
        }
        Log.d { "Connection closed" }
    }

    private fun closeQuietly(s: BluetoothSocket) {
        try {
            s.close()
        } catch (e: IOException) {
            Log.w({ "Error closing socket" }, e)
        }
    }

    private companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val QUALCOMM: UUID = UUID.fromString("0000079A-D102-11E1-9B23-00025B00A5A5")
        const val SEQ_START = 0x40
        const val REQUEST_TIMEOUT_MS = 2000L

        /** Multi-connect answers only after the peer disconnects, observed to take longer than 2 s. */
        const val SLOW_REQUEST_TIMEOUT_MS = 10_000L
    }
}

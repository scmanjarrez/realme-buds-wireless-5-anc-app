package dev.openbuds.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.openbuds.app.OpenBudsApp
import dev.openbuds.app.R
import dev.openbuds.app.data.BudsUiState
import dev.openbuds.app.data.ConnectionState
import dev.openbuds.app.protocol.BudsEvent
import dev.openbuds.app.protocol.Feature
import dev.openbuds.app.protocol.FindPhone
import dev.openbuds.app.ui.MainActivity
import dev.openbuds.app.util.Log
import dev.openbuds.app.util.hasBluetoothConnect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Foreground service keeping the earbud connection alive.
 *
 * It runs only while find-my-phone is enabled on the earbuds and Bluetooth connect permission is granted.
 * It stops itself when find-my-phone is turned off, the permission is missing or Bluetooth is off.
 * While disconnected it reconnects with a backoff of 5 s, 15 s and 60 s, then every 60 s; the attempt
 * counter resets on connect.
 *
 * Two notification channels are used: a low-importance persistent "connection" channel and a
 * high-importance ring channel. The ring notification is shown while [PhoneRinger] rings and offers a
 * Stop action ([ACTION_STOP_RING]). Find-my-phone requests from the earbuds toggle the ringer, see
 * 'Unsolicited frames (0x05xx)' in docs/PROTOCOL.md.
 */
class BudsService : Service() {
    private val app: OpenBudsApp
        get() = application as OpenBudsApp

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var channelsCreated = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    /** Unbound service: always returns null. */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Enters the foreground and handles the stop-ring action.
     *
     * Starts the background work only on the first call. Stops itself when Bluetooth connect permission
     * is missing.
     *
     * @param intent The start intent; null when the system restarts the service after killing it.
     * @param flags Start flags supplied by the system; not used.
     * @param startId Token identifying this start request; not used.
     * @return [START_STICKY] while running, [START_NOT_STICKY] when stopping because of missing permission.
     */
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!hasBluetoothConnect()) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannels()
        ServiceCompat.startForeground(this, CONNECTION_ID, connectionNotification(), foregroundServiceType())
        if (intent?.action == ACTION_STOP_RING) app.phoneRinger.stop()
        if (!started) {
            started = true
            startWork()
        }
        return START_STICKY
    }

    /** Stops ringing and cancels all background work. */
    override fun onDestroy() {
        app.phoneRinger.stop()
        cancelRingNotification()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    /** Launches the find-my-phone, ring notification and connection watch jobs. */
    private fun startWork() {
        val repository = app.repository
        val ringer = app.phoneRinger
        scope.launch {
            repository.events.collect { event ->
                if (event is BudsEvent.FindPhoneRequest) {
                    if (event.action == FindPhone.STOP) ringer.stop() else ringer.toggle()
                }
            }
        }
        scope.launch {
            ringer.isRinging.collect { ringing ->
                if (ringing) showRingNotification() else cancelRingNotification()
            }
        }
        val connection = repository.state.value.connection
        if (connection !is ConnectionState.Connected && connection !is ConnectionState.Connecting) {
            repository.refresh()
        }
        scope.launch {
            repository.state.collect { onStateChanged(it) }
        }
    }

    /**
     * Reacts to a connection or earbud state change.
     *
     * Any pending reconnect is canceled first. Stops the service when find-my-phone is off or the
     * app cannot use Bluetooth; schedules a reconnect with backoff when disconnected.
     */
    private fun onStateChanged(ui: BudsUiState) {
        reconnectJob?.cancel()
        reconnectJob = null
        val buds = ui.buds
        if (buds != null && buds.features[Feature.FIND_MY_PHONE] != true) {
            stopSelf()
            return
        }
        when (ui.connection) {
            ConnectionState.NeedsPermission, ConnectionState.BluetoothOff -> stopSelf()
            ConnectionState.Disconnected -> scheduleReconnect()
            ConnectionState.Connected -> reconnectAttempts = 0
            else -> Unit
        }
    }

    /** Schedules one reconnect attempt using the backoff delay for the current attempt number. */
    private fun scheduleReconnect() {
        val delayMs = RECONNECT_DELAYS_MS.getOrNull(reconnectAttempts) ?: RECONNECT_STEADY_MS
        reconnectAttempts++
        reconnectJob =
            scope.launch {
                delay(delayMs.milliseconds)
                app.repository.reconnect()
            }
    }

    /** Creates the connection and ring notification channels. Runs once per service instance. */
    private fun ensureChannels() {
        if (channelsCreated) return
        channelsCreated = true
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_CONNECTION, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.service_channel_connection))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_RING, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(getString(R.string.home_find_my_phone))
                .build(),
        )
    }

    /** Foreground service type for connected devices on Android 10 and later, 0 before. */
    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }

    /** Low-priority persistent notification shown while the service runs. */
    private fun connectionNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_stat_buds)
            .setContentTitle(getString(R.string.service_connected_title))
            .setContentText(getString(R.string.service_connected_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .build()

    /** High-priority alarm notification with a Stop action, shown while the phone is ringing. */
    private fun showRingNotification() {
        val stopIntent = Intent(this, BudsService::class.java).setAction(ACTION_STOP_RING)
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_RING)
                .setSmallIcon(R.drawable.ic_stat_buds)
                .setContentTitle(getString(R.string.service_ring_title))
                .setContentText(getString(R.string.service_ring_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(openAppIntent())
                .addAction(0, getString(R.string.findphone_stop), stopPending)
                .build()
        try {
            NotificationManagerCompat.from(this).notify(RING_ID, notification)
        } catch (e: SecurityException) {
            Log.w({ "Cannot post ring notification" }, e)
        }
    }

    /** Removes the ring notification if it is shown. */
    private fun cancelRingNotification() {
        NotificationManagerCompat.from(this).cancel(RING_ID)
    }

    /** Opens [MainActivity] when a notification is tapped. */
    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

    /** Factory and controls for [BudsService]. */
    companion object {
        /** Intent action that stops the phone ringer from the ring notification. */
        const val ACTION_STOP_RING = "dev.openbuds.app.action.STOP_RING"

        /**
         * Starts the service in the foreground. Failures are logged and never thrown.
         *
         * @param context Context used to start the service.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, BudsService::class.java))
            } catch (e: Exception) {
                Log.w({ "Could not start BudsService" }, e)
            }
        }

        /**
         * Stops the service, which also stops ringing through [onDestroy].
         *
         * @param context Context used to stop the service.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, BudsService::class.java))
        }

        private const val CHANNEL_CONNECTION = "connection"
        private const val CHANNEL_RING = "ring"
        private const val CONNECTION_ID = 1
        private const val RING_ID = 2
        private const val RECONNECT_STEADY_MS = 60_000L
        private val RECONNECT_DELAYS_MS = listOf(5_000L, 15_000L, 60_000L)
    }
}

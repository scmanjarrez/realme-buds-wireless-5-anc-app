package dev.openbuds.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import dev.openbuds.app.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Rings the phone at full alarm volume so the user can locate it.
 *
 * Plays the system alarm tone on a looping [MediaPlayer], vibrates when supported,
 * and stops automatically after [AUTO_STOP_MS]. The previous alarm volume is restored on stop.
 *
 * @param context Any context; the application context is retained.
 */
class PhoneRinger(
    context: Context,
) {
    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager? =
        appContext.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isRinging = MutableStateFlow(false)

    /** Whether the phone is currently ringing. Updated on the thread that calls [start] or [stop]. */
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    private var player: MediaPlayer? = null
    private var savedVolume: Int? = null
    private var autoStopJob: Job? = null

    /**
     * Starts ringing at full alarm volume with vibration.
     *
     * Schedules an automatic stop after [AUTO_STOP_MS]. No-op if already ringing. On any platform failure
     * the ringer stays silent and [isRinging] remains false.
     */
    fun start() {
        if (_isRinging.value) return
        try {
            raiseAlarmVolume()
            startPlayer()
            startVibration()
        } catch (e: Exception) {
            Log.w({ "PhoneRinger failed to start" }, e)
            stop()
            return
        }
        _isRinging.value = true
        autoStopJob =
            scope.launch {
                delay(AUTO_STOP_MS.milliseconds)
                stop()
            }
    }

    /**
     * Stops playback and vibration and restores the previous alarm volume.
     *
     * Also cancels the pending auto-stop. Safe to call when not ringing.
     */
    fun stop() {
        autoStopJob?.cancel()
        autoStopJob = null
        try {
            player?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        } catch (e: Exception) {
            Log.w({ "PhoneRinger failed to stop player" }, e)
        }
        player = null
        cancelVibration()
        restoreAlarmVolume()
        _isRinging.value = false
    }

    /**
     * Starts ringing if idle, otherwise stops it.
     *
     * Used for find-my-phone requests from the earbuds (0x0502 data 5), which the protocol treats as a
     * toggle; see 'Unsolicited frames (0x05xx)' in docs/PROTOCOL.md.
     */
    fun toggle() {
        if (_isRinging.value) stop() else start()
    }

    private fun raiseAlarmVolume() {
        val am = audioManager ?: return
        savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
    }

    private fun restoreAlarmVolume() {
        val volume = savedVolume ?: return
        savedVolume = null
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        } catch (e: Exception) {
            Log.w({ "PhoneRinger failed to restore volume" }, e)
        }
    }

    private fun startPlayer() {
        val uri = alarmUri() ?: throw IllegalStateException("No default alarm, ringtone or notification sound")
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        mp.setDataSource(appContext, uri)
        mp.isLooping = true
        routeToBuiltInSpeaker(mp)
        mp.prepare()
        mp.start()
    }

    private fun routeToBuiltInSpeaker(mp: MediaPlayer) {
        // MediaPlayer.setPreferredDevice requires API 28; older devices keep the default routing.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val speaker =
            audioManager
                ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (speaker == null) {
            Log.d { "PhoneRinger found no built-in speaker" }
            return
        }
        mp.setPreferredDevice(speaker)
    }

    private fun alarmUri(): Uri? =
        listOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_RINGTONE,
            RingtoneManager.TYPE_NOTIFICATION,
        ).firstNotNullOfOrNull { RingtoneManager.getDefaultUri(it) }

    private fun startVibration() {
        // VibrationEffect.createWaveform requires API 26; older devices skip vibration.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val vibrator = appContext.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN_MS, VIBRATION_REPEAT_INDEX))
    }

    private fun cancelVibration() {
        try {
            appContext.getSystemService(Vibrator::class.java)?.cancel()
        } catch (e: Exception) {
            Log.w({ "PhoneRinger failed to cancel vibration" }, e)
        }
    }

    private companion object {
        /** Maximum ringing duration before the ringer stops on its own. */
        const val AUTO_STOP_MS = 30_000L

        /** Off/on timings in milliseconds for the repeating vibration. */
        val VIBRATION_PATTERN_MS = longArrayOf(0L, 500L, 500L)

        /** Index at which the vibration pattern repeats; 0 loops indefinitely. */
        const val VIBRATION_REPEAT_INDEX = 0
    }
}

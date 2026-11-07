package dev.openbuds.app.util

import dev.openbuds.app.BuildConfig
import android.util.Log as AndroidLog

/**
 * Debug-only logger.
 *
 * Messages are only emitted in debug builds and are never shown on screen.
 * Message builders are lambdas so release builds never construct the string.
 */
object Log {
    private const val TAG = "OpenBuds"

    /**
     * Logs a debug message. No-op outside debug builds.
     *
     * @param msg lazily built message, evaluated only in debug builds.
     */
    fun d(msg: () -> String) {
        if (BuildConfig.DEBUG) {
            AndroidLog.d(TAG, msg())
        }
    }

    /**
     * Logs a warning. No-op outside debug builds.
     *
     * @param msg lazily built message, evaluated only in debug builds.
     * @param t optional throwable logged together with the message.
     */
    fun w(
        msg: () -> String,
        t: Throwable? = null,
    ) {
        if (BuildConfig.DEBUG) {
            AndroidLog.w(TAG, msg(), t)
        }
    }
}

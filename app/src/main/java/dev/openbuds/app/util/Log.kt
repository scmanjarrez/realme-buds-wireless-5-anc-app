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

    /** Logs a debug message. No-op outside debug builds. */
    fun d(msg: () -> String) {
        if (BuildConfig.DEBUG) {
            AndroidLog.d(TAG, msg())
        }
    }

    /** Logs a warning with an optional [t]. No-op outside debug builds. */
    fun w(
        msg: () -> String,
        t: Throwable? = null,
    ) {
        if (BuildConfig.DEBUG) {
            AndroidLog.w(TAG, msg(), t)
        }
    }

    /** Logs an error with an optional [t]. No-op outside debug builds. */
    fun e(
        msg: () -> String,
        t: Throwable? = null,
    ) {
        if (BuildConfig.DEBUG) {
            AndroidLog.e(TAG, msg(), t)
        }
    }
}

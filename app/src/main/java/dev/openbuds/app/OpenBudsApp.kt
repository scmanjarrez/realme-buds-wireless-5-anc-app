package dev.openbuds.app

import android.app.Application
import dev.openbuds.app.data.BudsRepository
import dev.openbuds.app.service.PhoneRinger
import dev.openbuds.app.ui.theme.ThemePreferences

/**
 * Process-wide owner of the earbuds connection and the phone ringer.
 *
 * The [BudsRepository] and [PhoneRinger] are created lazily on first access and live as long as
 * the process, so every screen and view model shares the same Bluetooth connection and ringer.
 */
class OpenBudsApp : Application() {
    /** Shared Bluetooth connection to the earbuds, created on first access. */
    val repository: BudsRepository by lazy { BudsRepository(this) }

    /** Shared phone ringer used by the "find my phone" feature, created on first access. */
    val phoneRinger: PhoneRinger by lazy { PhoneRinger(this) }

    /** Shared theme mode preference, read from and written to local storage on first access. */
    val themePreferences: ThemePreferences by lazy { ThemePreferences(this) }
}

package de.erikedits.pocketbot.data

import android.content.Context
import androidx.core.content.edit

/** Non-secret app settings. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var termsAccepted: Boolean
        get() = sp.getBoolean("terms_accepted", false)
        set(value) = sp.edit { putBoolean("terms_accepted", value) }

    var notificationsAsked: Boolean
        get() = sp.getBoolean("notifications_asked", false)
        set(value) = sp.edit { putBoolean("notifications_asked", value) }

    var batteryProtection: Boolean
        get() = sp.getBoolean("battery_protection", true)
        set(value) = sp.edit { putBoolean("battery_protection", value) }

    /** Pause all bots at or below this battery level (percent). */
    var batteryLow: Int
        get() = sp.getInt("battery_low", 15)
        set(value) = sp.edit { putInt("battery_low", value.coerceIn(5, 50)) }

    /** Start them again from this level on (percent). */
    var batteryResume: Int
        get() = sp.getInt("battery_resume", 30)
        set(value) = sp.edit { putInt("battery_resume", value.coerceIn(10, 90)) }

    var userJson: String?
        get() = sp.getString("user_json", null)
        set(value) = sp.edit { if (value == null) remove("user_json") else putString("user_json", value) }
}

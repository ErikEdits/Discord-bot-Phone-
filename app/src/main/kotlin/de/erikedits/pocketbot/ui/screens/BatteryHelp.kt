package de.erikedits.pocketbot.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import de.erikedits.pocketbot.R

/** Battery optimisation + manufacturer tips (SPEC 4.2). */
object BatteryHelp {
    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** The app's whole job is a background bot, which is an accepted use case for this request. */
    @SuppressLint("BatteryLife")
    fun requestUnrestricted(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { context.startActivity(direct) }.onFailure { runCatching { context.startActivity(fallback) } }
    }

    fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }

    @StringRes
    fun manufacturerTip(): Int {
        val maker = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any(maker::contains) -> R.string.battery_tip_xiaomi
            "samsung" in maker -> R.string.battery_tip_samsung
            listOf("huawei", "honor").any(maker::contains) -> R.string.battery_tip_huawei
            listOf("oppo", "realme", "oneplus").any(maker::contains) -> R.string.battery_tip_oppo
            listOf("vivo", "iqoo").any(maker::contains) -> R.string.battery_tip_vivo
            else -> R.string.battery_tip_generic
        }
    }
}

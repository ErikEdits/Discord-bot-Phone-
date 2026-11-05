package de.erikedits.pocketbot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.erikedits.pocketbot.PocketBotApp

/** Auto-start after reboot / app update for bots that were running (SPEC 4.3). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as PocketBotApp).container
        if (container.bots.bots.value.any { it.shouldRun } && !container.crashLog.crashedRecently()) {
            runCatching { BotRuntimeService.restore(context) }
        }
    }
}

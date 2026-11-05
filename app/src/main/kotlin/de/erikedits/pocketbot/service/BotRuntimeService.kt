package de.erikedits.pocketbot.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import de.erikedits.pocketbot.PocketBotApp
import de.erikedits.pocketbot.R
import de.erikedits.pocketbot.engine.BotStatus
import de.erikedits.pocketbot.engine.EngineEvent
import de.erikedits.pocketbot.engine.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the bots alive in the background (SPEC 4.1): foreground service of type specialUse with a
 * permanent notification and a stop button. Also does battery protection (SPEC 4.3).
 */
class BotRuntimeService : LifecycleService() {

    private val container get() = (application as PocketBotApp).container
    private val manager get() = container.botManager
    private var handledCommand = false
    private var batteryPaused = false
    private var batteryLevel = -1

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBattery(intent)
    }

    override fun onCreate() {
        super.onCreate()
        goForeground()
        ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch {
            manager.states.collect {
                if (handledCommand && !manager.hasWork()) shutdown() else updateNotification()
            }
        }
        lifecycleScope.launch { manager.events.collect(::onBotEvent) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        goForeground()
        handledCommand = true
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_BOT_ID)?.let { manager.start(it) }
            ACTION_STOP_ALL -> manager.stopAll()
            // null intent = system restarted us after killing the process (START_STICKY)
            ACTION_RESTORE, null -> container.bots.bots.value.filter { it.shouldRun }.forEach { manager.start(it.id) }
        }
        if (!manager.hasWork()) shutdown()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }
        super.onDestroy()
    }

    private fun shutdown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_RUNNING, buildNotification(), type)
    }

    private fun buildNotification(): Notification {
        val states = manager.states.value
        val online = states.values.count { it.status == BotStatus.ONLINE }
        val active = states.values.count { it.status.isActive }
        val text = when {
            batteryPaused -> getString(R.string.notif_paused_battery, batteryLevel)
            active == 0 -> getString(R.string.notif_starting)
            else -> resources.getQuantityString(R.plurals.notif_bots_online, online, online, active)
        }
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BotRuntimeService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Notifications.openAppIntent(this))
            .addAction(0, getString(R.string.notif_stop_all), stopIntent)
            .build()
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(Notifications.ID_RUNNING, buildNotification())
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        batteryLevel = level * 100 / scale
        val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val prefs = container.prefs
        if (!prefs.batteryProtection) {
            if (batteryPaused) resumeFromBattery()
            return
        }
        if (!batteryPaused && !charging && batteryLevel <= prefs.batteryLow && manager.activeCount() > 0) {
            batteryPaused = true
            manager.pauseAll()
            Notifications.alert(this, getString(R.string.alert_battery_paused_title), getString(R.string.alert_battery_paused_text, batteryLevel, prefs.batteryResume))
            updateNotification()
        } else if (batteryPaused && (charging || batteryLevel >= prefs.batteryResume)) {
            resumeFromBattery()
        }
    }

    private fun resumeFromBattery() {
        batteryPaused = false
        manager.resumePaused()
        updateNotification()
    }

    private fun onBotEvent(e: BotEvent) {
        val name = container.bots.get(e.botId)?.name ?: e.botId
        when (val event = e.event) {
            is EngineEvent.JoinedGuild -> Notifications.alert(this, getString(R.string.alert_joined_title, name), getString(R.string.alert_joined_text, event.guildName, event.memberCount))
            EngineEvent.InvalidToken -> Notifications.alert(this, getString(R.string.alert_invalid_token_title, name), getString(R.string.alert_invalid_token_text))
            is EngineEvent.Crashed -> Notifications.alert(this, getString(R.string.alert_crashed_title, name), getString(R.string.alert_crashed_text, event.reason))
        }
    }

    companion object {
        const val ACTION_START = "de.erikedits.pocketbot.action.START"
        const val ACTION_STOP_ALL = "de.erikedits.pocketbot.action.STOP_ALL"
        const val ACTION_RESTORE = "de.erikedits.pocketbot.action.RESTORE"
        const val EXTRA_BOT_ID = "bot_id"

        fun startBot(context: Context, botId: String) = ContextCompat.startForegroundService(
            context, Intent(context, BotRuntimeService::class.java).setAction(ACTION_START).putExtra(EXTRA_BOT_ID, botId),
        )

        fun restore(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, BotRuntimeService::class.java).setAction(ACTION_RESTORE),
        )
    }
}

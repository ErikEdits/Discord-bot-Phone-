package de.erikedits.pocketbot

import android.app.Application
import de.erikedits.pocketbot.data.AppContainer
import de.erikedits.pocketbot.service.Notifications

class PocketBotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
    }
}

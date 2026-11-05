package de.erikedits.pocketbot.data

import android.content.Context
import android.os.Build
import de.erikedits.pocketbot.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves the last app crash to a file so it can be shown and shared from the settings. */
class CrashLog(context: Context) {
    private val file = File(context.filesDir, "last_crash.txt")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file.writeText(
                    "Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())}\n" +
                        "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "Thread: ${thread.name}\n\n" + trace.take(20_000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(): String? = file.takeIf { it.exists() }?.readText()

    fun clear() {
        file.delete()
    }
}

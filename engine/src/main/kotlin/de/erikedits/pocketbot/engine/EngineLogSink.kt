package de.erikedits.pocketbot.engine

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Receives JDA's own log output (via [PocketBotSlf4jProvider]) and forwards it to the log buffers
 * of the running engines, so errors inside JDA show up in the app's live log.
 */
object EngineLogSink {
    private val listeners = CopyOnWriteArrayList<LogBuffer>()

    fun register(buffer: LogBuffer) {
        if (buffer !in listeners) listeners += buffer
    }

    fun unregister(buffer: LogBuffer) {
        listeners -= buffer
    }

    fun publish(level: LogLevel, source: String, message: String, error: Throwable?) {
        val text = buildString {
            append("[").append(source).append("] ").append(message)
            if (error != null) append("\n").append(error.shortTrace())
        }
        listeners.forEach { it.add(level, text) }
    }
}

/** First lines of a stack trace incl. causes; enough to diagnose without flooding the log. */
fun Throwable.shortTrace(maxFrames: Int = 6): String = buildString {
    var t: Throwable? = this@shortTrace
    var depth = 0
    while (t != null && depth < 4) {
        if (depth > 0) append("\nCaused by: ")
        append(t.javaClass.name).append(": ").append(t.message ?: "")
        t.stackTrace.take(maxFrames).forEach { append("\n  at ").append(it) }
        t = t.cause?.takeIf { it !== t }
        depth++
    }
}

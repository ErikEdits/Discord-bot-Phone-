package de.erikedits.pocketbot.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Ring buffer of the last [capacity] log lines, exposed as a StateFlow for the UI.
 * With a [file], every line is also appended there, so the log survives an app crash.
 */
class LogBuffer(
    private val capacity: Int = 2000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val file: File? = null,
) {
    private val _entries = MutableStateFlow(file?.let { LogFile.read(it, 200) } ?: emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(level: LogLevel, message: String) {
        val entry = LogEntry(clock(), level, message)
        _entries.update { old -> if (old.size >= capacity) old.drop(old.size - capacity + 1) + entry else old + entry }
        file?.let { LogFile.append(it, entry) }
    }

    fun info(message: String) = add(LogLevel.INFO, message)
    fun warn(message: String) = add(LogLevel.WARN, message)
    fun error(message: String) = add(LogLevel.ERROR, message)
}

/** Simple line format "time<TAB>LEVEL<TAB>message" with escaped newlines. */
object LogFile {
    private const val MAX_BYTES = 256 * 1024L
    private val lock = Any()

    fun append(file: File, entry: LogEntry) = synchronized(lock) {
        runCatching {
            file.parentFile?.mkdirs()
            val msg = entry.message.replace("\\", "\\\\").replace("\n", "\\n")
            file.appendText("${entry.time}\t${entry.level}\t$msg\n")
            if (file.length() > MAX_BYTES) {
                val keep = file.readLines().takeLast(500)
                file.writeText(keep.joinToString("\n", postfix = "\n"))
            }
        }
        Unit
    }

    fun read(file: File, last: Int = 300): List<LogEntry> = synchronized(lock) {
        runCatching {
            if (!file.exists()) return@runCatching emptyList()
            file.readLines().takeLast(last).mapNotNull { line ->
                val parts = line.split('\t', limit = 3)
                if (parts.size < 3) return@mapNotNull null
                val level = runCatching { LogLevel.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
                val msg = parts[2].replace("\\n", "\n").replace("\\\\", "\\")
                LogEntry(parts[0].toLongOrNull() ?: 0, level, msg)
            }
        }.getOrDefault(emptyList())
    }
}

package de.erikedits.pocketbot.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Ring buffer of the last [capacity] log lines, exposed as a StateFlow for the UI. */
class LogBuffer(private val capacity: Int = 2000, private val clock: () -> Long = System::currentTimeMillis) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(level: LogLevel, message: String) {
        val entry = LogEntry(clock(), level, message)
        _entries.update { old -> if (old.size >= capacity) old.drop(old.size - capacity + 1) + entry else old + entry }
    }

    fun info(message: String) = add(LogLevel.INFO, message)
    fun warn(message: String) = add(LogLevel.WARN, message)
    fun error(message: String) = add(LogLevel.ERROR, message)
}

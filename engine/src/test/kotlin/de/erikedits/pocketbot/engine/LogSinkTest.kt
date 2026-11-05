package de.erikedits.pocketbot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.LoggerFactory

class LogSinkTest {
    @Test
    fun `jda logs reach registered buffers`() {
        val buffer = LogBuffer()
        EngineLogSink.register(buffer)
        try {
            LoggerFactory.getLogger("net.dv8tion.jda.internal.requests.WebSocketClient")
                .error("Failed {} times", 3, IllegalStateException("boom"))
        } finally {
            EngineLogSink.unregister(buffer)
        }
        val entry = buffer.entries.value.single()
        assertEquals(LogLevel.ERROR, entry.level)
        assertTrue(entry.message, entry.message.startsWith("[WebSocketClient] Failed 3 times"))
        assertTrue(entry.message, entry.message.contains("IllegalStateException: boom"))
    }

    @Test
    fun `debug is dropped and unregistered buffers get nothing`() {
        val buffer = LogBuffer()
        EngineLogSink.register(buffer)
        LoggerFactory.getLogger("x").debug("noise")
        EngineLogSink.unregister(buffer)
        LoggerFactory.getLogger("x").warn("after")
        assertTrue(buffer.entries.value.isEmpty())
    }

    @Test
    fun `short trace includes cause`() {
        val trace = RuntimeException("outer", NoClassDefFoundError("inner")).shortTrace()
        assertTrue(trace.contains("RuntimeException: outer"))
        assertTrue(trace.contains("Caused by: java.lang.NoClassDefFoundError: inner"))
    }
}

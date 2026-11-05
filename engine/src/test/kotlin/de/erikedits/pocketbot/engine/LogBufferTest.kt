package de.erikedits.pocketbot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBufferTest {
    @Test
    fun `keeps only the newest entries`() {
        var t = 0L
        val buffer = LogBuffer(capacity = 3, clock = { t++ })
        repeat(5) { buffer.info("line $it") }
        assertEquals(listOf("line 2", "line 3", "line 4"), buffer.entries.value.map { it.message })
    }

    @Test
    fun `levels are kept`() {
        val buffer = LogBuffer()
        buffer.error("boom")
        assertEquals(LogLevel.ERROR, buffer.entries.value.single().level)
    }

    @Test
    fun `active statuses`() {
        assertTrue(BotStatus.ONLINE.isActive)
        assertTrue(BotStatus.RECONNECTING.isActive)
        assertFalse(BotStatus.PAUSED.isActive)
        assertFalse(BotStatus.INVALID_TOKEN.isActive)
    }

    @Test
    fun `malformed token fails without crashing`() {
        val engine = JdaBotEngine("not-a-token")
        engine.start()
        val deadline = System.currentTimeMillis() + 10_000
        while (engine.state.value.status == BotStatus.CONNECTING && System.currentTimeMillis() < deadline) Thread.sleep(50)
        val status = engine.state.value.status
        assertTrue("status was $status", status == BotStatus.INVALID_TOKEN || status == BotStatus.ERROR)
    }
}

class LogFileTest {
    @Test
    fun `log survives in file and is reloaded`() {
        val file = kotlin.io.path.createTempFile("pb", ".log").toFile()
        file.delete()
        val first = LogBuffer(file = file)
        first.info("hello")
        first.error("multi\nline \\ text")
        val reloaded = LogBuffer(file = file)
        assertEquals(listOf("hello", "multi\nline \\ text"), reloaded.entries.value.map { it.message })
        assertEquals(LogLevel.ERROR, reloaded.entries.value.last().level)
        file.delete()
    }
}

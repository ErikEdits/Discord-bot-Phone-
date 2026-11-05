package de.erikedits.pocketbot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

/** Guards the JDA bytecode patch (see engine/build.gradle.kts): no Android-incompatible regex flags. */
class JdaAndroidPatchTest {
    @Test
    fun `jda checks regexes have no UNICODE_CHARACTER_CLASS flag`() {
        val checks = Class.forName("net.dv8tion.jda.internal.utils.Checks")
        val patterns = checks.declaredFields
            .filter { it.type == Pattern::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.isAccessible = true; it.name to (it.get(null) as Pattern) }
        assertTrue("no Pattern fields found", patterns.isNotEmpty())
        patterns.forEach { (name, p) ->
            assertEquals("$name still has UNICODE_CHARACTER_CLASS", 0, p.flags() and Pattern.UNICODE_CHARACTER_CLASS)
        }
    }

    @Test
    fun `gateway intents initialise (crashed on Android before)`() {
        assertTrue(net.dv8tion.jda.api.requests.GatewayIntent.getRaw(net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MESSAGES) > 0)
    }
}

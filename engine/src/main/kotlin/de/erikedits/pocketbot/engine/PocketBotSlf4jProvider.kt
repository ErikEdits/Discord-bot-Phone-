package de.erikedits.pocketbot.engine

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import java.util.concurrent.ConcurrentHashMap

/** SLF4J 2 provider (found via META-INF/services) that routes JDA's logs into [EngineLogSink]. */
class PocketBotSlf4jProvider : SLF4JServiceProvider {
    private val loggers = ConcurrentHashMap<String, Logger>()
    private val factory = ILoggerFactory { name -> loggers.getOrPut(name) { SinkLogger(name) } }
    private val markers = BasicMarkerFactory()
    private val mdc = NOPMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = factory
    override fun getMarkerFactory(): IMarkerFactory = markers
    override fun getMDCAdapter(): MDCAdapter = mdc
    override fun getRequestedApiVersion(): String = "2.0.99"
    override fun initialize() = Unit
}

private class SinkLogger(loggerName: String) : LegacyAbstractLogger() {
    private val shortName = loggerName.substringAfterLast('.')

    init {
        name = loggerName
    }

    override fun isTraceEnabled(): Boolean = false
    override fun isDebugEnabled(): Boolean = false
    override fun isInfoEnabled(): Boolean = true
    override fun isWarnEnabled(): Boolean = true
    override fun isErrorEnabled(): Boolean = true
    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(level: Level, marker: Marker?, messagePattern: String?, arguments: Array<out Any?>?, throwable: Throwable?) {
        val text = MessageFormatter.basicArrayFormat(messagePattern, arguments) ?: ""
        val mapped = when (level) {
            Level.ERROR -> LogLevel.ERROR
            Level.WARN -> LogLevel.WARN
            else -> LogLevel.INFO
        }
        EngineLogSink.publish(mapped, shortName, text, throwable)
    }
}

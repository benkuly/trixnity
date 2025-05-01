package net.folivo.trixnity.test.utils

import io.github.oshai.kotlinlogging.*

internal expect val runningUnderKarma: Boolean

private val defaultAppender = KotlinLoggingConfiguration.appender

private class FilteringAppender(
    val defaultLogLevel: Level,
    val packageLogLevels: List<Map.Entry<String, Level>>,
) : Appender {
    override fun log(loggingEvent: KLoggingEvent) {
        val loggerName = loggingEvent.loggerName

        val entry = packageLogLevels.firstOrNull { loggerName.startsWith(it.key) }
        val level = entry?.value ?: defaultLogLevel

        if (loggingEvent.level.ordinal >= level.ordinal) {
            val event = if (runningUnderKarma) {
                loggingEvent.copy(message = loggingEvent.message?.let { it + "\n" })
            } else {
                loggingEvent
            }

            defaultAppender.log(event)
        }
    }
}

internal actual fun setupTestLogging(
    defaultLogLevel: Level,
    packageLogLevels: Map<String, Level>,
) {
    // pass through all logs to our filter
    KotlinLoggingConfiguration.logLevel = Level.TRACE
    KotlinLoggingConfiguration.appender = FilteringAppender(
        defaultLogLevel = defaultLogLevel,
        packageLogLevels = packageLogLevels.entries.sortedByDescending { it.key.length },
    )
}
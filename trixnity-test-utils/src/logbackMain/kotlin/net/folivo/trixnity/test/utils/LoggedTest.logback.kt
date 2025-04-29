package net.folivo.trixnity.test.utils

import ch.qos.logback.classic.Level as LogbackLevel
import io.github.oshai.kotlinlogging.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

private fun Level.toLogbackLevel(): LogbackLevel = when(this) {
    Level.INFO -> LogbackLevel.INFO
    Level.DEBUG -> LogbackLevel.DEBUG
    Level.TRACE -> LogbackLevel.TRACE
    Level.OFF -> LogbackLevel.OFF
    Level.WARN -> LogbackLevel.WARN
    Level.ERROR -> LogbackLevel.ERROR
}

internal fun setupLogback(
    defaultLogLevel: Level,
    packageLogLevels: Map<String, Level>,
) {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext

    val rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME)
    rootLogger.level = defaultLogLevel.toLogbackLevel()

    val logLevels = packageLogLevels.entries.sortedBy { it.key.length }

    for ((packageName, level) in logLevels) {
        val logger = context.getLogger(packageName)
        logger.level = level.toLogbackLevel()
    }
}
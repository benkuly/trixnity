package de.connect2x.trixnity.test.utils

import de.connect2x.lognity.api.appender.Filter

import de.connect2x.lognity.api.config.ConfigBuilder
import de.connect2x.lognity.api.format.Formatter
import de.connect2x.lognity.appender.ConsoleAppender
import de.connect2x.lognity.config.systemLogAppender

private fun isUnitTest(): Boolean {
    try {
        Class.forName("androidx.test.runner.AndroidJUnitRunner")
        return false
    } catch (e: ClassNotFoundException) {
        return true
    }
}

internal actual fun ConfigBuilder.configure(pattern: String) {
    if (isUnitTest()) {
        appender(
            ConsoleAppender(
                pattern = pattern,
                formatter = Formatter.default,
                filter = Filter.always,
            )
        )
    } else {
        systemLogAppender(pattern)
    }
}

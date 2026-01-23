package de.connect2x.trixnity.test.utils

import de.connect2x.lognity.api.appender.Filter
import de.connect2x.lognity.api.config.ConfigBuilder
import de.connect2x.lognity.config.consoleAppender

internal actual fun ConfigBuilder.configure(pattern: String) {
    consoleAppender(pattern = pattern)
}
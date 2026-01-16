package de.connect2x.trixnity.test.utils

import io.github.oshai.kotlinlogging.Level

internal actual fun setupTestLogging(
    defaultLogLevel: Level,
    packageLogLevels: Map<String, Level>,
) {
    // NO-OP as it is configured by underlying logger
}
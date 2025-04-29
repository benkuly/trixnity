package net.folivo.trixnity.test.utils

import io.github.oshai.kotlinlogging.Level

internal actual fun setupTestLogging(
    defaultLogLevel: Level,
    packageLogLevels: Map<String, Level>,
) = setupLogback(defaultLogLevel, packageLogLevels)

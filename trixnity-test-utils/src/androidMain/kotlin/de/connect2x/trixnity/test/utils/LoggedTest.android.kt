package de.connect2x.trixnity.test.utils

import io.github.oshai.kotlinlogging.Level

private fun isInstrumentationClassPresent(): Boolean {
    try {
        Class.forName("androidx.test.runner.AndroidJUnitRunner")
        return true
    } catch (e: ClassNotFoundException) {
        return false
    }
}

internal actual fun setupTestLogging(
    defaultLogLevel: Level,
    packageLogLevels: Map<String, Level>,
) {
    if (isInstrumentationClassPresent()) {
        System.setProperty("kotlin-logging-to-android-native", "true")
    } else {
        setupLogback(defaultLogLevel, packageLogLevels)
    }
}
package de.connect2x.trixnity.test.utils

import io.github.oshai.kotlinlogging.Level

interface TrixnityLoggedTest : LoggedTest {

    val trixnityLogLevel: Level
        get() = Level.TRACE

    override val packageLogLevels: Map<String, Level>
        get() = mapOf("de.connect2x.trixnity" to trixnityLogLevel)
}
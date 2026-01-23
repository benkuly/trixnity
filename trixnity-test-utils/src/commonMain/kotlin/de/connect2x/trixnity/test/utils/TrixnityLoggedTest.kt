package de.connect2x.trixnity.test.utils

import de.connect2x.lognity.api.logger.Level

interface TrixnityLoggedTest : LoggedTest {

    val trixnityLogLevel: Level
        get() = Level.TRACE

    override val packageLogLevels: Map<String, Level>
        get() = mapOf("de.connect2x.trixnity" to trixnityLogLevel)
}
package net.folivo.trixnity.test.utils

import io.github.oshai.kotlinlogging.Level

interface TrixnityLoggedTest : LoggedTest {

    val trixnityLogLevel: Level
        get() = Level.TRACE

    override val packageLogLevels: Map<String, Level>
        get() = mapOf("net.folivo.trixnity" to trixnityLogLevel)
}
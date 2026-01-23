package de.connect2x.trixnity.test.utils

import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.api.config.ConfigBuilder
import de.connect2x.lognity.api.logger.Level
import de.connect2x.lognity.backend.DefaultBackend
import kotlin.test.BeforeTest

/**
 * The precedence works like the following:
 *
 * # On an android device and all apple targets
 *
 * These options are pointless as all filtering happens on the OS level, this is a limitation of KotlinLogging.
 * For tests these are normally configured to not filter anything and are equipped with solid filtering like LogCat.
 *
 * # On JVM and Android Unit tests
 *
 * Fallback is not used as Loggers have proper names.
 * Logback is instructed to use the defaultLogLevel as base and configure each logger in packageLogLevels with the specified Level.
 *
 * # On Linux, Windows, Js (and Wasm in the future)
 *
 * A custom filter is installed which filters all logs according to packagelogLevels, e.g.
 *  - 'de.connect2x' would filter all loggers which start with 'de.connect2x'
 */
interface LoggedTest {

    val defaultLogLevel: Level
        get() = Level.DEBUG

    val packageLogLevels: Map<String, Level>
        get() = emptyMap()

    @BeforeTest
    fun setupLogged() {
        // TODO packageLogLevels
        Backend.set(DefaultBackend)
        Backend.configSpec = {
            level = defaultLogLevel
            configure(pattern = "{{levelColor}}>>  {{levelSymbol}}\t{{hh}}:{{mm}}:{{ss}}.{{SSS}} ({{name}} @ {{threadId}}) {{message}}{{r}}",)
        }
    }
}

internal expect fun ConfigBuilder.configure(pattern: String)
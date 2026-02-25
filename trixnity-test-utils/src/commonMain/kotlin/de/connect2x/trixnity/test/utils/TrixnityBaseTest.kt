package de.connect2x.trixnity.test.utils

import de.connect2x.lognity.api.appender.Filter
import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.config.consoleAppender
import de.connect2x.lognity.test.TestBackend

open class TrixnityBaseTest : CoroutineTest {
    init {
        Backend.setOnce(TestBackend)
        Backend.configSpec = {
            consoleAppender(
                "{{levelColor}}>> {{levelSymbol}} {{hh}}:{{mm}}:{{ss}}.{{SSS}} [{{threadId}}/{{coroutineName}}][{{name}}] {{message}}{{r}}",
                filter = Filter { logger, message, marker ->
                    logger.context.get(Logger.Name)?.name?.startsWith("de.connect2x.trixnity") ?: false
                }
            )
        }
    }

    override val testScope = CoroutineTestScope()
}
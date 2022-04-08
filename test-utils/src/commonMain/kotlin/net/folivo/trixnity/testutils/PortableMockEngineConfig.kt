package net.folivo.trixnity.testutils

import io.ktor.client.engine.mock.*

class PortableMockEngineConfig {
    lateinit var config: MockEngineConfig
    fun endpoints(block: MockEngineConfig.() -> Unit) =
        config.apply {
            requestHandlers.removeLast()
            block()
            defaultMockEngineHandler()
        }
}

fun MockEngineConfig.configurePortableMockEngine(portableMockEngineConfig: PortableMockEngineConfig) {
    portableMockEngineConfig.config = this
}
package net.folivo.trixnity.testutils

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.mock.*

private val log = KotlinLogging.logger {}

fun mockEngineFactory(
    withDefaultResponse: Boolean = true,
    config: MockEngineConfig.() -> Unit = {}
): (HttpClientConfig<*>.() -> Unit) -> HttpClient = {
    HttpClient(MockEngine) {
        it()
        engine {
            config()
            if (withDefaultResponse)
                defaultMockEngineHandler()
        }
    }
}

internal fun MockEngineConfig.defaultMockEngineHandler() {
    addHandler { request ->
        log.error { "request $request not defined in mock engine config. Configured are: ${requestHandlers.map { it::class.toString() }}" }
        throw IllegalStateException("request $request not defined in mock engine config. Configured are: ${requestHandlers.map { it::class.toString() }}")
    }
}
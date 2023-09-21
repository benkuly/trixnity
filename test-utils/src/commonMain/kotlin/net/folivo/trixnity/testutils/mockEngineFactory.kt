package net.folivo.trixnity.testutils

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

private val log = KotlinLogging.logger {}

fun mockEngineFactory(
    withDefaultResponse: Boolean = true,
    configure: MockEngineConfig.() -> Unit = {}
): (HttpClientConfig<*>.() -> Unit) -> HttpClient = {
    HttpClient(MockEngine) {
        it()
        engine {
            configure()
            if (withDefaultResponse)
                defaultMockEngineHandler()
        }
    }
}

data class MockEngineEndpointsConfig(
    val json: Json,
    val contentMappings: EventContentSerializerMappings,
) {
    val requestHandlers = mutableListOf<suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?>()

    fun addHandler(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?) {
        requestHandlers.add(handler)
    }
}

class PortableMockEngineConfig {
    lateinit var config: MockEngineEndpointsConfig
    fun endpoints(block: MockEngineEndpointsConfig.() -> Unit) =
        config.apply {
            requestHandlers.clear()
            block()
        }
}

fun mockEngineFactoryWithEndpoints(
    json: Json,
    contentMappings: EventContentSerializerMappings,
    portableConfig: PortableMockEngineConfig? = null,
    configure: MockEngineEndpointsConfig.() -> Unit = {}
): (HttpClientConfig<*>.() -> Unit) -> HttpClient = mockEngineFactory(false) {
    val config = MockEngineEndpointsConfig(json, contentMappings).apply { configure() }
    portableConfig?.config = config
    addHandler { request ->
        config.requestHandlers.firstNotNullOfOrNull { it(this, request) }
            ?: run {
                log.error { "request $request not defined in mock engine config. Configured are: ${requestHandlers.map { it::class.toString() }}" }
                throw IllegalStateException("request $request not defined in mock engine config. Configured are: ${requestHandlers.map { it::class.toString() }}")
            }
    }
}

internal fun MockEngineConfig.defaultMockEngineHandler() {
    addHandler { request ->
        log.error { "request $request not defined in mock engine config. Configured are: ${requestHandlers.map { it::class.toString() }}" }
        throw IllegalStateException("request $request not defined in mock engine config. Configured are: ${requestHandlers.map { it::class.toString() }}")
    }
}
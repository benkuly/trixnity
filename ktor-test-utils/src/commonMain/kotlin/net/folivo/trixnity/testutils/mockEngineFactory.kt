package net.folivo.trixnity.testutils

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.coroutines.ContinuationInterceptor

private val log = KotlinLogging.logger("net.folivo.trixnity.testutils.MockEngineFactory")

fun CoroutineScope.scopedMockEngine(
    withDefaultResponse: Boolean = true,
    configure: MockEngineConfig.() -> Unit = {}
): HttpClientEngine =
    MockEngine.create {
        dispatcher = coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher
        configure()
        if (withDefaultResponse)
            defaultMockEngineHandler()
    }.also { engine -> coroutineContext.job.invokeOnCompletion { engine.close() } }

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
    fun endpoints(replace: Boolean = false, block: MockEngineEndpointsConfig.() -> Unit) =
        config.apply {
            if (replace) requestHandlers.clear()
            block()
        }
}

fun mockEngineWithEndpoints(
    json: Json,
    contentMappings: EventContentSerializerMappings,
    portableConfig: PortableMockEngineConfig? = null,
    configure: MockEngineEndpointsConfig.() -> Unit = {}
): HttpClientEngine = MockEngine.create {
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

fun CoroutineScope.scopedMockEngineWithEndpoints(
    json: Json,
    contentMappings: EventContentSerializerMappings,
    portableConfig: PortableMockEngineConfig? = null,
    configure: MockEngineEndpointsConfig.() -> Unit = {}
): HttpClientEngine = scopedMockEngine(false) {
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
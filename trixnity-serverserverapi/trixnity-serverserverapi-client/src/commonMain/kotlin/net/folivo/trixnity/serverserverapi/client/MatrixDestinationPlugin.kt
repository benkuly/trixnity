package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.util.*

class MatrixDestinationPlugin(
    private val getDelegatedDestination: (String, Int) -> Pair<String, Int>,
) : HttpClientPlugin<Unit, Unit> {
    override val key: AttributeKey<Unit> = AttributeKey("MatrixDestinationPlugin")

    override fun prepare(block: Unit.() -> Unit) {
    }

    override fun install(plugin: Unit, scope: HttpClient) {
        scope.requestPipeline.intercept(HttpRequestPipeline.State) {
            val (delegatedHost, delegatedPort) = getDelegatedDestination(context.host, context.port)
            context.host = delegatedHost
            context.port = delegatedPort
        }
    }
}
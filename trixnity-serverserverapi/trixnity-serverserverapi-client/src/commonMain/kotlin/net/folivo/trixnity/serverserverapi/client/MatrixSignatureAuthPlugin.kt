package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.serverserverapi.model.requestAuthenticationBody

class MatrixSignatureAuthPlugin(
    private val hostname: String,
    private val sign: (String) -> Key.Ed25519Key,
    private val json: Json,
) : HttpClientPlugin<Unit, Unit> {
    override val key: AttributeKey<Unit> = AttributeKey("MatrixSignatureAuthPlugin")

    override fun prepare(block: Unit.() -> Unit) {
    }

    override fun install(plugin: Unit, scope: HttpClient) {
        scope.requestPipeline.intercept(HttpRequestPipeline.Render) { body ->
            val content =
                when (body) {
                    is EmptyContent -> null
                    is TextContent -> if (body.contentType == ContentType.Application.Json) body.text else null
                    else -> return@intercept
                }
            val destination = "${context.host}:${context.port}"
            val signature = sign(
                requestAuthenticationBody(
                    content = content,
                    method = context.method.value,
                    uri = context.url.encodedPath,
                    origin = hostname,
                    destination = destination,
                )
            )
            context.header(
                HttpHeaders.Authorization,
                """X-Matrix origin="$hostname",destination="$destination",key="${signature.algorithm.name}:${signature.id}",sig="${signature.value.value}""""
            )
        }
    }
}
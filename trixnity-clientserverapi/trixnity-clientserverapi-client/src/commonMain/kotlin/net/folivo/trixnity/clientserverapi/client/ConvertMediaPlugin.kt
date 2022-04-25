package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import net.folivo.trixnity.clientserverapi.model.media.Media

object ConvertMediaPlugin : HttpClientPlugin<Unit, Unit> {
    override val key: AttributeKey<Unit> = AttributeKey("ConvertMediaPlugin")

    override fun prepare(block: Unit.() -> Unit) {
    }

    override fun install(plugin: Unit, scope: HttpClient) {
        scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { body ->
            if (body !is Media) return@intercept
            proceedWith(
                object : OutgoingContent.ReadChannelContent() {
                    override fun readFrom() = body.content
                    override val contentType = body.contentType
                    override val contentLength = body.contentLength
                    override val headers =
                        body.filename?.let { it1 -> headersOf(HttpHeaders.ContentDisposition, it1) } ?: headersOf()
                })
        }
        scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
            if (body !is ByteReadChannel || info.type != Media::class) return@intercept

            proceedWith(
                HttpResponseContainer(
                    info,
                    Media(
                        content = body,
                        contentType = context.response.contentType(),
                        contentLength = context.response.contentLength(),
                        filename = context.response.headers[HttpHeaders.ContentDisposition]
                    )
                )
            )
        }
    }
}
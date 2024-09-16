package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.plugins.api.*
import io.ktor.http.*
import io.ktor.http.content.*
import net.folivo.trixnity.clientserverapi.model.media.Media

val ConvertMediaPlugin = createClientPlugin("ConvertMediaPlugin") {
    transformRequestBody { _, body, _ ->
        if (body !is Media) return@transformRequestBody null
        object : OutgoingContent.ReadChannelContent() {
            override fun readFrom() = body.content
            override val contentType = body.contentType
            override val contentLength = body.contentLength
            override val headers =
                body.contentDisposition
                    ?.let { headersOf(HttpHeaders.ContentDisposition, it.toString()) }
                    ?: headersOf()
        }
    }
    transformResponseBody { response, body, requestedType ->
        if (requestedType.type != Media::class) return@transformResponseBody null
        Media(
            content = body,
            contentType = response.contentType(),
            contentLength = response.contentLength(),
            contentDisposition = response.headers[HttpHeaders.ContentDisposition]?.let(
                ContentDisposition::parse
            )
        )
    }
}
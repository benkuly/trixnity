package de.connect2x.trixnity.clientserverapi.server

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import de.connect2x.trixnity.clientserverapi.model.media.Media

val ConvertMediaPlugin = createRouteScopedPlugin("ConvertMediaPlugin") {
    onCallReceive { call ->
        if (call.receiveType.type != Media::class) return@onCallReceive

        transformBody { body ->
            Media(
                content = body,
                contentLength = call.request.contentLength(),
                contentType = call.request.contentType(),
                contentDisposition = call.request.header(HttpHeaders.ContentDisposition)?.let(ContentDisposition::parse)
            )
        }
    }
    onCallRespond { _, body ->
        if (body !is Media) return@onCallRespond
        transformBody {
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
    }
}
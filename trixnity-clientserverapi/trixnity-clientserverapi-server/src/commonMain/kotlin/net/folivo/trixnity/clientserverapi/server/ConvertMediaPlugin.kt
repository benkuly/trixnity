package net.folivo.trixnity.clientserverapi.server

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import net.folivo.trixnity.clientserverapi.model.media.Media

val ConvertMediaPlugin = createRouteScopedPlugin("ConvertMediaPlugin") {
    onCallReceive { call ->
        if (call.receiveType.type != Media::class) return@onCallReceive

        transformBody { body ->
            Media(
                content = body,
                contentLength = call.request.contentLength(),
                contentType = call.request.contentType(),
                filename = call.request.header(HttpHeaders.ContentDisposition)
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
                    body.filename?.let { it1 -> headersOf(HttpHeaders.ContentDisposition, it1) } ?: headersOf()
            }
        }
    }
}
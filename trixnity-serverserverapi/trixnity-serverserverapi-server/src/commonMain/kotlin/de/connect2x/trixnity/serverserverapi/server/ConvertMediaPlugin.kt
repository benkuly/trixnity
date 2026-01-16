package de.connect2x.trixnity.serverserverapi.server

import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import de.connect2x.trixnity.serverserverapi.model.federation.Media
import de.connect2x.trixnity.utils.nextString
import kotlin.random.Random

val ConvertMediaPlugin = createRouteScopedPlugin("ConvertMediaPlugin") {
    onCallRespond { _, body ->
        if (body !is Media) return@onCallRespond
        transformBody {
            val boundary = Random.nextString(70).replace("-", "_")
            MultiPartFormDataContent(
                parts = listOf(
                    PartData.BinaryChannelItem(
                        { ByteReadChannel("{}".toByteArray()) },
                        Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        }
                    ),
                    when (body) {
                        is Media.Stream -> PartData.BinaryChannelItem(
                            { body.content },
                            Headers.build {
                                body.contentLength?.let { append(HttpHeaders.ContentLength, it.toString()) }
                                body.contentType?.let { append(HttpHeaders.ContentType, it.toString()) }
                                body.contentDisposition?.let { append(HttpHeaders.ContentDisposition, it) }
                            }
                        )

                        is Media.Redirect -> PartData.BinaryChannelItem(
                            { ByteReadChannel("".toByteArray()) },
                            Headers.build {
                                append(HttpHeaders.Location, body.location)
                            }
                        )
                    },
                ),
                boundary = boundary,
                contentType = ContentType.MultiPart.Mixed.withParameter("boundary", boundary)
            )
        }
    }
}
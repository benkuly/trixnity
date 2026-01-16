package de.connect2x.trixnity.serverserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.api.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import de.connect2x.trixnity.serverserverapi.model.federation.Media

private val log = KotlinLogging.logger("de.connect2x.trixnity.serverserverapi.client.ConvertMediaPlugin")

val ConvertMediaPlugin = createClientPlugin("ConvertMediaPlugin") {
    transformResponseBody { response, body, requestedType ->
        if (requestedType.type != Media::class) return@transformResponseBody null

        val rawContentType =
            checkNotNull(response.headers[HttpHeaders.ContentType]) { "No content type provided for multipart" }
        val contentType = ContentType.parse(rawContentType)
        check(contentType.match(ContentType.MultiPart.Mixed)) { "Expected multipart/mixed, got $contentType" }

        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLong()
        val multipartBody =
            CoroutineScope(response.coroutineContext).parseMultipart(body, rawContentType, contentLength)

        when (val part1 = multipartBody.receive()) {// right now is empty and therefore ignored
            is MultipartEvent.MultipartPart -> {
                val bodyJson = part1.body.readRemaining().use { it.readText() }
                if (bodyJson != "{}") log.warn { "part1 of media should be empty json but was '$bodyJson'" }
                part1.release()
            }

            else -> {
                part1.release()
            }
        }
        when (val part2 = multipartBody.receive()) {
            is MultipartEvent.MultipartPart -> {
                val headers = CIOHeaders(part2.headers.await())
                val locationHeader = headers[HttpHeaders.Location]

                when {
                    locationHeader != null -> Media.Redirect(locationHeader)
                    else -> Media.Stream(
                        content = part2.body,
                        contentLength = headers[HttpHeaders.ContentLength]?.toLong(),
                        contentType = headers[HttpHeaders.ContentType]
                            ?.let { ContentType.parse(it) },
                        contentDisposition = headers[HttpHeaders.ContentDisposition]
                            ?.let { ContentDisposition.parse(it) },
                    )
                }
            }

            else -> {
                part2.release()
            }
        }
    }
}
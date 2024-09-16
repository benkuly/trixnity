package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.plugins.api.*

val ConvertMediaPlugin = createClientPlugin("ConvertMediaPlugin") {
    transformResponseBody { _, body, requestedType ->
        throw NotImplementedError("parsing multipart/mixed into Media is not implemented yet")
//        if (body !is MultiPartData || requestedType.type != Media::class) return@transformResponseBody null
//
//        val part1 = body.readPart() // right now is empty and therefore ignored
//        checkNotNull(part1 != null) { "part 1 of media missing" }
//        check(part1 is PartData.BinaryChannelItem) { "part 1 was not a PartData.BinaryChannelItem" }
//        val part2 = body.readPart()
//        checkNotNull(part2 != null) { "part 2 of media missing" }
//        check(part2 is PartData.BinaryChannelItem) { "part 2 was not a PartData.BinaryChannelItem" }
//
//        val locationHeader = part2.headers[HttpHeaders.Location]
//
//        when {
//            locationHeader != null -> Media.Redirect(locationHeader)
//            else -> Media.Stream(
//                content = part2.provider(),
//                contentLength = part2.headers[HttpHeaders.ContentLength]?.toLong(),
//                contentType = part2.contentType,
//                contentDisposition = part2.contentDisposition,
//            )
//        }
    }
}
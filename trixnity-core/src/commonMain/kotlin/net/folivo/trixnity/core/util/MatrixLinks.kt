package net.folivo.trixnity.core.util

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

private val log = KotlinLogging.logger {}

object MatrixLinks {
    private val matrixProtocol = URLProtocol("matrix", 0)

    fun parse(href: String): Reference? {
        val url = Url(href)
        if (url.protocol == matrixProtocol) {
            return parseMatrixProtocol(url.segments, url.parameters)
        }
        // matrix.to URLs look like this:
        // https://matrix.to/#/!roomId?via=example.org
        // protocol=https host=matrix.to segments=[] fragment=/!roomId?via=example.org
        if (url.protocol == URLProtocol.HTTPS && url.host == "matrix.to" && url.segments.isEmpty()) {
            // matrix.to uses AJAX hash routing, where the entire path is passed within the hash fragment to prevent
            // the server from seeing the roomId.
            // This means we have to parse this hash back into path segments and query parameters
            val path = url.fragment.substringBefore('?').removePrefix("/")
            val query = url.fragment.substringAfter('?', missingDelimiterValue = "")
            val segments = path.removePrefix("/").split('/')
            val parameters = parseQueryString(query, decode = false)
            return parseMatrixTo(segments, parameters)
        }
        return null
    }

    private fun parseMatrixTo(path: List<String>, parameters: Parameters): Reference? {
        val parts = path.map { id ->
            when {
                id.length > 255 -> {
                    log.trace { "malformed matrix link: id too long: ${id.length} (max length: 255)" }
                    return null
                }
                id.startsWith(RoomAliasId.sigilCharacter) -> RoomAliasId(id)
                id.startsWith(RoomId.sigilCharacter) -> RoomId(id)
                id.startsWith(UserId.sigilCharacter) -> UserId(id)
                id.startsWith(EventId.sigilCharacter) -> EventId(id)
                else -> {
                    log.trace { "malformed matrix link: invalid id type: ${id.firstOrNull()} (known types: #, !, @, $)" }
                    null
                }
            }
        }
        val first = parts.getOrNull(0)
        val second = parts.getOrNull(1)
        return when {
            first is UserId -> Reference.User(first, parameters)
            first is RoomAliasId -> Reference.RoomAlias(first, parameters)
            first is EventId -> Reference.Event(null, first, parameters)
            first is RoomId && second is EventId -> Reference.Event(first, second, parameters)
            first is RoomId -> Reference.Room(first, parameters)
            else -> {
                log.trace { "malformed matrix link: unknown format" }
                null
            }
        }
    }

    private fun parseMatrixProtocol(path: List<String>, parameters: Parameters): Reference? {
        val parts = path.windowed(2, 2).map { (type, id) ->
            when {
                id.length > 255 -> {
                    log.trace { "malformed matrix link: id too long: ${id.length} (max length: 255)" }
                    return null
                }
                type == "roomid" -> RoomId("!$id")
                type == "r" -> RoomAliasId("#$id")
                type == "u" -> UserId("@$id")
                type == "e" -> EventId("$$id")
                else -> {
                    log.trace { "malformed matrix link: invalid id type: $type (known types: roomid, r, u, e)" }
                    null
                }
            }
        }
        val first = parts.getOrNull(0)
        val second = parts.getOrNull(1)
        return when {
            first is UserId -> Reference.User(first, parameters)
            first is RoomAliasId -> Reference.RoomAlias(first, parameters)
            first is RoomId && second is EventId -> Reference.Event(first, second, parameters)
            first is RoomId -> Reference.Room(first, parameters)
            else -> {
                log.trace { "malformed matrix link: unknown format" }
                null
            }
        }
    }
}
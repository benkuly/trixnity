package net.folivo.trixnity.core.util

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.Mention
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

private val log = KotlinLogging.logger {}

object MatrixLinks {
    private val matrixProtocol = URLProtocol("matrix", 0)

    fun parse(href: String): Mention? {
        val url = Url(href)
        if (url.protocol == matrixProtocol) {
            return parseMatrixProtocol(url.segments, url.parameters)
        }
        if (url.protocol == URLProtocol.HTTPS && url.host == "matrix.to" && url.segments.isEmpty()) {
            val path = url.fragment.substringBefore('?').removePrefix("/")
            val query = url.fragment.substringAfter('?', missingDelimiterValue = "")
            val segments = path.removePrefix("/").split('/')
            val parameters = parseQueryString(query, decode = false)
            return parseMatrixTo(segments, parameters)
        }
        return null
    }

    private fun parseMatrixTo(path: List<String>, parameters: Parameters): Mention? {
        val parts = path.map { id ->
            when {
                id.length > 255 -> {
                    log.trace { "malformed matrix link: id too long: ${id.length} (max length: 255)" }
                    null
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
            first is UserId -> Mention.User(first, parameters)
            first is RoomAliasId -> Mention.RoomAlias(first, parameters)
            first is EventId -> Mention.Event(null, first, parameters)
            first is RoomId && second is EventId -> Mention.Event(first, second, parameters)
            first is RoomId -> Mention.Room(first, parameters)
            else -> {
                log.trace { "malformed matrix link: unknown format" }
                null
            }
        }
    }

    private fun parseMatrixProtocol(path: List<String>, parameters: Parameters): Mention? {
        val parts = path.windowed(2, 2).map { (type, id) ->
            when {
                id.length > 255 -> {
                    log.trace { "malformed matrix link: id too long: ${id.length} (max length: 255)" }
                    null
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
            first is UserId -> Mention.User(first, parameters)
            first is RoomAliasId -> Mention.RoomAlias(first, parameters)
            first is RoomId && second is EventId -> Mention.Event(first, second, parameters)
            first is RoomId -> Mention.Room(first, parameters)
            else -> {
                log.trace { "malformed matrix link: unknown format" }
                null
            }
        }
    }
}
package net.folivo.trixnity.core.util

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Parameters
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.parametersOf
import io.ktor.http.parseQueryString
import io.ktor.http.parseUrl
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

private val log = KotlinLogging.logger {}

/**
 * Represents a mention. A mention can refer to various entities and potentially include actions associated with them.
 */
sealed interface Reference {
    /**
     * Represents a mention of a user.
     */
    data class User(
        val userId: UserId,
        val parameters: Parameters = parametersOf()
    ) : Reference

    /**
     * Represents a mention of a room.
     */
    data class Room(
        val roomId: RoomId,
        val parameters: Parameters = parametersOf()
    ) : Reference

    /**
     * Represents a mention of a room alias
     */
    data class RoomAlias(
        val roomAliasId: RoomAliasId,
        val parameters: Parameters = parametersOf()
    ) : Reference

    /**
     * Represents a mention of a generic event.
     */
    data class Event(
        val roomId: RoomId? = null,
        val eventId: EventId,
        val parameters: Parameters = parametersOf()
    ) : Reference

    data class Link(
        val url: String
    ) : Reference

    companion object {
        fun findReferences(message: String): Map<IntRange, Reference> {
            val candidates = findLinkReferences(message).plus(findIdReferences(message))
            return buildMap {
                for ((candidateKey, candidateValue) in candidates) {
                    val overlapKey = keys.find { candidateKey.overlaps(it) }
                    if (overlapKey == null) {
                        put(candidateKey, candidateValue)
                    } else if (overlapKey.last - overlapKey.first < candidateKey.last - candidateKey.first) {
                        remove(overlapKey)
                        put(candidateKey, candidateValue)
                    }
                }
            }
        }

        private fun findIdReferences(content: String): Map<IntRange, Reference> {
            return MatrixIdRegex.autolinkId.findAll(content).mapNotNull {
                Pair(it.range, parseMatrixId(it.value) ?: return@mapNotNull null)
            }.toMap()
        }

        private fun findLinkReferences(content: String): Map<IntRange, Reference> {
            return Patterns.AUTOLINK_MATRIX_URI.findAll(content).associate {
                val trimmedContent = it.value.trimLink()
                Pair(
                    it.range.first.until(it.range.first + trimmedContent.length),
                    parseLink(trimmedContent) ?: Link(trimmedContent)
                )
            }
        }

        private fun parseMatrixId(id: String): Reference? {
            return when {
                id.length > 255 -> {
                    log.trace { "malformed matrix id: id too long: ${id.length} (max length: 255)" }
                    null
                }
                id.startsWith(UserId.sigilCharacter) -> User(UserId(id))
                id.startsWith(RoomAliasId.sigilCharacter) -> RoomAlias(RoomAliasId(id))
                else -> null
            }
        }

        private fun parseUri(href: String): Url? = try {
            Url(href)
        } catch (_: URLParserException) {
            null
        }

        fun parseLink(href: String): Reference? {
            val url = parseUri(href) ?: return null
            if (url.protocol == matrixProtocol) {
                return parseMatrixProtocolLink(url.segments, url.parameters)
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
                return parseMatrixToLink(segments, parameters)
            }
            return null
        }

        private fun parseMatrixToLink(path: List<String>, parameters: Parameters): Reference? {
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
                first is UserId -> User(first, parameters)
                first is RoomAliasId -> RoomAlias(first, parameters)
                first is EventId -> Event(null, first, parameters)
                first is RoomId && second is EventId -> Event(first, second, parameters)
                first is RoomId -> Room(first, parameters)
                else -> {
                    log.trace { "malformed matrix link: unknown format" }
                    null
                }
            }
        }

        private fun parseMatrixProtocolLink(path: List<String>, parameters: Parameters): Reference? {
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
                first is UserId -> User(first, parameters)
                first is RoomAliasId -> RoomAlias(first, parameters)
                first is RoomId && second is EventId -> Event(first, second, parameters)
                first is RoomId -> Room(first, parameters)
                else -> {
                    log.trace { "malformed matrix link: unknown format" }
                    null
                }
            }
        }

        private fun String.trimParens(): String =
            if (endsWith(')')) {
                val trimmed = trimEnd(')')
                val openingParens = trimmed.count { it == '(' }
                val closingParens = trimmed.count { it == ')' }
                val endingParens = length - trimmed.length
                val openParens = openingParens - closingParens

                val desiredParens = minOf(endingParens, openParens)
                take(trimmed.length + desiredParens)
            } else this

        private fun String.trimLink(): String =
            trimEnd(',', '.', '!', '?', ':').trimParens()

        private fun IntRange.overlaps(other: IntRange): Boolean {
            return this.first <= other.last && other.first <= this.last
        }

        private val matrixProtocol = URLProtocol("matrix", 0)
    }
}
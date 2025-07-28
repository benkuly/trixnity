package net.folivo.trixnity.core.util

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

private val log = KotlinLogging.logger("net.folivo.trixnity.core.util.Mentions")

/**
 * Represents a mention. A mention can refer to various entities and potentially include actions associated with them.
 */
sealed interface Mention {
    /**
     * If exists, the parameters provided in the URI
     */
    val parameters: Parameters?


    /**
     * Represents a mention of a user.
     */
    data class User(
        val userId: UserId,
        override val parameters: Parameters? = parametersOf()
    ) : Mention

    /**
     * Represents a mention of a room.
     */
    data class Room(
        val roomId: RoomId,
        override val parameters: Parameters? = parametersOf()
    ) : Mention

    /**
     * Represents a mention of a room alias
     */
    data class RoomAlias(
        val roomAliasId: RoomAliasId,
        override val parameters: Parameters? = parametersOf()
    ) : Mention

    /**
     * Represents a mention of a generic event.
     */
    data class Event(
        val roomId: RoomId? = null,
        val eventId: EventId,
        override val parameters: Parameters? = parametersOf()
    ) : Mention
}

object Mentions {
    private val matrixProtocol = URLProtocol("matrix", 0)

    fun findMentions(message: String): Map<IntRange, Mention> {
        val links = findLinkMentions(message)
        val users = findIdMentions(message)
        val linksRange = links.keys.sortedBy { it.first }
        val uniqueUsers = users.filter { (user, _) ->
            // We don't want id matches that overlap with link matches,
            // as matrix.to urls will match both as link and as id
            !linksRange.overlaps(user)
        }
        return links.plus(uniqueUsers).toMap()
    }

    fun findIdMentions(content: String, from: Int = 0, to: Int = content.length): Map<IntRange, Mention> {
        return matrixIdRegex
            .findAll(content, startIndex = from)
            .filter { it.range.last < to }
            .filter { it.range.last - it.range.first <= 255 }
            .mapNotNull { Pair(it.range, parseMatrixId(it.value) ?: return@mapNotNull null) }
            .toMap()
    }

    fun findLinkMentions(content: String, from: Int = 0, to: Int = content.length): Map<IntRange, Mention> {
        return Patterns.AUTOLINK_MATRIX_URI
            .findAll(content, startIndex = from)
            .filter { it.range.last < to }
            .mapNotNull {
                val trimmedContent = it.value.trimLink()
                Pair(
                    it.range.first.until(it.range.first + trimmedContent.length),
                    parseLink(trimmedContent) ?: return@mapNotNull null
                )
            }.toMap()
    }

    fun findLinks(content: String, from: Int = 0, to: Int = content.length): Map<IntRange, String> {
        return Patterns.AUTOLINK_MATRIX_URI
            .findAll(content, startIndex = from)
            .filter { it.range.last < to }
            .map {
                val trimmedContent = it.value.trimLink()
                Pair(
                    it.range.first.until(it.range.first + trimmedContent.length),
                    trimmedContent,
                )
            }.toMap()
    }

    fun parseLink(href: String): Mention? {
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

    private fun parseMatrixTo(path: List<String>, parameters: Parameters): Mention? {
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

    private fun parseMatrixId(id: String): Mention? {
        return when {
            id.length > 255 -> {
                log.trace { "malformed matrix id: id too long: ${id.length} (max length: 255)" }
                null
            }

            id.startsWith(UserId.sigilCharacter) -> Mention.User(UserId(id))
            id.startsWith(RoomAliasId.sigilCharacter) -> Mention.RoomAlias(RoomAliasId(id))
            else -> null
        }
    }

    private fun List<IntRange>.overlaps(user: IntRange): Boolean {
        val index = binarySearch { link ->
            when {
                user.last < link.first -> 1
                user.first > link.last -> -1
                user.first >= link.first && user.last <= link.last -> 0
                else -> -1
            }
        }
        return index >= 0
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
}
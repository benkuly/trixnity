package net.folivo.trixnity.core.util

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

private val log = KotlinLogging.logger("net.folivo.trixnity.core.util.References")

/**
 * Represents a reference. A reference can refer to various entities and potentially include actions associated with them.
 */
sealed interface Reference {
    /**
     * If exists, the orignal uri.
     */
    val uri: String?


    /**
     * Represents a mention of a user.
     */
    data class User(
        val userId: UserId,
        override val uri: String? = null
    ) : Reference

    /**
     * Represents a mention of a room.
     */
    data class Room(
        val roomId: RoomId,
        override val uri: String? = null
    ) : Reference

    /**
     * Represents a mention of a room alias.
     */
    data class RoomAlias(
        val roomAliasId: RoomAliasId,
        override val uri: String? = null
    ) : Reference

    /**
     * Represents a mention of a generic event.
     */
    data class Event(
        val roomId: RoomId? = null,
        val eventId: EventId,
        override val uri: String? = null
    ) : Reference

    /**
     * Represents a classic link (url).
     */
    data class Link(
        override val uri: String
    ) : Reference
}

object References {
    private val matrixProtocol = URLProtocol("matrix", 0)

    fun findReferences(message: String): Map<IntRange, Reference> {
        val linkReferences = findLinkReferences(message)
        val idReferences = findIdReferences(message)

        val idRanges = idReferences.keys.sortedBy { it.first }
        val uniqueLinkReferences = linkReferences.filter { (linkRange, linkReference) ->
            // We don't want links matches that overlap with user matches,
            // as a matrixIds will match both as link and as id
            if (linkReference is Reference.Link) !idRanges.overlaps(linkRange)
            else true
        }

        val linkRanges = linkReferences.filterValues { it !is Reference.Link }.keys.sortedBy { it.first }
        val uniqueIdReferences = idReferences.filter { (userRange, _) ->
            // We don't want id matches that overlap with link matches,
            // as matrix.to urls will match both as link and as id
            !linkRanges.overlaps(userRange)
        }
        return uniqueIdReferences + uniqueLinkReferences
    }

    fun findIdReferences(content: String): Map<IntRange, Reference> {
        return MatrixIdRegex.userIdRegex.findAll(content).toReferences() +
                MatrixIdRegex.roomAliasIdRegex.findAll(content).toReferences()
    }

    private fun Sequence<MatchResult>.toReferences() =
        filter { it.range.last - it.range.first <= 255 }
            .mapNotNull {
                Pair(
                    it.range,
                    when (val matrixId = it.value.toMatrixId()) {
                        null -> null
                        is UserId -> Reference.User(matrixId)
                        is RoomAliasId -> Reference.RoomAlias(matrixId)
                        is EventId -> Reference.Event(null, matrixId)
                        is RoomId -> Reference.Room(matrixId)
                        else -> null
                    } ?: return@mapNotNull null
                )
            }
            .toMap()

    fun findLinkReferences(content: String): Map<IntRange, Reference> {
        return Patterns.AUTOLINK_MATRIX_URI
            .findAll(content)
            .mapNotNull {
                val trimmedContent = it.value.trimLink()
                Pair(
                    it.range.first.until(it.range.first + trimmedContent.length),
                    parseLink(trimmedContent) ?: return@mapNotNull null
                )
            }.toMap()
    }

    private fun parseLink(uri: String): Reference? {
        val url = try {
            Url(uri)
        } catch (_: URLParserException) {
            null
        }
        return when {
            url == null -> Reference.Link(uri)
            url.protocol == matrixProtocol -> parseMatrixProtocol(url, uri)
            // matrix.to URLs look like this:
            // https://matrix.to/#/!roomId?via=example.org
            // protocol=https host=matrix.to segments=[] fragment=/!roomId?via=example.org
            url.protocol == URLProtocol.HTTPS && url.host == "matrix.to" && url.segments.isEmpty() -> {
                // matrix.to uses AJAX hash routing, where the entire path is passed within the hash fragment to prevent
                // the server from seeing the roomId.
                // This means we have to parse this hash back into path segments and query parameters
                val path = url.fragment.substringBefore('?').removePrefix("/")
                val segments = path.removePrefix("/").split('/')
                return parseMatrixTo(segments, uri)
            }

            else -> Reference.Link(uri)
        }
    }

    private fun String.toMatrixId() =
        when {
            startsWith(UserId.sigilCharacter) && UserId.isValid(this) -> UserId(this)
            startsWith(RoomAliasId.sigilCharacter) && RoomAliasId.isValid(this) -> RoomAliasId(this)
            startsWith(RoomId.sigilCharacter) && RoomId.isValid(this) -> RoomId(this)
            startsWith(EventId.sigilCharacter) && EventId.isValid(this) -> EventId(this)
            else -> {
                log.trace { "malformed matrix id $this invalid id type: ${firstOrNull()} (known types: #, !, @, $)" }
                null
            }
        }

    private fun parseMatrixTo(path: List<String>, uri: String): Reference? {
        val parts = path.map { it.toMatrixId() }
        val first = parts.getOrNull(0)
        val second = parts.getOrNull(1)
        return when {
            first is UserId -> Reference.User(first, uri)
            first is RoomAliasId -> Reference.RoomAlias(first, uri)
            first is EventId -> Reference.Event(null, first, uri)
            first is RoomId && second is EventId -> Reference.Event(first, second, uri)
            first is RoomId -> Reference.Room(first, uri)
            else -> {
                log.trace { "malformed matrix link $path: unknown format" }
                null
            }
        }
    }

    private fun parseMatrixProtocol(url: Url, uri: String): Reference? {
        val parts = url.segments.windowed(2, 2).map { (type, id) ->
            when (type) {
                "roomid" -> "!$id".toMatrixId()
                "r" -> "#$id".toMatrixId()
                "u" -> "@$id".toMatrixId()
                "e" -> "$$id".toMatrixId()
                else -> null
            }
        }
        val first = parts.getOrNull(0)
        val second = parts.getOrNull(1)
        return when {
            first is UserId -> Reference.User(first, uri)
            first is RoomAliasId -> Reference.RoomAlias(first, uri)
            first is RoomId && second is EventId -> Reference.Event(first, second, uri)
            first is RoomId -> Reference.Room(first, uri)
            else -> {
                log.trace { "malformed matrix link: unknown format" }
                null
            }
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
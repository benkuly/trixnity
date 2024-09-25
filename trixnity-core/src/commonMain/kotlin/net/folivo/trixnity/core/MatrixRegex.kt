package net.folivo.trixnity.core

import io.ktor.http.*
import net.folivo.trixnity.core.model.*

object MatrixRegex {
    // Decode/Encode Grammar
    private fun makeSymboleRegex(symbole: String, code: String) = "(?:(?:$symbole)|(?:$code))"
    private val exlMark = makeSymboleRegex("!", "%21")
    private val dollar = makeSymboleRegex("\\$", "%24")
    private val at = makeSymboleRegex("@", "%40")
    private val hash = makeSymboleRegex("#", "%23")
    private val colon = makeSymboleRegex(":", "%3A")
    private val qesMark = makeSymboleRegex("\\?", "%3F")
    private val eq = makeSymboleRegex("=", "%3D")
    private val amp = makeSymboleRegex("&", "%26")

    // https://spec.matrix.org/v1.11/appendices/#common-namespaced-identifier-grammar
    private val namespaceIdRegex = """(?!m\.)[a-z][a-z0-9-_.]{1,254}"""

    // https://spec.matrix.org/v1.11/appendices/#user-identifiers
    private val userLocalpartRegex = """(?:[0-9a-z-=_/+.]+)"""

    // https://github.com/matrix-org/matrix-spec-proposals/blob/human-id-rules/drafts/human-id-rules.rst
    private val roomAliasLocalpartRegex = """(?:[^:\s]+)"""

    // https://spec.matrix.org/v1.11/appendices/#opaque-identifiers
    private val opaqueIdRegex = """(?:[0-9A-Za-z-._~]+)"""

    // https://spec.matrix.org/v1.11/appendices/#server-name
    private const val basePortRegex = """:[0-9]{1,5}"""
    private const val baseDnsRegex = """(?:[\w-]+\.)+[\w-]+"""
    private const val baseIPV4Regex = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""
    private const val baseIPV6Regex = """\[[0-9a-fA-F:]+\]"""
    private const val servernameRegex =
        """(?:(?:$baseIPV4Regex)|(?:$baseDnsRegex)|(?:$baseIPV6Regex))(?:$basePortRegex)?"""


    // https://spec.matrix.org/v1.11/appendices/#user-identifiers
    private val userIdRegex = """@($userLocalpartRegex):($servernameRegex)"""

    // https://spec.matrix.org/v1.11/appendices/#room-ids
    private val roomIdRegex = """!($opaqueIdRegex):($servernameRegex)"""

    // https://spec.matrix.org/v1.11/appendices/#room-aliases
    private val roomAliasRegex = """#($roomAliasLocalpartRegex):($servernameRegex)"""

    // https://spec.matrix.org/v1.11/appendices/#event-ids
    private val eventIdRegex = """\$($opaqueIdRegex(?::$servernameRegex)?)"""

    // https://spec.matrix.org/v1.11/appendices/#matrix-uri-scheme
    private val queryParameterRegex =
        """($qesMark$namespaceIdRegex$eq([^\s&]+)(?:$amp$namespaceIdRegex$eq([^\s&]+))*)"""
    private val userUriRegex =
        """matrix:u\/($userLocalpartRegex):($servernameRegex)$queryParameterRegex?"""
    private val roomIdUriRegex =
        """matrix:roomid\/($opaqueIdRegex):($servernameRegex)$queryParameterRegex?"""
    private val roomAliasUriRegex =
        """matrix:r\/($roomAliasLocalpartRegex):($servernameRegex)$queryParameterRegex?"""
    private val eventUriRegex =
        """matrix:(roomid\/$opaqueIdRegex:$servernameRegex)\/e\/($opaqueIdRegex)$queryParameterRegex?"""

    // https://spec.matrix.org/v1.11/appendices/#matrixto-navigation
    private val viaArgumentRegex = """(?:\?(via=$servernameRegex))"""
    private val matrixToRegex = """https?:\/\/matrix\.to\/$hash\/"""
    private val userPermalinkRegex =
        """$matrixToRegex$at($userLocalpartRegex)$colon($servernameRegex)$viaArgumentRegex?"""

    // see implementation note
    private val roomIdPermalinkRegex =
        """$matrixToRegex$exlMark($opaqueIdRegex)$colon($servernameRegex)$viaArgumentRegex?"""
    private val roomAliasPermalinkRegex =
        """$matrixToRegex$hash($roomAliasLocalpartRegex)$colon($servernameRegex)$viaArgumentRegex?"""
    private val eventPermalinkRegex =
        """$matrixToRegex($exlMark$opaqueIdRegex$colon$servernameRegex)\/$dollar($opaqueIdRegex(?:$colon$servernameRegex)?)$viaArgumentRegex?"""

    private fun getAnchor(regex: String, maxLength: Int): String =
        "<a href=\"(?:(?!.{$maxLength,})$regex)\">(.*?)<\\/a>"

    val domain by lazy { servernameRegex.toRegex() }
    val userIdLocalpart by lazy { userLocalpartRegex.toRegex(255) }
    val roomAliasLocalpart by lazy { roomAliasLocalpartRegex.toRegex(255) }
    val opaqueId by lazy { opaqueIdRegex.toRegex(255) }
    val namespacedId by lazy { namespaceIdRegex.toRegex() }

    val userId by lazy { userIdRegex.toRegex(255) }
    val roomId by lazy { roomIdRegex.toRegex(255) }
    val roomAlias by lazy { roomAliasRegex.toRegex(255) }
    val eventId by lazy { eventIdRegex.toRegex(255) }

    val userIdUri by lazy { userUriRegex.toRegex(255) }
    val roomIdUri by lazy { roomIdUriRegex.toRegex(255) }
    val roomAliasUri by lazy { roomAliasUriRegex.toRegex(255) }
    val eventIdUri by lazy { eventUriRegex.toRegex(255) }

    private val userIdPermalink by lazy { userPermalinkRegex.toRegex(255) }
    private val roomIdPermalink by lazy { roomIdPermalinkRegex.toRegex(255) }
    private val roomAliasPermalink by lazy { roomAliasPermalinkRegex.toRegex(255) }
    private val eventIdPermalink by lazy { eventPermalinkRegex.toRegex(255) }

    internal val userIdPermalinkAnchor by lazy { getAnchor(userPermalinkRegex, 255).toRegex() }
    internal val roomIdPermalinkAnchor by lazy { getAnchor(roomIdPermalinkRegex, 255).toRegex() }
    internal val roomAliasPermalinkAnchor by lazy { getAnchor(roomAliasPermalinkRegex, 255).toRegex() }
    internal val eventIdPermalinkAnchor by lazy { getAnchor(eventPermalinkRegex, 255).toRegex() }

    private val userIdUriAnchor by lazy { getAnchor(userUriRegex, 255).toRegex() }
    private val roomIdUriAnchor by lazy { getAnchor(roomIdUriRegex, 255).toRegex() }
    private val roomAliasUriAnchor by lazy { getAnchor(roomAliasUriRegex, 255).toRegex() }
    private val eventIdUriAnchor by lazy { getAnchor(eventUriRegex, 255).toRegex() }

    fun findMentions(message: String): Map<IntRange, Mention> {
        val mentions = findUserIdMentions(message)
            .plus(findRoomIdMentions(message))
            .plus(findRoomAliasMentions(message))
            .plus(findEventMentions(message))

        val uniqueMentions = mentions.filter { mention ->
            mentions.forEach {
                if (it.key.contains(mention.key)) {
                    return@filter false
                }
            }

            true
        }

        return uniqueMentions
    }

    private fun findUserIdMentions(message: String): Map<IntRange, Mention> {
        fun handleMention(result: List<String>, options: List<String>): Mention.User {
            val match = result[0]
            val localpart = result[1]
            val domain = result[2]
            val (params, label) = parseOptions(options, anchor = match.startsWith("<a") && match.endsWith("</a>"))

            return Mention.User(UserId(localpart, domain), match, params, label)
        }

        val ids = findMention(userId, message, ::handleMention)
        val uris = findMention(userIdUri, message, ::handleMention)
        val uriAnchors = findMention(userIdUriAnchor, message, ::handleMention)
        val links = findMention(userIdPermalink, message, ::handleMention)
        val linkAnchors = findMention(userIdPermalinkAnchor, message, ::handleMention)

        return ids + uris + uriAnchors + links + linkAnchors
    }

    private fun findRoomIdMentions(message: String): Map<IntRange, Mention> {
        fun handleMention(
            result: List<String>,
            options: List<String>
        ): Mention.Room {
            val match = result[0]
            val localpart = result[1]
            val domain = result[2]
            val (params, label) = parseOptions(options, anchor = match.startsWith("<a") && match.endsWith("</a>"))

            return Mention.Room(RoomId(localpart, domain), match, params, label)
        }

        val ids = findMention(roomId, message, ::handleMention)
        val uris = findMention(roomIdUri, message, ::handleMention)
        val uriAnchors = findMention(roomIdUriAnchor, message, ::handleMention)
        val links = findMention(roomIdPermalink, message, ::handleMention)
        val linkAnchors = findMention(roomIdPermalinkAnchor, message, ::handleMention)

        return ids + uris + uriAnchors + links + linkAnchors
    }

    private fun findRoomAliasMentions(message: String): Map<IntRange, Mention> {
        fun handleMention(result: List<String>, options: List<String>): Mention.RoomAlias {
            val match = result[0]
            val localpart = result[1]
            val domain = result[2]
            val (params, label) = parseOptions(options, anchor = match.startsWith("<a") && match.endsWith("</a>"))

            return Mention.RoomAlias(RoomAliasId(localpart, domain), match, params, label)
        }

        val aliases = findMention(roomAlias, message, ::handleMention)
        val uris = findMention(roomAliasUri, message, ::handleMention)
        val uriAnchors = findMention(roomAliasUriAnchor, message, ::handleMention)
        val links = findMention(roomAliasPermalink, message, ::handleMention)
        val linkAnchors = findMention(roomAliasPermalinkAnchor, message, ::handleMention)

        return aliases + uris + uriAnchors + links + linkAnchors
    }

    private fun findEventMentions(message: String): Map<IntRange, Mention> {
        val ids = eventId.findAll(message).associate {
            val result = it.groupValues.filter(String::isNotBlank)

            val match = result[0]
            val eventId = result[1]

            it.range to Mention.Event(eventId = EventId("$$eventId"), match = match)
        }

        val uris = eventIdUri.findAll(message).associate {
            val (result, options) = it.groupValues.filter(String::isNotBlank).let {
                it.take(3) to it.drop(3)
            }

            val match = result[0]
            val roomId = result[1].replaceFirst("roomid/", "!")
            val eventId = result[2]
            val (params, _) = parseOptions(options, false)

            it.range to Mention.Event(RoomId(roomId), EventId("$$eventId"), match, parameters = params)
        }

        val uriAnchors = eventIdUriAnchor.findAll(message).associate {
            val (result, options) = it.groupValues.filter(String::isNotBlank).let {
                it.take(3) to it.drop(3)
            }

            val match = result[0]
            val roomId = result[1].replaceFirst("roomid/", "!")
            val eventId = result[2]
            val (params, label) = parseOptions(options, true)

            it.range to Mention.Event(
                RoomId(roomId),
                EventId("$$eventId"),
                match,
                parameters = params,
                label = label
            )
        }

        val links = eventIdPermalink.findAll(message).associate {
            val (result, options) = it.groupValues.filter(String::isNotBlank).let {
                it.take(3) to it.drop(3)
            }

            val match = result[0]
            val roomId = result[1]
            val eventId = result[2]
            val (params, _) = parseOptions(options, false)

            it.range to Mention.Event(RoomId(roomId.decodeURLPart()), EventId("$$eventId"), match, parameters = params)
        }

        val linkAnchors = eventIdPermalinkAnchor.findAll(message).associate {
            val (result, options) = it.groupValues.filter(String::isNotBlank).let {
                it.take(3) to it.drop(3)
            }

            val match = result[0]
            val roomId = result[1]
            val eventId = result[2]
            val (params, label) = parseOptions(options, true)

            it.range to Mention.Event(
                RoomId(roomId.decodeURLPart()),
                EventId("$$eventId"),
                match,
                parameters = params,
                label = label
            )
        }

        return ids + uris + uriAnchors + links + linkAnchors
    }

    fun parseOptions(options: List<String>, anchor: Boolean): Pair<Parameters?, String?> {
        val params = options.firstOrNull()?.let {
            Parameters.build {
                parseQueryString(it).forEach { key, values ->
                    this.appendAll(key.removePrefix("%3F").removePrefix("?"), values)
                }
            }
        }
        val label = if (anchor) options.last() else null

        return params to label
    }

    fun findMention(
        regex: Regex,
        message: String,
        handle: (List<String>, List<String>) -> Mention
    ): Map<IntRange, Mention> {
        return regex.findAll(message).associate { match ->
            match.range to match.groupValues.filter(String::isNotBlank).let {
                handle(it.take(3), it.drop(3))
            }
        }
    }
}

private fun String.toRegex(maxLength: Int) = "(?!.{${maxLength + 1},})$this".toRegex()
private fun IntRange.contains(other: IntRange): Boolean =
    this.start <= other.start && other.endInclusive <= this.endInclusive && this != other


package net.folivo.trixnity.core

import io.github.oshai.kotlinlogging.KotlinLogging
import net.folivo.trixnity.core.model.*

private val log = KotlinLogging.logger {}

object MatrixRegex {

    // https://spec.matrix.org/v1.9/appendices/#identifier-grammar
    private const val baseLocalpartRegex = """(?:[a-z0-9.\-_=\/+]+)"""

    // Nothing in the Appendix found, assumed that based on research
    // TL;DR it's an "encrypted" by the Server for security reasons
    private const val baseOpaqueIdRegex = """(?:\S+)"""

    // https://spec.matrix.org/v1.9/appendices/#server-name
    private const val basePortRegex = """:[0-9]{1,5}"""
    private const val baseDnsRegex = """(?:[\w-]+\.)?[\w-]+\.[\w-]+(?:$basePortRegex)?"""
    private const val baseIPV4Regex = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?:$basePortRegex)?"""
    private const val baseIPV6Regex = """\[[0-9a-fA-F:]+\](?:$basePortRegex)?"""
    private const val baseDomainRegex = """(?:(?:$baseIPV4Regex)|(?:$baseDnsRegex)|(?:$baseIPV6Regex))"""

    // https://spec.matrix.org/v1.9/appendices/#room-ids
    private const val baseRoomIdRegex = """(!)($baseOpaqueIdRegex):($baseDomainRegex)"""

    // https://spec.matrix.org/v1.9/appendices/#room-aliases
    private const val baseRoomAliasRegex = """(#)($baseLocalpartRegex):($baseDomainRegex)"""

    // https://spec.matrix.org/latest/appendices/#event-ids
    private const val baseEventIdRegex = """()(\$)($baseOpaqueIdRegex)"""

    // https://spec.matrix.org/v1.9/appendices/#user-identifiers
    private const val baseUserIdRegex = """(@)($baseLocalpartRegex):($baseDomainRegex)"""

    // https://spec.matrix.org/v1.9/appendices/#matrix-uri-scheme
    private const val baseRoomUriViaRegex =
        """(?:(?:\?action=join(?:&via=$baseDomainRegex)?)|(?:\?via=$baseDomainRegex(?:&action=join)?))?"""
    private const val baseEventUriRegex =
        """matrix:((?:(?:roomid)|(?:r))\/$baseLocalpartRegex:$baseDomainRegex)\/(e)\/($baseOpaqueIdRegex)$baseRoomUriViaRegex"""
    private const val baseRoomIdUriRegex =
        """matrix:(roomid)\/($baseLocalpartRegex):($baseDomainRegex)$baseRoomUriViaRegex"""
    private const val baseRoomAliasUriRegex =
        """matrix:(r)\/($baseLocalpartRegex):($baseDomainRegex)$baseRoomUriViaRegex"""
    private const val baseRoomUriRegex =
        """(?:$baseRoomIdUriRegex)|(?:$baseRoomAliasUriRegex)|(?:$baseEventUriRegex)"""
    private const val baseUserUriRegex =
        """matrix:(u)\/($baseLocalpartRegex):($baseDomainRegex)(?:(?:\?action=chat(?:&via=$baseDomainRegex)?)|(?:\?via=$baseDomainRegex(?:&action=chat)?))?"""

    // https://spec.matrix.org/v1.9/appendices/#matrixto-navigation
    private const val baseRoomLinkViaRegex = """(?:\?via=$baseDomainRegex)?"""
    private const val baseEventLinkRegex =
        """https?:\/\/matrix\.to\/#\/((?:@|!)$baseLocalpartRegex:$baseDomainRegex)\/(\$)($baseOpaqueIdRegex)$baseRoomLinkViaRegex"""
    private const val baseRoomIdLinkRegex =
        """https?:\/\/matrix\.to\/#\/$baseRoomIdRegex$baseRoomLinkViaRegex"""
    private const val baseRoomAliasLinkRegex =
        """https?:\/\/matrix\.to\/#\/$baseRoomAliasRegex$baseRoomLinkViaRegex"""
    private const val baseRoomLinkRegex =
        """(?:(?:$baseEventLinkRegex)|(?:$baseRoomIdLinkRegex)|(?:$baseRoomAliasLinkRegex))"""
    private const val baseRoomHtmlAnchorRegex = """<a href="$baseRoomLinkRegex">.*<\/a>"""
    private const val baseUserLinkRegex =
        """https?:\/\/matrix\.to\/#\/$baseUserIdRegex"""
    private const val baseUserHtmlAnchorRegex = """<a href="$baseUserLinkRegex">.*<\/a>"""

    private const val baseMentionRoomRegex =
        """(?:(?:$baseRoomIdRegex)|(?:$baseRoomAliasRegex)|(?:$baseEventIdRegex)|(?:$baseRoomUriRegex)|(?:$baseRoomLinkRegex)|(?:$baseRoomHtmlAnchorRegex))"""
    private const val baseMentionUserRegex =
        """(?:(?:$baseUserIdRegex)|(?:$baseUserUriRegex)|(?:$baseUserLinkRegex)|(?:$baseUserHtmlAnchorRegex))"""
    private const val baseMentionRegex = """(?:$baseMentionUserRegex)|(?:$baseMentionRoomRegex)"""

    val IPv4 by lazy { baseIPV4Regex.toRegex() }
    val domain by lazy { baseDomainRegex.toRegex() }
    val localpart by lazy { baseLocalpartRegex.toRegex() }

    val roomId by lazy { baseRoomIdRegex.toRegex() }
    val roomAlias by lazy { baseRoomAliasRegex.toRegex() }
    val roomUri by lazy { baseRoomUriRegex.toRegex() }
    val roomLink by lazy { baseRoomLinkRegex.toRegex() }
    val roomHtmlAnchor by lazy { baseRoomHtmlAnchorRegex.toRegex() }
    val roomMention by lazy { baseMentionRoomRegex.toRegex() }
    val roomIdLink = """<a href="$baseRoomIdLinkRegex">.*<\/a>""".toRegex()

    val userId by lazy { baseUserIdRegex.toRegex() }
    val userUri by lazy { baseUserUriRegex.toRegex() }
    val userLink by lazy { baseUserLinkRegex.toRegex() }
    val userHtmlAnchor by lazy { baseUserHtmlAnchorRegex.toRegex() }
    val userMention by lazy { baseMentionUserRegex.toRegex() }

    val mention by lazy { baseMentionRegex.toRegex() }

    @Deprecated("Use findMentions instead", ReplaceWith("findMentions(message)"))
    fun findUserMentions(message: String): Map<String, UserId> {
        return userMention.findAll(message).associate {
            val matched = it.groupValues[0]
            val localpart = it.groupValues[2] + it.groupValues[5] + it.groupValues[8] + it.groupValues[11]
            val domain = it.groupValues[3] + it.groupValues[6] + it.groupValues[9] + it.groupValues[12]
            matched to UserId(localpart, domain)
        }
    }

    fun findMentions(message: String, matcher: Regex = mention): Map<String, Mention> {
        return matcher.findAll(message).associate { result ->
            val matched = result.groupValues[0]
            val match = result.groupValues.drop(1).windowed(3, 3)

            val sigil = match.joinToString(separator = "") { it[0] }
            val localpart = match.joinToString(separator = "") { it[1] }
            val domain = match.joinToString(separator = "") { it[2] }

            log.trace {
                """
                    Matched: $matched
                    Sigil/Event Location: $sigil
                    Localpart/Event Sigil: $localpart
                    Domain/EventId: $domain
                """.trimIndent()
            }

            val eventlocation = sigil
            val eventSigil = localpart
            val eventId = domain

            when (sigil) {
                "@", "u" -> matched to Mention.User(UserId(localpart, domain))
                "!", "roomid" -> matched to Mention.Room(RoomId(localpart, domain))
                "#", "r" -> matched to Mention.RoomAlias(RoomAliasId(localpart, domain))
                else -> matched to Mention.Event(EventId(
                    "$eventSigil$eventId",
                ))
            }
        }
    }
}

private fun String.toRegex() = Regex(this + "$")
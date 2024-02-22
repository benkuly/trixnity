package net.folivo.trixnity.core

import net.folivo.trixnity.core.model.UserId

object MatrixRegex {

    // https://spec.matrix.org/v1.9/appendices/#identifier-grammar
    private const val baseLocalpartRegex = """[a-z0-9.\-_=\/+]+"""

    // Nothing in the Appendix found, assumed that based on research
    private const val baseOpaqueIdRegex = """[a-zA-Z0-9.\-_=\/+]+"""

    // https://spec.matrix.org/v1.9/appendices/#server-name
    private const val basePortRegex = """:[0-9]{1,5}"""
    private const val baseDomainRegex = """(?:[\w-]+\.)?[\w-]+\.[\w-]+(?:$basePortRegex)?"""
    private const val baseIPV4Regex = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?:$basePortRegex)?"""
    private const val baseIPV6Regex = """\[[0-9a-fA-F:]+\](?:$basePortRegex)?"""
    private const val baseServernameRegex = """(?:(?:$baseIPV4Regex)|(?:$baseDomainRegex)|(?:$baseIPV6Regex))"""

    // https://spec.matrix.org/v1.9/appendices/#room-ids
    private const val baseRoomIdRegex = """!($baseOpaqueIdRegex):($baseServernameRegex)"""

    // https://spec.matrix.org/v1.9/appendices/#room-aliases
    private const val baseRoomAliasRegex = """#($baseLocalpartRegex):($baseServernameRegex)"""

    // https://spec.matrix.org/latest/appendices/#event-ids
    private const val baseEventIdRegex = """$($baseOpaqueIdRegex)"""

    // https://spec.matrix.org/v1.9/appendices/#user-identifiers
    private const val baseUserIdRegex = """@($baseLocalpartRegex):($baseServernameRegex)"""

    // https://spec.matrix.org/v1.9/appendices/#matrix-uri-scheme
    private const val baseRoomUriRegex =
        """matrix:(r|(?:roomid))\/($baseLocalpartRegex):($baseServernameRegex)(?:/e/($baseOpaqueIdRegex))?(?:(?:\?action=chat(?:&via=$baseServernameRegex)?)|(?:\?via=$baseServernameRegex(?:&action=chat)?))?"""
    private const val baseUserUriRegex =
        """matrix:u\/($baseLocalpartRegex):($baseServernameRegex)(?:(?:\?action=chat(?:&via=$baseServernameRegex)?)|(?:\?via=$baseServernameRegex(?:&action=chat)?))?"""

    // https://spec.matrix.org/v1.9/appendices/#matrixto-navigation
    private const val baseRoomLinkRegex = """https?:\/\/matrix\.to\/#\/(#|!)($baseLocalpartRegex):($baseServernameRegex)(?:/($baseEventIdRegex):(?:$baseServernameRegex))?(?:\?via=($baseServernameRegex))"""
    private const val baseRoomHtmlAnchorRegex = """<a href="$baseRoomLinkRegex">.*<\/a>"""
    private const val baseUserLinkRegex = """https?:\/\/matrix\.to\/#\/@($baseLocalpartRegex):($baseServernameRegex)"""
    private const val baseUserHtmlAnchorRegex = """<a href="$baseUserLinkRegex">.*<\/a>"""

    private const val baseMentionRoomRegex =
        """(?:(?:$baseRoomIdRegex)|(?:$baseRoomAliasRegex)|(?:$baseRoomUriRegex)|(?:$baseRoomLinkRegex)|(?:$baseRoomHtmlAnchorRegex))${'$'}"""
    private const val baseMentionUserRegex =
        """(?:(?:$baseUserIdRegex)|(?:$baseUserUriRegex)|(?:$baseUserLinkRegex)|(?:$baseUserHtmlAnchorRegex))$"""

    val domain by lazy { baseServernameRegex.toRegex() }
    val localpart by lazy { baseLocalpartRegex.toRegex() }

    val roomId by lazy { baseRoomIdRegex.toRegex() }
    val roomAlias by lazy { baseRoomAliasRegex.toRegex() }
    val roomUri by lazy { baseRoomUriRegex.toRegex() }
    val roomLink by lazy { baseRoomLinkRegex.toRegex() }
    val roomHtmlAnchor by lazy { baseRoomHtmlAnchorRegex.toRegex() }
    val roomMention by lazy { baseMentionRoomRegex.toRegex() }

    val userId by lazy { baseUserIdRegex.toRegex() }
    val userUri by lazy { baseUserUriRegex.toRegex() }
    val userLink by lazy { baseUserLinkRegex.toRegex() }
    val userHtmlAnchor by lazy { baseUserHtmlAnchorRegex.toRegex() }
    val userMention by lazy { baseMentionUserRegex.toRegex() }

    fun findUserMentions(message: String): Map<String, UserId> {
        val matches = userMention.findAll(message)
        return matches.associate {
            val matched = it.groupValues[0]
            val localpart = it.groupValues[1] + it.groupValues[3] + it.groupValues[5] + it.groupValues[7]
            val domain = it.groupValues[2] + it.groupValues[4] + it.groupValues[6] + it.groupValues[8]
            matched to UserId(localpart, domain)
        }
    }
}
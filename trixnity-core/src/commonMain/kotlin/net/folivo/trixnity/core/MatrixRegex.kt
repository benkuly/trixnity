package net.folivo.trixnity.core

import net.folivo.trixnity.core.model.UserId

// https://spec.matrix.org/v1.9/appendices/#identifier-grammar
private const val baseLocalpartRegex = """[a-z0-9.\-_=\/+]+"""

// https://spec.matrix.org/v1.9/appendices/#server-name
private const val basePortRegex = """:[0-9]{1,5}"""
private const val baseDomainRegex = """(?:[\w-]+\.)?[\w-]+\.[\w-]+(?:$basePortRegex)?"""
private const val baseIPV4Regex = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?:$basePortRegex)?"""
private const val baseIPV6Regex = """\[[0-9a-fA-F:]+\](?:$basePortRegex)?"""
private const val baseServernameRegex = """(?:(?:$baseIPV4Regex)|(?:$baseDomainRegex)|(?:$baseIPV6Regex))"""

// https://spec.matrix.org/v1.9/appendices/#user-identifiers
private const val baseUserIdRegex = """@($baseLocalpartRegex):($baseServernameRegex)"""

// https://spec.matrix.org/v1.9/appendices/#matrix-uri-scheme
private const val baseUserUriRegex =
    """matrix:u\/($baseLocalpartRegex):($baseServernameRegex)(?:(?:\?action=chat(?:&via=$baseServernameRegex)?)|(?:\?via=$baseServernameRegex(?:&action=chat)?))?"""

// https://spec.matrix.org/v1.9/appendices/#matrixto-navigation
private const val baseUserLinkRegex = """https?:\/\/matrix\.to\/#\/@($baseLocalpartRegex):($baseServernameRegex)"""
private const val baseUserHtmlAnchorRegex = """<a href="$baseUserLinkRegex">.*<\/a>"""

private const val baseMentionUserRegex =
    """(?:(?:$baseUserIdRegex)|(?:$baseUserUriRegex)|(?:$baseUserLinkRegex)|(?:$baseUserHtmlAnchorRegex))$"""


object MatrixRegex {
    val userMentionRegex by lazy { baseMentionUserRegex.toRegex() }
    val domainRegex by lazy { baseDomainRegex.toRegex() }
    val localpartRegex by lazy { baseLocalpartRegex.toRegex() }
    val userIdRegex by lazy { baseUserIdRegex.toRegex() }
}

fun matchUsers(message: String): Map<String, UserId> {
    val matches = MatrixRegex.userMentionRegex.findAll(message)
    return matches.associate {
        val matched = it.groupValues[0]
        val localpart = it.groupValues[1] + it.groupValues[3] + it.groupValues[5] + it.groupValues[7]
        val domain = it.groupValues[2] + it.groupValues[4] + it.groupValues[6] + it.groupValues[8]
        matched to UserId(localpart, domain)
    }
}
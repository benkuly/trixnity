package net.folivo.trixnity.core

import net.folivo.trixnity.core.model.UserId

object MatrixRegex {

    // https://spec.matrix.org/v1.9/appendices/#identifier-grammar
    private const val baseUserLocalpartRegex = """[a-z0-9.\-_=/+]+"""

    // https://spec.matrix.org/v1.9/appendices/#server-name
    private const val basePortRegex = """:[0-9]{1,5}"""
    private const val baseDnsRegex = """(?:[\w-]+\.)?[\w-]+\.[\w-]+(?:$basePortRegex)?"""
    private const val baseIPV4Regex = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?:$basePortRegex)?"""
    private const val baseIPV6Regex = """\[[0-9a-fA-F:]+\](?:$basePortRegex)?"""
    private const val baseDomainRegex = """(?:(?:$baseIPV4Regex)|(?:$baseDnsRegex)|(?:$baseIPV6Regex))"""

    // https://spec.matrix.org/v1.9/appendices/#user-identifiers
    private const val baseUserIdRegex = """@($baseUserLocalpartRegex):($baseDomainRegex)"""

    // https://spec.matrix.org/v1.9/appendices/#matrix-uri-scheme
    private const val baseUserUriRegex =
        """matrix:u/($baseUserLocalpartRegex):($baseDomainRegex)(?:(?:\?action=chat(?:&via=$baseDomainRegex)?)|(?:\?via=$baseDomainRegex(?:&action=chat)?))?"""

    // https://spec.matrix.org/v1.9/appendices/#matrixto-navigation
    private const val baseUserLinkRegex = """https?://matrix\.to/#/@($baseUserLocalpartRegex):($baseDomainRegex)"""
    private const val baseUserHtmlLinkRegex = """<a href="$baseUserLinkRegex">.*</a>"""

    private const val baseUserMentionRegex =
        """(?:(?:$baseUserIdRegex)|(?:$baseUserUriRegex)|(?:$baseUserLinkRegex)|(?:$baseUserHtmlLinkRegex))$"""

    val domain by lazy { baseDomainRegex.toRegex() }
    
    val userLocalpart by lazy { baseUserLocalpartRegex.toRegex() }
    val userId by lazy { baseUserIdRegex.toRegex() }
    val userUri by lazy { baseUserUriRegex.toRegex() }
    val userLink by lazy { baseUserLinkRegex.toRegex() }
    val userHtmlLink by lazy { baseUserHtmlLinkRegex.toRegex() }
    val userMention by lazy { baseUserMentionRegex.toRegex() }

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
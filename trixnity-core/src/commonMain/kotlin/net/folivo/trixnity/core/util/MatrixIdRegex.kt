package net.folivo.trixnity.core.util

internal object MatrixIdRegex {
    // https://spec.matrix.org/v1.14/appendices/#server-name
    private const val baseDnsRegex = """(?:[A-Za-z0-9.-]{1,255})"""
    private const val baseIPV4Regex = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""
    private const val baseIPV6Regex = """\[[0-9a-fA-F:]+\]"""
    private const val basePortRegex = """:[0-9]{1,5}"""
    private const val servernameRegex =
        """(?:(?:(?:$baseDnsRegex)|$baseIPV4Regex)|(?:$baseIPV6Regex))(?:$basePortRegex)?"""

    // https://spec.matrix.org/v1.14/appendices/#opaque-identifiers
    private val opaqueIdRegex = """(?:[0-9A-Za-z-._~]+)"""

    // https://spec.matrix.org/v1.14/appendices/#user-identifiers
    private val userLocalpartRegex = """(?:[0-9a-z-=_/+.]+)"""
    val userIdRegex by lazy { """@($userLocalpartRegex):($servernameRegex)""".toRegex() }

    // https://spec.matrix.org/v1.14/appendices/#room-ids
    val roomIdRegex by lazy { """!($opaqueIdRegex)(?::($servernameRegex))?""".toRegex() }

    // https://spec.matrix.org/v1.11/appendices/#room-aliases
    private val roomAliasLocalpartRegex = """(?:[^:\s/]+)"""
    val roomAliasIdRegex by lazy { """#($roomAliasLocalpartRegex):($servernameRegex)""".toRegex() }

    // https://spec.matrix.org/v1.11/appendices/#event-ids
    val eventIdRegex by lazy { """\$($opaqueIdRegex(?::$servernameRegex)?)""".toRegex() }
}
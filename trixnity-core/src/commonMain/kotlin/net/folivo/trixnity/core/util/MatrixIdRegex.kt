package net.folivo.trixnity.core.util

internal object MatrixIdRegex {
    /**
     * @see [https://spec.matrix.org/unstable/appendices/#server-name]
     */
    // language=Regexp
    private const val DOMAIN_PATTERN = """(?:[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}|\[[0-9a-fA-F:.]{2,45}\]|[0-9a-zA-Z\-.]{1,255})(?::[0-9]{1,5})?"""

    /**
     * @see [https://spec.matrix.org/v1.14/appendices/#opaque-identifiers]
     */
    // language=Regexp
    private const val OPAQUE_ID_PATTERN = "[0-9A-Za-z-._~]+"

    /**
     * clients and servers MUST accept user IDs with localparts consisting of any legal non-surrogate Unicode
     * code points except for : and NUL (U+0000), including other control characters and the empty string.
     *
     * @see [https://spec.matrix.org/unstable/appendices/#user-identifiers]
     */
    // language=Regexp
    private const val USER_ID_PATTERN = "@[^:\uD800-\uDFFF]+:$DOMAIN_PATTERN"
    val userId = USER_ID_PATTERN.toRegex()

    // language=Regexp
    private const val REASONABLE_USER_ID_PATTERN = "@[0-9a-z-=_/+.]+:$DOMAIN_PATTERN"
    val reasonableUserId = REASONABLE_USER_ID_PATTERN.toRegex()

    /**
     * The localpart of a room alias may contain any valid non-surrogate Unicode codepoints except : and NUL.
     *
     * @see [https://spec.matrix.org/unstable/appendices/#room-aliases]
     */
    // language=Regexp
    private const val ROOM_ALIAS_PATTERN = "#[^:\uD800-\uDFFF]+:$DOMAIN_PATTERN"
    val roomAlias = ROOM_ALIAS_PATTERN.toRegex()

    /**
     * The localpart of a room ID (opaque_id above) may contain any valid non-surrogate Unicode code points,
     * including control characters, except : and NUL (U+0000)
     *
     * @see [https://spec.matrix.org/unstable/appendices/#room-ids]
     */
    // language=Regexp
    private const val ROOM_ID_PATTERN = "!(?:$OPAQUE_ID_PATTERN|[^:\uD800-\uDFFF]+:$DOMAIN_PATTERN)"
    val roomId = ROOM_ID_PATTERN.toRegex()

    // language=Regexp
    private const val REASONABLE_ROOM_ID_PATTERN = "!$OPAQUE_ID_PATTERN(?::$DOMAIN_PATTERN)?"
    val reasonableRoomId = ROOM_ID_PATTERN.toRegex()

    /**
     * However, the precise format depends upon the room version specification: early room versions included
     * a domain component, whereas more recent versions omit the domain and use a base64-encoded hash instead.
     *
     * @see [https://spec.matrix.org/unstable/appendices/#event-ids]
     */
    // language=Regexp
    private const val EVENT_ID_PATTERN = "\\$(?:$OPAQUE_ID_PATTERN|[^:\uD800-\uDFFF]+:$DOMAIN_PATTERN)"
    val eventId = EVENT_ID_PATTERN.toRegex()

    // language=Regexp
    private const val REASONABLE_EVENT_ID_PATTERN = "\\$$OPAQUE_ID_PATTERN(?::$DOMAIN_PATTERN)?"
    val reasonableEventId = REASONABLE_EVENT_ID_PATTERN.toRegex()

    /**
     * This matches user ids and room aliases on best-effort basis,
     * it is NOT intended to match all valid ids.
     */
    // language=Regexp
    private const val AUTOLINK_ID_PATTERN = "[@#](?:\\p{L}|\\p{N}|[\\-.=_/+])+:$DOMAIN_PATTERN"
    val autolinkId = AUTOLINK_ID_PATTERN.toRegex()
}
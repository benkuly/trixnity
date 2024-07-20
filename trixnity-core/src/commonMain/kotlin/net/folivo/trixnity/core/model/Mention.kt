package net.folivo.trixnity.core.model

import io.ktor.http.*

/**
 * Represents a mention. A mention can refer to various entities and potentially include actions associated with them.
 */
sealed interface Mention {

    /**
     * The textual representation of the mention within the message it appears.
     * Use with care, IntRange preferred
     */
    val match: String

    /**
     * If exists, the parameters provided in the URI
     */
    val parameters: Parameters?

    /**
     * The optional display name associated with the mention, if applicable.
     */
    val label: String?


    /**
     * Represents a mention of a user.
     */
    data class User(
        val userId: UserId,
        override val match: String,
        override val parameters: Parameters? = null,
        override val label: String? = null
    ) : Mention

    /**
     * Represents a mention of a room.
     */
    data class Room(
        val roomId: RoomId,
        override val match: String,
        override val parameters: Parameters? = null,
        override val label: String? = null
    ) : Mention

    /**
     * Represents a mention of a room alias
     */
    data class RoomAlias(
        val roomAliasId: RoomAliasId,
        override val match: String,
        override val parameters: Parameters? = null,
        override val label: String? = null
    ) : Mention

    /**
     * Represents a mention of a generic event.
     */
    data class Event(
        val roomId: RoomId? = null,
        val eventId: EventId,
        override val match: String,
        override val label: String? = null,
        override val parameters: Parameters? = null
    ) : Mention
}
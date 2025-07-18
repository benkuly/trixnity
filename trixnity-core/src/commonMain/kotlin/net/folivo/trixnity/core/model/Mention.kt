package net.folivo.trixnity.core.model

import io.ktor.http.*

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
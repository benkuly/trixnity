package net.folivo.trixnity.core.util

import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * Represents a mention. A mention can refer to various entities and potentially include actions associated with them.
 */
sealed interface Reference {
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
    ) : Reference

    /**
     * Represents a mention of a room.
     */
    data class Room(
        val roomId: RoomId,
        override val parameters: Parameters? = parametersOf()
    ) : Reference

    /**
     * Represents a mention of a room alias
     */
    data class RoomAlias(
        val roomAliasId: RoomAliasId,
        override val parameters: Parameters? = parametersOf()
    ) : Reference

    /**
     * Represents a mention of a generic event.
     */
    data class Event(
        val roomId: RoomId? = null,
        val eventId: EventId,
        override val parameters: Parameters? = parametersOf()
    ) : Reference
}
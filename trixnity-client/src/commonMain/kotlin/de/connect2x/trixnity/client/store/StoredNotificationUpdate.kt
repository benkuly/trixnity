package de.connect2x.trixnity.client.store

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.push.PushAction

/**
 * Represents an update operation for a notification. This [Change] can be [Change.New], [Change.Update] or [Change.Remove].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface StoredNotificationUpdate {
    val id: String
    val sortKey: String
    val roomId: RoomId

    @Serializable
    @SerialName("new")
    data class New(
        override val id: String,
        override val sortKey: String,
        val actions: Set<PushAction>,
        val content: Content,
    ) : StoredNotificationUpdate {
        override val roomId = content.roomId
    }

    @Serializable
    @SerialName("update")
    data class Update(
        override val id: String,
        override val sortKey: String,
        val actions: Set<PushAction>,
        val content: Content,
    ) : StoredNotificationUpdate {
        override val roomId = content.roomId
    }
    
    @Serializable
    @SerialName("remove")
    data class Remove(
        override val id: String,
        override val roomId: RoomId
    ) : StoredNotificationUpdate {
        override val sortKey: String = ""
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @JsonClassDiscriminator("type")
    sealed interface Content {
        val roomId: RoomId

        @Serializable
        @SerialName("message")
        data class Message(
            override val roomId: RoomId,
            val eventId: EventId,
        ) : Content

        @Serializable
        @SerialName("state")
        data class State(
            override val roomId: RoomId,
            val eventId: EventId?,
            @SerialName("eventContentType")
            val type: String,
            val stateKey: String,
        ) : Content
    }
}
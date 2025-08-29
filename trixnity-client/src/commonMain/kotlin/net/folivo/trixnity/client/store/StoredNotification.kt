package net.folivo.trixnity.client.store

import io.ktor.utils.io.core.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.push.PushAction
import okio.ByteString.Companion.toByteString

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface StoredNotification {
    val id: String
    val sortKey: String
    val roomId: RoomId
    val eventId: EventId?
    val actions: Set<PushAction>
    val dismissed: Boolean

    @Serializable
    @SerialName("message")
    data class Message(
        override val roomId: RoomId,
        override val eventId: EventId,
        override val sortKey: String,
        override val actions: Set<PushAction>,
        override val dismissed: Boolean = false,
    ) : StoredNotification {
        companion object {
            fun id(roomId: RoomId, eventId: EventId) =
                "message-$roomId-$eventId".toByteArray().toByteString().sha256().hex()
        }

        override val id: String = id(roomId, eventId)
    }

    @Serializable
    @SerialName("state")
    data class State(
        override val roomId: RoomId,
        override val eventId: EventId?,
        @SerialName("eventContentType")
        val type: String,
        val stateKey: String,
        override val sortKey: String,
        override val actions: Set<PushAction>,
        override val dismissed: Boolean = false,
    ) : StoredNotification {
        companion object {
            fun id(roomId: RoomId, type: String, stateKey: String) =
                "state-$roomId-$type-$stateKey".toByteArray().toByteString().sha256().hex()
        }

        override val id: String = id(roomId, type, stateKey)
    }
}


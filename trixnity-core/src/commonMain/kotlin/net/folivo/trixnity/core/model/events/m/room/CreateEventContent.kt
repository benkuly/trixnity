package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent.RoomType

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mroomcreate">matrix spec</a>
 */
@Serializable
data class CreateEventContent(
    @SerialName("creator")
    val creator: UserId,
    @SerialName("m.federate")
    val federate: Boolean = true,
    @SerialName("room_version")
    val roomVersion: String = "1",
    @SerialName("predecessor")
    val predecessor: PreviousRoom? = null,
    @SerialName("type")
    val type: RoomType = RoomType.Room
) : StateEventContent {
    @Serializable
    data class PreviousRoom(
        @SerialName("room_id")
        val roomId: RoomId,
        @SerialName("event_id")
        val eventId: EventId
    )

    @Serializable(with = RoomTypeSerializer::class)
    sealed class RoomType {
        abstract val name: String?

        object Room : RoomType() {
            override val name: String? = null
        }

        object Space : RoomType() {
            override val name = "m.space"
        }

        data class Unknown(override val name: String) : RoomType()
    }
}

object RoomTypeSerializer : KSerializer<RoomType> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomTypeSerializer")

    override fun deserialize(decoder: Decoder): RoomType {
        return when (val name = decoder.decodeString()) {
            RoomType.Room.name -> RoomType.Room
            RoomType.Space.name -> RoomType.Space
            else -> RoomType.Unknown(name)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: RoomType) {
        val name = value.name
        if (name == null) encoder.encodeNull()
        else encoder.encodeString(name)
    }
}
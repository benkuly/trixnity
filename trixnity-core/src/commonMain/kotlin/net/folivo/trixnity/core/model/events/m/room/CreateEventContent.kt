package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.decodeFromJsonElement
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent.RoomType

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomcreate">matrix spec</a>
 */
@Serializable
data class CreateEventContent(
    @SerialName("m.federate")
    val federate: Boolean? = null,
    @SerialName("room_version")
    val roomVersion: String? = null,
    @SerialName("predecessor")
    val predecessor: PreviousRoom? = null,
    @SerialName("type")
    val type: RoomType? = null,
    @SerialName("additional_creators")
    val additionalCreators: Set<UserId>? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent {
    @Serializable
    data class PreviousRoom(
        @SerialName("room_id")
        val roomId: RoomId,
        @Deprecated("deprecated since room version 12")
        @SerialName("event_id")
        val eventId: EventId? = null
    )

    @Serializable(with = RoomTypeSerializer::class)
    sealed interface RoomType {
        val name: String?

        data object Room : RoomType {
            override val name: String? = null
        }

        data object Space : RoomType {
            override val name = "m.space"
        }

        data class Unknown(override val name: String) : RoomType
    }
}

object RoomTypeSerializer : KSerializer<RoomType> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomTypeSerializer")

    override fun deserialize(decoder: Decoder): RoomType {
        require(decoder is JsonDecoder)
        return when (val name = decoder.json.decodeFromJsonElement<String?>(decoder.decodeJsonElement())) {
            null -> RoomType.Room
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
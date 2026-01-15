package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mtag">matrix spec</a>
 */
@Serializable
data class TagEventContent(
    @SerialName("tags") val tags: Map<TagName, Tag>,
) : RoomAccountDataEventContent {

    @Serializable(with = TagName.Serializer::class)
    interface TagName {
        val name: String

        data object Favourite : TagName {
            override val name: String = "m.favourite"
        }

        data object LowPriority : TagName {
            override val name: String = "m.lowpriority"
        }

        data object ServerNotice : TagName {
            override val name: String = "m.server_notice"
        }

        data class Unknown(override val name: String) : TagName

        object Serializer : KSerializer<TagName> {
            override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TagName", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): TagName {
                return when (val name = decoder.decodeString()) {
                    Favourite.name -> Favourite
                    LowPriority.name -> LowPriority
                    ServerNotice.name -> ServerNotice
                    else -> Unknown(name)
                }
            }

            override fun serialize(encoder: Encoder, value: TagName) {
                encoder.encodeString(value.name)
            }

        }
    }

    @Serializable
    data class Tag(
        @SerialName("order") val order: Double? = null
    )
}
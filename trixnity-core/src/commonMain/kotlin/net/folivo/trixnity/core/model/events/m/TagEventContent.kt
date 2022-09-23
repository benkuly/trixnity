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
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#mtag">matrix spec</a>
 */
@Serializable
data class TagEventContent(
    @SerialName("tags") val tags: Map<TagName, Tag>,
) : RoomAccountDataEventContent {

    @Serializable(with = TagNameSerializer::class)
    interface TagName {
        val name: String

        object Favourite : TagName {
            override val name: String = "m.favourite"
        }

        object LowPriority : TagName {
            override val name: String = "m.lowpriority"
        }

        object ServerNotice : TagName {
            override val name: String = "m.server_notice"
        }

        data class Unknown(override val name: String) : TagName
    }

    @Serializable
    data class Tag(
        @SerialName("order") val order: Double? = null
    )
}

object TagNameSerializer : KSerializer<TagEventContent.TagName> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TagNameSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TagEventContent.TagName {
        return when (val name = decoder.decodeString()) {
            TagEventContent.TagName.Favourite.name -> TagEventContent.TagName.Favourite
            TagEventContent.TagName.LowPriority.name -> TagEventContent.TagName.LowPriority
            TagEventContent.TagName.ServerNotice.name -> TagEventContent.TagName.ServerNotice
            else -> TagEventContent.TagName.Unknown(name)
        }
    }

    override fun serialize(encoder: Encoder, value: TagEventContent.TagName) {
        encoder.encodeString(value.name)
    }

}
package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

@MSC4354
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = StoredStickyEvent.Serializer::class)
data class StoredStickyEvent<T : StickyEventContent>(
    val event: @Contextual RoomEvent<T>,
    val startTime: Instant,
    val endTime: Instant,
) {
    object Serializer : KSerializer<StoredStickyEvent<StickyEventContent>> {
        private val generatedSerializer = generatedSerializer(
            object : KSerializer<StickyEventContent> {
                override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DummyStickyEventContent")

                override fun serialize(
                    encoder: Encoder,
                    value: StickyEventContent
                ) {
                    throw IllegalStateException("This serializer should never be used")
                }

                override fun deserialize(decoder: Decoder): StickyEventContent {
                    throw IllegalStateException("This serializer should never be used")
                }

            }
        )
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StoredStickyEvent")

        override fun serialize(
            encoder: Encoder,
            value: StoredStickyEvent<StickyEventContent>
        ) {
            encoder.encodeSerializableValue(generatedSerializer, value)
        }

        override fun deserialize(decoder: Decoder): StoredStickyEvent<StickyEventContent> =
            decoder.decodeSerializableValue(generatedSerializer)
    }
}



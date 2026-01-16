package de.connect2x.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.ExtensibleEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.block.EventContentBlocks
import de.connect2x.trixnity.core.model.events.block.m.TextContentBlock
import de.connect2x.trixnity.core.model.events.block.m.TopicContentBlock
import de.connect2x.trixnity.core.serialization.events.ExtensibleEventContentSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.15/client-server-api/#mroomtopic">matrix spec</a>
 */
@Serializable(TopicEventContent.Serializer::class)
data class TopicEventContent(
    override val blocks: EventContentBlocks,
    override val legacy: Legacy,
) : StateEventContent, ExtensibleEventContent<TopicEventContent.Legacy> {
    constructor(topic: TopicContentBlock, externalUrl: String? = null) : this(
        EventContentBlocks(topic),
        Legacy(topic.text?.plain ?: "", externalUrl)
    )

    constructor(topic: String, externalUrl: String? = null) : this(
        EventContentBlocks(TopicContentBlock(TextContentBlock(topic))),
        Legacy(topic, externalUrl)
    )

    val topic: TopicContentBlock? = blocks[TopicContentBlock]
    override val externalUrl: String? = legacy.externalUrl

    @Serializable
    data class Legacy(
        @SerialName("topic") val topic: String,
        @SerialName("external_url") val externalUrl: String? = null,
    ) : ExtensibleEventContent.Legacy

    object Serializer : ExtensibleEventContentSerializer<Legacy, TopicEventContent>(
        "TopicEventContent",
        { blocks: EventContentBlocks, legacy: Legacy? -> TopicEventContent(blocks, legacy ?: Legacy(topic = "")) },
        Legacy.serializer(),
    )
}
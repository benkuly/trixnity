package net.folivo.trixnity.core.model.events.block.m

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.block.EventContentBlock
import net.folivo.trixnity.core.model.events.block.EventContentBlocks
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class TopicContentBlock(
    val blocks: @Contextual EventContentBlocks,
) : EventContentBlock.Default {
    constructor(text: TextContentBlock) : this(EventContentBlocks(text))

    override val type: EventContentBlock.Type<TopicContentBlock> get() = Type

    companion object Type : EventContentBlock.Type<TopicContentBlock> {
        override val value: String = "m.topic"

        override fun toString(): String = value
    }

    val text: TextContentBlock? get() = blocks[TextContentBlock]
}
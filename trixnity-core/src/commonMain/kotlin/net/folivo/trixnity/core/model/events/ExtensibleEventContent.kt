package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.block.EventContentBlocks

/**
 * This is based on MSC1767 and maybe included into [EventContent] in the future.
 */
interface ExtensibleEventContent<L : ExtensibleEventContent.Legacy> {
    val blocks: EventContentBlocks
    val legacy: L?

    interface Legacy {
        @Serializable
        object None : Legacy
    }
}
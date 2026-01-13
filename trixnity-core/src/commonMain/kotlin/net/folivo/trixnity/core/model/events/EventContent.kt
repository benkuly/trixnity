package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.events.block.EventContentBlocks
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

sealed interface EventContent

/**
 * Content of a matrix room event
 *
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#types-of-room-events">Types of matrix room events</a>
 */
sealed interface RoomEventContent : EventContent {
    /**
     * @see <a href="https://spec.matrix.org/v1.10/application-service-api/#referencing-messages-from-a-third-party-network">matrix spec</a>
     */
    val externalUrl: String?
}

/**
 * Content of a matrix message event
 */
interface MessageEventContent : RoomEventContent {
    val relatesTo: RelatesTo?
    val mentions: Mentions?

    /**
     * This should return the same instance, but with the [relatesTo] property set to the given value.
     * It is used for event content replacing.
     */
    fun copyWith(relatesTo: RelatesTo?): MessageEventContent
}

/**
 * Content of a matrix state event
 */
interface StateEventContent : RoomEventContent

interface ToDeviceEventContent : EventContent

interface EphemeralEventContent : EventContent

interface EphemeralDataUnitContent : EventContent

interface GlobalAccountDataEventContent : EventContent

interface RoomAccountDataEventContent : EventContent

@Serializable
data object EmptyEventContent :
    EventContent,
    RoomEventContent,
    MessageEventContent,
    StateEventContent,
    ToDeviceEventContent,
    EphemeralEventContent,
    EphemeralDataUnitContent,
    GlobalAccountDataEventContent,
    RoomAccountDataEventContent {
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
    override val externalUrl: String? = null

    override fun copyWith(relatesTo: RelatesTo?) = this
}

data class UnknownEventContent(
    val raw: JsonObject, // TODO remove when ExtensibleEventContent is the default
    override val blocks: EventContentBlocks,
    val eventType: String,
) : EventContent,
    RoomEventContent,
    MessageEventContent,
    StateEventContent,
    ToDeviceEventContent,
    EphemeralEventContent,
    EphemeralDataUnitContent,
    GlobalAccountDataEventContent,
    RoomAccountDataEventContent,
    ExtensibleEventContent<ExtensibleEventContent.Legacy.None> {
    // is always null, because this class is the last fallback, when nothing can be deserialized
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
    override val externalUrl: String? = null
    override val legacy: ExtensibleEventContent.Legacy.None? = null

    override fun copyWith(relatesTo: RelatesTo?) = this
}
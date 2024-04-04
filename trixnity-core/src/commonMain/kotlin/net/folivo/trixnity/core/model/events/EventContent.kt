package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

sealed interface EventContent

sealed interface RoomEventContent : EventContent {
    /**
     * @see <a href="https://spec.matrix.org/v1.10/application-service-api/#referencing-messages-from-a-third-party-network">matrix spec</a>
     */
    val externalUrl: String?
}

interface MessageEventContent : RoomEventContent {
    val relatesTo: RelatesTo?
    val mentions: Mentions?
}

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
}

data class UnknownEventContent(
    val raw: JsonObject,
    val eventType: String,
) : EventContent,
    RoomEventContent,
    MessageEventContent,
    StateEventContent,
    ToDeviceEventContent,
    EphemeralEventContent,
    EphemeralDataUnitContent,
    GlobalAccountDataEventContent,
    RoomAccountDataEventContent {
    // is always null, because this class is the last fallback, when nothing can be deserialized
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
    override val externalUrl: String? = null
}
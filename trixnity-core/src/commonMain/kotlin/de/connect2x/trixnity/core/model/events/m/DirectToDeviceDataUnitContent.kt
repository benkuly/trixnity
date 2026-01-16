package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.EphemeralDataUnitContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#send-to-device-messaging">matrix spec</a>
 */
@Serializable
data class DirectToDeviceDataUnitContent(
    @SerialName("message_id")
    val messageId: String,
    @SerialName("messages")
    val messages: Map<UserId, Map<String, JsonObject>>, // TODO could be ToDeviceEventContent
    @SerialName("sender")
    val sender: UserId,
    @SerialName("type")
    val type: String,
) : EphemeralDataUnitContent
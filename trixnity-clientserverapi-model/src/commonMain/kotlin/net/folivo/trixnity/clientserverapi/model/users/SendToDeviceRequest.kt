package net.folivo.trixnity.clientserverapi.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

@Serializable
data class SendToDeviceRequest<C : ToDeviceEventContent>(
    @SerialName("messages")
    val messages: Map<UserId, Map<String, C>>
)
package de.connect2x.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys

@Serializable
data class DecryptedOlmEvent<C : EventContent>(
    @SerialName("content") override val content: C,
    @SerialName("sender") val sender: UserId,
    @SerialName("keys") val senderKeys: Keys,
    @SerialName("sender_device_keys") val senderDeviceKeys: SignedDeviceKeys? = null,
    @SerialName("recipient") val recipient: UserId,
    @SerialName("recipient_keys") val recipientKeys: Keys,
) : Event<C>
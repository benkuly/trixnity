package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Keys

@Serializable
data class DecryptedOlmEvent<C : EventContent>(
    @SerialName("content") val content: C,
    @SerialName("sender") val sender: UserId,
    @SerialName("keys") val senderKeys: Keys,
    @SerialName("recipient") val recipient: UserId,
    @SerialName("recipient_keys") val recipientKeys: Keys
)
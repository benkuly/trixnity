package net.folivo.trixnity.client.store

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent

@Serializable
data class StoredSecret(
    @SerialName("event")
    val event: Event.GlobalAccountDataEvent<out SecretEventContent>,
    @SerialName("decryptedPrivateKey")
    val decryptedPrivateKey: String
)

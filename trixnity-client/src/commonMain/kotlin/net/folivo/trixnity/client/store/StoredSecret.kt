package net.folivo.trixnity.client.store

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent

@Serializable
data class StoredSecret(
    @SerialName("event")
    val event: @Contextual GlobalAccountDataEvent<out SecretEventContent>,
    @SerialName("decryptedPrivateKey")
    val decryptedPrivateKey: String
)

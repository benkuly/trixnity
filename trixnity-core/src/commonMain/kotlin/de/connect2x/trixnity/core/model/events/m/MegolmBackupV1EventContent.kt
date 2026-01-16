package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretEventContent

@Serializable
data class MegolmBackupV1EventContent(
    @SerialName("encrypted")
    override val encrypted: Map<String, JsonElement>
) : SecretEventContent

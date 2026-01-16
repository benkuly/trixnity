package de.connect2x.trixnity.core.model.events.m.crosssigning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretEventContent

@Serializable
data class MasterKeyEventContent(
    @SerialName("encrypted")
    override val encrypted: Map<String, JsonElement>
) : SecretEventContent

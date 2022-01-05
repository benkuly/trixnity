package net.folivo.trixnity.core.model.events.m.crosssigning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent

@Serializable
data class SelfSigningKeyEventContent(
    @SerialName("encrypted")
    override val encrypted: Map<String, JsonElement>
) : SecretEventContent

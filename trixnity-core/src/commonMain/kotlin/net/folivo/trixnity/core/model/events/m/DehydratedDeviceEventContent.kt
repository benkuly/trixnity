package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent

@MSC3814
@Serializable
data class DehydratedDeviceEventContent(
    @SerialName("encrypted")
    override val encrypted: Map<String, JsonElement>
) : SecretEventContent

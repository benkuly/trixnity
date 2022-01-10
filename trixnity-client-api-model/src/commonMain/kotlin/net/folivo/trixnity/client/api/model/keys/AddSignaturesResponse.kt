package net.folivo.trixnity.client.api.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.model.UserId

@Serializable
data class AddSignaturesResponse(
    @SerialName("failures")
    val failures: Map<UserId, Map<String, JsonElement>>,
)
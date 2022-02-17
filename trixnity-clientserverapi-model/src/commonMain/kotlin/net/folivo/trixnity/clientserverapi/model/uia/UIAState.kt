package net.folivo.trixnity.clientserverapi.model.uia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UIAState(
    @SerialName("completed") val completed: List<AuthenticationType> = listOf(),
    @SerialName("flows") val flows: Set<FlowInformation> = setOf(),
    @SerialName("params") val parameter: JsonObject? = null,
    @SerialName("session") val session: String? = null
) {
    @Serializable
    data class FlowInformation(
        @SerialName("stages") val stages: List<AuthenticationType>
    )
}

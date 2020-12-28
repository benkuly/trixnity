package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.MatrixId.UserId

@Serializable
data class StrippedStateEvent(
    @SerialName("type")
    val type: String,
    @SerialName("content")
    val content: JsonObject, // TODO should be event content, so we should handle StrippedStateEvent as subset of StateEvent
    @SerialName("state_key")
    val stateKey: String,
    @SerialName("sender")
    val sender: UserId
)
package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class GetKeyChangesResponse(
    @SerialName("changed")
    val changed: Set<UserId>,
    @SerialName("left")
    val left: Set<UserId>
)
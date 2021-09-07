package net.folivo.trixnity.client.api.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.UserId

@Serializable
data class GetKeyChangesResponse(
    @SerialName("changed")
    val changed: Set<UserId>,
    @SerialName("left")
    val left: Set<UserId>
)
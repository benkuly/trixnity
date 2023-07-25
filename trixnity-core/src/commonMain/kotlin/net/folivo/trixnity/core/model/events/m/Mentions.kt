package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class Mentions(
    @SerialName("user_ids") val users: Set<UserId>? = null,
    @SerialName("room") val room: Boolean? = null,
)
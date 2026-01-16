package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.UserId

@Serializable
data class Mentions(
    @SerialName("user_ids") val users: Set<UserId>? = null,
    @SerialName("room") val room: Boolean? = null,
)
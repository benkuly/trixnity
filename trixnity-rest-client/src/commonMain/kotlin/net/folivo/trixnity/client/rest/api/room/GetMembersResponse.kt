package net.folivo.trixnity.client.rest.api.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.m.room.MemberEvent

@Serializable
internal data class GetMembersResponse(
    @SerialName("chunk") val chunk: List<MemberEvent>
)
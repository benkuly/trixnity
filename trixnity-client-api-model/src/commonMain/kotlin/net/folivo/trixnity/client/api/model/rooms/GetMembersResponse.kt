package net.folivo.trixnity.client.api.model.rooms

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

@Serializable
data class GetMembersResponse(
    @SerialName("chunk") val chunk: List<@Contextual StateEvent<MemberEventContent>>
)
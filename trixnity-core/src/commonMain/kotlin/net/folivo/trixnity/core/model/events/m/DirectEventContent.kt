package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.serialization.m.DirectEventContentSerializer

@Serializable(with = DirectEventContentSerializer::class)
data class DirectEventContent(
    val mappings: Map<UserId, Set<RoomId>?>
) : GlobalAccountDataEventContent
package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class DirectEventContent(
    val mappings: Map<UserId, Set<RoomId>?>
) : GlobalAccountDataEventContent
package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class DirectEventContent(
    val mappings: Map<UserId, Set<RoomId>?>
) : GlobalAccountDataEventContent
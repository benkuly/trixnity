package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.RoomAccountDataEventContent

@Serializable
data class MarkedUnreadEventContent(
    @SerialName("unread") val unread: Boolean,
) : RoomAccountDataEventContent
package net.folivo.trixnity.core.model.events

import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

interface MessageEventContent : RoomEventContent {
    val relatesTo: RelatesTo?
    val mentions: Mentions?
}
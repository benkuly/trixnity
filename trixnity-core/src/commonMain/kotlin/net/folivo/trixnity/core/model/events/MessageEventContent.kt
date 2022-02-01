package net.folivo.trixnity.core.model.events

interface MessageEventContent : RoomEventContent {
    val relatesTo: RelatesTo?
}
package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

fun MessageBuilder.emote(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    roomMessageBuilder(body, format, formattedBody) {
        RoomMessageEventContent.TextBased.Emote(
            body = this.body,
            format = this.format,
            formattedBody = this.formattedBody,
            relatesTo = relatesTo,
            mentions = mentions,
        )
    }
}
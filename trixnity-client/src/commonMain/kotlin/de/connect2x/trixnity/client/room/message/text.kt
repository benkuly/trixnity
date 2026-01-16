package de.connect2x.trixnity.client.room.message

import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent

fun MessageBuilder.text(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    roomMessageBuilder(body, format, formattedBody) {
        RoomMessageEventContent.TextBased.Text(
            body = this.body,
            format = this.format,
            formattedBody = this.formattedBody,
            relatesTo = relatesTo,
            mentions = mentions,
        )
    }
}
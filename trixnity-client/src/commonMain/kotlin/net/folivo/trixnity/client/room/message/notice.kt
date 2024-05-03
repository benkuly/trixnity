package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

fun MessageBuilder.notice(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    roomMessageBuilder(body, format, formattedBody) {
        RoomMessageEventContent.TextBased.Notice(
            body = this.body,
            format = this.format,
            formattedBody = this.formattedBody,
            relatesTo = relatesTo,
            mentions = mentions,
        )
    }
}
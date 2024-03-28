package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.utils.TrixnityDsl

@TrixnityDsl
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
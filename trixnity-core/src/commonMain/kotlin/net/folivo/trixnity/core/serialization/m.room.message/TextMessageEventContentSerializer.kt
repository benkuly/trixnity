package net.folivo.trixnity.core.serialization.m.room.message

import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

object TextMessageEventContentSerializer :
    AddFieldsSerializer<TextMessageEventContent>(
        TextMessageEventContent.serializer(),
        "msgtype" to TextMessageEventContent.type
    )
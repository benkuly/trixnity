package net.folivo.trixnity.core.serialization.m.room.message

import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.HideDiscriminatorSerializer

object TextMessageEventContentSerializer :
    HideDiscriminatorSerializer<TextMessageEventContent>(
        TextMessageEventContent.serializer(),
        "msgtype",
        TextMessageEventContent.type
    )
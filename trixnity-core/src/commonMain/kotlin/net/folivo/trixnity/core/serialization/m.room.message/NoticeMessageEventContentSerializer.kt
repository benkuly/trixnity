package net.folivo.trixnity.core.serialization.m.room.message

import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.NoticeMessageEventContent
import net.folivo.trixnity.core.serialization.HideDiscriminatorSerializer

object NoticeMessageEventContentSerializer :
    HideDiscriminatorSerializer<NoticeMessageEventContent>(
        NoticeMessageEventContent.serializer(),
        "msgtype",
        NoticeMessageEventContent.type
    )
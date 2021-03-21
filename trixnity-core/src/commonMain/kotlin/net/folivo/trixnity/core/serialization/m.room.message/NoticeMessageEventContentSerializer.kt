package net.folivo.trixnity.core.serialization.m.room.message

import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.NoticeMessageEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

object NoticeMessageEventContentSerializer :
    AddFieldsSerializer<NoticeMessageEventContent>(
        NoticeMessageEventContent.serializer(),
        "msgtype" to NoticeMessageEventContent.type
    )
package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.core.model.events.MessageEventContent

typealias uploadAndTransformMessageEventContent = suspend (
    content: MessageEventContent,
    upload: suspend (cacheUri: String) -> String
) -> MessageEventContent
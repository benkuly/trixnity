package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.core.model.events.MessageEventContent

typealias UploadAndTransformMessageEventContent = suspend (
    content: MessageEventContent,
    upload: suspend (cacheUri: String, thumbnailUploaded: Boolean) -> String
) -> MessageEventContent
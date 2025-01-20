package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.core.model.events.MessageEventContent

typealias UploadAndTransformMessageEventContent = suspend (
    content: MessageEventContent,
    upload: suspend (cacheUri: String, uploadProgress: FileTransferProgress) -> String
) -> MessageEventContent
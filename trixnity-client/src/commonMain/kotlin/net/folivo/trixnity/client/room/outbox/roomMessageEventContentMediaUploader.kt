package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.*

// TODO test
suspend fun roomMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String) -> String
): RoomMessageEventContent {
    require(content is RoomMessageEventContent)
    return when (content) {
        is TextMessageEventContent -> content
        is NoticeMessageEventContent -> content
        is EmoteMessageEventContent -> content
        is VerificationRequestMessageEventContent -> content
        is FileMessageEventContent -> {
            val encryptedContentUrl = content.file?.url
            val contentUrl = content.url
            if (encryptedContentUrl != null) {
                val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it) }
                val mxcUri = upload(encryptedContentUrl)
                content.copy(
                    file = content.file?.copy(url = mxcUri),
                    info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                        content.info?.thumbnailFile?.copy(
                            url = it
                        )
                    })
                )
            } else if (contentUrl != null) {
                val thumbnailMxcUri = content.info?.thumbnailUrl?.let { upload(it) }
                val mxcUri = upload(contentUrl)
                content.copy(
                    url = mxcUri,
                    info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
                )
            } else content
        }
        is ImageMessageEventContent -> {
            val encryptedContentUrl = content.file?.url
            val contentUrl = content.url
            if (encryptedContentUrl != null) {
                val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it) }
                val mxcUri = upload(encryptedContentUrl)
                content.copy(
                    file = content.file?.copy(url = mxcUri),
                    info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                        content.info?.thumbnailFile?.copy(
                            url = it
                        )
                    })
                )
            } else if (contentUrl != null) {
                val thumbnailMxcUri = content.info?.thumbnailUrl?.let { upload(it) }
                val mxcUri = upload(contentUrl)
                content.copy(
                    url = mxcUri,
                    info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
                )
            } else content
        }
        is VideoMessageEventContent -> {
            val encryptedContentUrl = content.file?.url
            val contentUrl = content.url
            if (encryptedContentUrl != null) {
                val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it) }
                val mxcUri = upload(encryptedContentUrl)
                content.copy(
                    file = content.file?.copy(url = mxcUri),
                    info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                        content.info?.thumbnailFile?.copy(
                            url = it
                        )
                    })
                )
            } else if (contentUrl != null) {
                val thumbnailMxcUri = content.info?.thumbnailUrl?.let { upload(it) }
                val mxcUri = upload(contentUrl)
                content.copy(
                    url = mxcUri,
                    info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
                )
            } else content
        }
        is AudioMessageEventContent -> {
            val encryptedContentUrl = content.file?.url
            val contentUrl = content.url
            if (encryptedContentUrl != null) {
                val mxcUri = upload(encryptedContentUrl)
                content.copy(file = content.file?.copy(url = mxcUri))
            } else if (contentUrl != null) {
                val mxcUri = upload(contentUrl)
                content.copy(url = mxcUri)
            } else content
        }
        is UnknownRoomMessageEventContent -> content
    }
}

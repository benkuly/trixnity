package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

// TODO test
suspend fun roomMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String) -> String
): RoomMessageEventContent {
    require(content is RoomMessageEventContent)
    return when (content) {
        is RoomMessageEventContent.TextMessageEventContent -> content
        is RoomMessageEventContent.NoticeMessageEventContent -> content
        is RoomMessageEventContent.EmoteMessageEventContent -> content
        is RoomMessageEventContent.FileMessageEventContent -> {
            val encryptedContentUrl = content.file?.url
            val contentUrl = content.url
            if (encryptedContentUrl != null) {
                val thumbnailMxcUri = content.format?.thumbnailFile?.url?.let { upload(it) }
                val mxcUri = upload(encryptedContentUrl)
                content.copy(
                    file = content.file?.copy(url = mxcUri),
                    format = content.format?.copy(thumbnailFile = thumbnailMxcUri?.let {
                        content.format?.thumbnailFile?.copy(
                            url = it
                        )
                    })
                )
            } else if (contentUrl != null) {
                val thumbnailMxcUri = content.format?.thumbnailUrl?.let { upload(it) }
                val mxcUri = upload(contentUrl)
                content.copy(
                    url = mxcUri,
                    format = content.format?.copy(thumbnailUrl = thumbnailMxcUri)
                )
            } else content
        }
        is RoomMessageEventContent.ImageMessageEventContent -> {
            val encryptedContentUrl = content.file?.url
            val contentUrl = content.url
            if (encryptedContentUrl != null) {
                val thumbnailMxcUri = content.format?.thumbnailFile?.url?.let { upload(it) }
                val mxcUri = upload(encryptedContentUrl)
                content.copy(
                    file = content.file?.copy(url = mxcUri),
                    format = content.format?.copy(thumbnailFile = thumbnailMxcUri?.let {
                        content.format?.thumbnailFile?.copy(
                            url = it
                        )
                    })
                )
            } else if (contentUrl != null) {
                val thumbnailMxcUri = content.format?.thumbnailUrl?.let { upload(it) }
                val mxcUri = upload(contentUrl)
                content.copy(
                    url = mxcUri,
                    format = content.format?.copy(thumbnailUrl = thumbnailMxcUri)
                )
            } else content
        }
        is RoomMessageEventContent.VideoMessageEventContent -> {
            val encryptedContentUrl = content.file?.url
            val contentUrl = content.url
            if (encryptedContentUrl != null) {
                val thumbnailMxcUri = content.format?.thumbnailFile?.url?.let { upload(it) }
                val mxcUri = upload(encryptedContentUrl)
                content.copy(
                    file = content.file?.copy(url = mxcUri),
                    format = content.format?.copy(thumbnailFile = thumbnailMxcUri?.let {
                        content.format?.thumbnailFile?.copy(
                            url = it
                        )
                    })
                )
            } else if (contentUrl != null) {
                val thumbnailMxcUri = content.format?.thumbnailUrl?.let { upload(it) }
                val mxcUri = upload(contentUrl)
                content.copy(
                    url = mxcUri,
                    format = content.format?.copy(thumbnailUrl = thumbnailMxcUri)
                )
            } else content
        }
        is RoomMessageEventContent.AudioMessageEventContent -> {
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
        is RoomMessageEventContent.UnknownMessageEventContent -> content
    }
}

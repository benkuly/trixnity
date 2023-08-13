package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.*

// TODO test

suspend fun fileMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String) -> String
): RoomMessageEventContent {
    require(content is FileMessageEventContent)
    val encryptedContentUrl = content.file?.url
    val contentUrl = content.url
    return if (encryptedContentUrl != null) {
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

suspend fun imageMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String) -> String
): RoomMessageEventContent {
    require(content is ImageMessageEventContent)
    val encryptedContentUrl = content.file?.url
    val contentUrl = content.url
    return if (encryptedContentUrl != null) {
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

suspend fun videoMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String) -> String
): RoomMessageEventContent {
    require(content is VideoMessageEventContent)
    val encryptedContentUrl = content.file?.url
    val contentUrl = content.url
    return if (encryptedContentUrl != null) {
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

suspend fun audioMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String) -> String
): RoomMessageEventContent {
    require(content is AudioMessageEventContent)
    val encryptedContentUrl = content.file?.url
    val contentUrl = content.url
    return if (encryptedContentUrl != null) {
        val mxcUri = upload(encryptedContentUrl)
        content.copy(file = content.file?.copy(url = mxcUri))
    } else if (contentUrl != null) {
        val mxcUri = upload(contentUrl)
        content.copy(url = mxcUri)
    } else content
}
package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

// TODO test



suspend fun fileRoomMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String, thumbnailUploaded: Boolean) -> String
): RoomMessageEventContent {
    require(content is RoomMessageEventContent.FileBased.File)
    val encryptedContentUrl = content.file?.url
    val contentUrl = content.url
    return if (encryptedContentUrl != null) {
        val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it, false) }
        val mxcUri = upload(encryptedContentUrl, true)
        content.copy(
            file = content.file?.copy(url = mxcUri),
            info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                content.info?.thumbnailFile?.copy(
                    url = it
                )
            })
        )
    } else if (contentUrl != null) {
        val thumbnailMxcUri = content.info?.thumbnailUrl?.let { upload(it, false) }
        val mxcUri = upload(contentUrl, true)
        content.copy(
            url = mxcUri,
            info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
        )
    } else content
}

suspend fun imageRoomMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String, thumbnailUploaded: Boolean) -> String
): RoomMessageEventContent {
    require(content is RoomMessageEventContent.FileBased.Image)
    val encryptedContentUrl = content.file?.url
    val contentUrl = content.url
    return if (encryptedContentUrl != null) {
        val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it, false) }
        val mxcUri = upload(encryptedContentUrl, true)
        content.copy(
            file = content.file?.copy(url = mxcUri),
            info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                content.info?.thumbnailFile?.copy(
                    url = it
                )
            })
        )
    } else if (contentUrl != null) {
        val thumbnailMxcUri = content.info?.thumbnailUrl?.let { upload(it, false) }
        val mxcUri = upload(contentUrl, true)
        content.copy(
            url = mxcUri,
            info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
        )
    } else content
}

suspend fun videoRoomMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String, thumbnailUploaded: Boolean) -> String
): RoomMessageEventContent {
    require(content is RoomMessageEventContent.FileBased.Video)
    val encryptedContentUrl = content.file?.url
    val contentUrl = content.url
    return if (encryptedContentUrl != null) {
        val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it, false) }
        val mxcUri = upload(encryptedContentUrl, true)
        content.copy(
            file = content.file?.copy(url = mxcUri),
            info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                content.info?.thumbnailFile?.copy(
                    url = it
                )
            })
        )
    } else if (contentUrl != null) {
        val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let { upload(it, false) }
        val mxcUri = upload(contentUrl, true)
        content.copy(
            url = mxcUri,
            info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
        )
    } else content
}

suspend fun audioRoomMessageEventContentMediaUploader(
    content: MessageEventContent,
    upload: suspend (cacheUri: String, thumbnailUploaded: Boolean) -> String
): RoomMessageEventContent {
    require(content is RoomMessageEventContent.FileBased.Audio)
    val encryptedContentUrl = content.file?.url
    val contentUrl = content.url
    return if (encryptedContentUrl != null) {
        val mxcUri = upload(encryptedContentUrl, false)
        content.copy(file = content.file?.copy(url = mxcUri))
    } else if (contentUrl != null) {
        val mxcUri = upload(contentUrl, false)
        content.copy(url = mxcUri)
    } else content
}
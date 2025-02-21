package net.folivo.trixnity.client.room.outbox

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

// TODO test

class FileMessageEventContentMediaUploader() : MessageEventContentMediaUploader {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): MessageEventContent = coroutineScope {
        require(content is RoomMessageEventContent.FileBased.File)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val combinedUploadProgress = CombinedFileTransferProgress()
        val thumbnailUploadProgress = combinedUploadProgress.acquire()
        thumbnailUploadProgress.value =
            if (content.info?.thumbnailFile != null || content.info?.thumbnailUrl != null) FileTransferProgress(
                0,
                content.info?.thumbnailInfo?.size
            ) else null
        val fileUploadProgress = combinedUploadProgress.acquire()
        fileUploadProgress.value = FileTransferProgress(0, content.info?.size)
        val updateProgress = launch {
            combinedUploadProgress.collect(uploadProgress)
        }

        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                    content.info?.thumbnailFile?.copy(
                        url = it
                    )
                })
            )
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailUrl?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(contentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            )
        } else content
    }

}

class ImageMessageEventContentMediaUploader() : MessageEventContentMediaUploader {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): MessageEventContent = coroutineScope {
        require(content is RoomMessageEventContent.FileBased.Image)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val combinedUploadProgress = CombinedFileTransferProgress()
        val updateProgress = launch {
            combinedUploadProgress.collect(uploadProgress)
        }
        val thumbnailUploadProgress = combinedUploadProgress.acquire()
        thumbnailUploadProgress.value =
            if (content.info?.thumbnailFile != null || content.info?.thumbnailUrl != null) FileTransferProgress(
                0,
                content.info?.thumbnailInfo?.size
            ) else null
        val fileUploadProgress = combinedUploadProgress.acquire()
        fileUploadProgress.value = FileTransferProgress(0, content.info?.size)
        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                    content.info?.thumbnailFile?.copy(
                        url = it
                    )
                })
            )
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailUrl?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(contentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            )
        } else content
    }
}

class VideoMessageEventContentMediaUploader() : MessageEventContentMediaUploader {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): MessageEventContent = coroutineScope {
        require(content is RoomMessageEventContent.FileBased.Video)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val combinedUploadProgress = CombinedFileTransferProgress()
        val thumbnailUploadProgress = combinedUploadProgress.acquire()
        thumbnailUploadProgress.value =
            if (content.info?.thumbnailFile != null || content.info?.thumbnailUrl != null) FileTransferProgress(
                0,
                content.info?.thumbnailInfo?.size
            ) else null
        val fileUploadProgress = combinedUploadProgress.acquire()
        fileUploadProgress.value = FileTransferProgress(0, content.info?.size)
        val updateProgress = launch {
            combinedUploadProgress.collect(uploadProgress)
        }
        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                    content.info?.thumbnailFile?.copy(
                        url = it
                    )
                })
            )
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailUrl?.let {
                upload(it, thumbnailUploadProgress)
            }
            val mxcUri = upload(contentUrl, fileUploadProgress)
            updateProgress.cancel()
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            )
        } else content
    }

}

class AudioMessageEventContentMediaUploader() : MessageEventContentMediaUploader {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): MessageEventContent {
        require(content is RoomMessageEventContent.FileBased.Audio)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        return if (encryptedContentUrl != null) {
            val mxcUri = upload(encryptedContentUrl, uploadProgress)
            content.copy(file = content.file?.copy(url = mxcUri))
        } else if (contentUrl != null) {
            val mxcUri = upload(contentUrl, uploadProgress)
            content.copy(url = mxcUri)
        } else content
    }
}
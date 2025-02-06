package net.folivo.trixnity.client.room.outbox

import korlibs.io.async.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

// TODO test

class FileRoomMessageEventContentMediaUploader() : RoomMessageEventContentMediaUploader {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent = coroutineScope {
        require(content is RoomMessageEventContent.FileBased.File)
        val maxSize = (content.info?.size ?: 0) + (content.info?.thumbnailInfo?.size ?: 0)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val thumbnailUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
        val fileUploadProgress = MutableStateFlow<FileTransferProgress?>(null)

        val uploadThumbnailJob = launch {
            thumbnailUploadProgress.map {
                FileTransferProgress(it?.transferred ?: 0, maxSize)
            }.collectLatest { uploadProgress.value = it }
        }

        val uploadJob = launch {
            fileUploadProgress.map {
                FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
            }.collectLatest { uploadProgress.value = it }
        }

        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            uploadThumbnailJob.cancel()
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            uploadJob.cancel()
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                    content.info?.thumbnailFile?.copy(
                        url = it
                    )
                })
            )
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            uploadThumbnailJob.cancel()
            val mxcUri = upload(contentUrl, fileUploadProgress)
            uploadJob.cancel()
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            )
        } else content
    }

}

class ImageRoomMessageEventContentMediaUploader() : RoomMessageEventContentMediaUploader {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent = coroutineScope {
        require(content is RoomMessageEventContent.FileBased.Image)
        val maxSize = (content.info?.size ?: 0) + (content.info?.thumbnailInfo?.size ?: 0)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val thumbnailUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
        val fileUploadProgress = MutableStateFlow<FileTransferProgress?>(null)

        val uploadThumbnailJob = launch {
            thumbnailUploadProgress.map {
                FileTransferProgress(it?.transferred ?: 0, maxSize)
            }.collectLatest { uploadProgress.value = it }
        }
        val uploadJob = launch {
            fileUploadProgress.map {
                FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
            }.collectLatest { uploadProgress.value = it }
        }
        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            uploadThumbnailJob.cancel()
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            uploadJob.cancel()
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                    content.info?.thumbnailFile?.copy(
                        url = it
                    )
                })
            )
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            uploadThumbnailJob.cancel()
            val mxcUri = upload(contentUrl, fileUploadProgress)
            uploadJob.cancel()
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            )
        } else content
    }
}

class VideoRoomMessageEventContentMediaUploader() : RoomMessageEventContentMediaUploader {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent = coroutineScope {
        require(content is RoomMessageEventContent.FileBased.Video)
        val maxSize = (content.info?.size ?: 0) + (content.info?.thumbnailInfo?.size ?: 0)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val thumbnailUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
        val fileUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
        val uploadThumbnailJob = launch {
            thumbnailUploadProgress.map {
                FileTransferProgress(it?.transferred ?: 0, maxSize)
            }.collectLatest { uploadProgress.value = it }
        }
        val uploadJob = launch {
            fileUploadProgress.map {
                FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
            }.collectLatest { uploadProgress.value = it }
        }
        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            uploadThumbnailJob.cancel()
            val mxcUri = upload(encryptedContentUrl, fileUploadProgress)
            uploadJob.cancel()
            content.copy(
                file = content.file?.copy(url = mxcUri),
                info = content.info?.copy(thumbnailFile = thumbnailMxcUri?.let {
                    content.info?.thumbnailFile?.copy(
                        url = it
                    )
                })
            )
        } else if (contentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                upload(it, thumbnailUploadProgress)
            }
            uploadThumbnailJob.cancel()
            val mxcUri = upload(contentUrl, fileUploadProgress)
            uploadJob.cancel()
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            )
        } else content
    }

}

class AudioRoomMessageEventContentMediaUploader() : RoomMessageEventContentMediaUploader {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent {
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
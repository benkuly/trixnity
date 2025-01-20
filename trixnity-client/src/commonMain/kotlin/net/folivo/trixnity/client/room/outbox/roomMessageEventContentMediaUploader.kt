package net.folivo.trixnity.client.room.outbox

import korlibs.io.async.async
import korlibs.io.async.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.selects.select
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

// TODO test

interface RoomMessageEventContentMediaUploader {
    val uploadProgress: MutableStateFlow<FileTransferProgress?>
    suspend fun uploader(
        content: MessageEventContent,
        upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent
}

class FileRoomMessageEventContentMediaUploader() : RoomMessageEventContentMediaUploader {
    override val uploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow<FileTransferProgress?>(null)

    override suspend fun uploader(
        content: MessageEventContent,
        upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent = coroutineScope {
        require(content is RoomMessageEventContent.FileBased.File)
        val maxSize = (content.info?.size ?: 0) + (content.info?.thumbnailInfo?.size ?: 0)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val thumbnailUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
        val fileUploadProgress = MutableStateFlow<FileTransferProgress?>(null)

        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                select {
                    async { upload(it, thumbnailUploadProgress) }.onAwait { it }
                    async {
                        thumbnailUploadProgress.map {
                            FileTransferProgress(it?.transferred ?: 0, maxSize)
                        }.collectLatest { uploadProgress.value = it }
                    }
                }
            }
            val mxcUri = select {
                async { upload(encryptedContentUrl, fileUploadProgress) }.onAwait { it }
                async {
                    fileUploadProgress.map {
                        FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
                    }.collectLatest { uploadProgress.value = it }
                }
            }.also { coroutineContext.cancelChildren() }
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
                select {
                    async { upload(it, thumbnailUploadProgress) }.onAwait { it }
                    async {
                        thumbnailUploadProgress.map {
                            FileTransferProgress(it?.transferred ?: 0, maxSize)
                        }.collectLatest { uploadProgress.value = it }
                    }
                }
            }
            val mxcUri = select {
                async { upload(contentUrl, fileUploadProgress) }.onAwait { it }
                async {
                    fileUploadProgress.map {
                        FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
                    }.collectLatest { uploadProgress.value = it }
                }
            }.also { coroutineContext.cancelChildren() }
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            )
        } else content
    }
}

class ImageRoomMessageEventContentMediaUploader() : RoomMessageEventContentMediaUploader {
    override val uploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow<FileTransferProgress?>(null)


    override suspend fun uploader(
        content: MessageEventContent,
        upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent = coroutineScope {
        require(content is RoomMessageEventContent.FileBased.Image)
        val maxSize = (content.info?.size ?: 0) + (content.info?.thumbnailInfo?.size ?: 0)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val thumbnailUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
        val fileUploadProgress = MutableStateFlow<FileTransferProgress?>(null)

        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                select {
                    async { upload(it, thumbnailUploadProgress) }.onAwait { it }
                    async {
                        thumbnailUploadProgress.map {
                            FileTransferProgress(it?.transferred ?: 0, maxSize)
                        }.collectLatest { uploadProgress.value = it }
                    }
                }
            }
            val mxcUri = select {
                async { upload(encryptedContentUrl, fileUploadProgress) }.onAwait { it }
                async {
                    fileUploadProgress.map {
                        FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
                    }.collectLatest { uploadProgress.value = it }
                }
            }.also { coroutineContext.cancelChildren() }
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
                select {
                    async { upload(it, thumbnailUploadProgress) }.onAwait { it }
                    async {
                        thumbnailUploadProgress.map {
                            FileTransferProgress(it?.transferred ?: 0, maxSize)
                        }.collectLatest { uploadProgress.value = it }
                    }
                }.also { coroutineContext.cancelChildren() }
            }
            val mxcUri = select {
                async { upload(contentUrl, fileUploadProgress) }.onAwait { it }
                async {
                    fileUploadProgress.map {
                        FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
                    }.collectLatest { uploadProgress.value = it }
                }
            }
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            )
        } else content
    }
}

class VideoRoomMessageEventContentMediaUploader() : RoomMessageEventContentMediaUploader {
    override val uploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow<FileTransferProgress?>(null)

    override suspend fun uploader(
        content: MessageEventContent,
        upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent = coroutineScope {
        require(content is RoomMessageEventContent.FileBased.Video)
        val maxSize = (content.info?.size ?: 0) + (content.info?.thumbnailInfo?.size ?: 0)
        val encryptedContentUrl = content.file?.url
        val contentUrl = content.url
        val thumbnailUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
        val fileUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
        launch {
            thumbnailUploadProgress.map {
                FileTransferProgress(it?.transferred ?: 0, maxSize)
            }.collectLatest { uploadProgress.value = it }
        }
        launch {
            fileUploadProgress.map {
                FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
            }.collectLatest { uploadProgress.value = it }
        }
        return@coroutineScope if (encryptedContentUrl != null) {
            val thumbnailMxcUri = content.info?.thumbnailFile?.url?.let {
                select {
                    async { upload(it, thumbnailUploadProgress) }.onAwait { it }
                    async {
                        thumbnailUploadProgress.map {
                            FileTransferProgress(it?.transferred ?: 0, maxSize)
                        }.collectLatest { uploadProgress.value = it }
                    }
                }
            }
            val mxcUri = select {
                async { upload(encryptedContentUrl, fileUploadProgress) }.onAwait { it }
                async {
                    fileUploadProgress.map {
                        FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
                    }.collectLatest { uploadProgress.value = it }
                }
            }.also { coroutineContext.cancelChildren() }
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
                select {
                    async { upload(it, thumbnailUploadProgress) }.onAwait { it }
                    async {
                        thumbnailUploadProgress.map {
                            FileTransferProgress(it?.transferred ?: 0, maxSize)
                        }.collectLatest { uploadProgress.value = it }
                    }
                }
            }
            val mxcUri = select {
                async { upload(contentUrl, fileUploadProgress) }.onAwait { it }
                async {
                    fileUploadProgress.map {
                        FileTransferProgress((content.info?.thumbnailInfo?.size ?: 0) + (it?.transferred ?: 0), maxSize)
                    }.collectLatest { uploadProgress.value = it }
                }
            }.also { coroutineContext.cancelChildren() }
            content.copy(
                url = mxcUri,
                info = content.info?.copy(thumbnailUrl = thumbnailMxcUri)
            )
        } else content
    }

}

class AudioRoomMessageEventContentMediaUploader() : RoomMessageEventContentMediaUploader {
    override val uploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow<FileTransferProgress?>(null)

    override suspend fun uploader(
        content: MessageEventContent,
        upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
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
package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.media.PlatformMedia
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import de.connect2x.trixnity.utils.ByteArrayFlow
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MediaServiceMock : MediaService {
    override suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<PlatformMedia> {
        throw NotImplementedError()
    }

    override suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<PlatformMedia> {
        throw NotImplementedError()
    }

    override suspend fun getThumbnail(
        uri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        animated: Boolean,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<PlatformMedia> {
        throw NotImplementedError()
    }

    val returnPrepareUploadMedia: MutableList<String> = mutableListOf()
    override suspend fun prepareUploadMedia(content: ByteArrayFlow, contentType: ContentType?): String {
        return returnPrepareUploadMedia.removeFirst()
    }

    val returnPrepareUploadEncryptedMedia: MutableList<EncryptedFile> = mutableListOf()
    override suspend fun prepareUploadEncryptedMedia(content: ByteArrayFlow): EncryptedFile {
        return returnPrepareUploadEncryptedMedia.removeFirst()
    }

    var returnUploadMedia: Result<String> = Result.success("")
    val uploadMediaCalled = MutableStateFlow<String?>(null)
    val uploadTimer = MutableStateFlow<Long>(0)
    val uploadSizes = MutableStateFlow<ArrayList<Long>?>(null)
    var currentUploadSize = 0
    override suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        keepMediaInCache: Boolean
    ): Result<String> {
        uploadMediaCalled.value = cacheUri
        val currentSize = uploadSizes.value?.getOrNull(currentUploadSize)
        progress?.value = FileTransferProgress(0, currentSize)
        delay(uploadTimer.value)
        progress?.value = FileTransferProgress(currentSize ?: 0, currentSize)
        delay(uploadTimer.value)
        currentUploadSize++
        return returnUploadMedia
    }
}
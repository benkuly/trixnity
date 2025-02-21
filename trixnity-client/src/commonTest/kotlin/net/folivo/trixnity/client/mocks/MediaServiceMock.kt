package net.folivo.trixnity.client.mocks

import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.media.PlatformMedia
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.utils.ByteArrayFlow

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
    override suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        keepMediaInCache: Boolean
    ): Result<String> {
        uploadMediaCalled.value = cacheUri
        delay(uploadTimer.value)
        return returnUploadMedia
    }
}
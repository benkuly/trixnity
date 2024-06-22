package net.folivo.trixnity.client.mocks

import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ThumbnailInfo
import net.folivo.trixnity.utils.ByteArrayFlow

class MediaServiceMock : MediaService {
    override suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<ByteArrayFlow> {
        throw NotImplementedError()
    }

    override suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<ByteArrayFlow> {
        throw NotImplementedError()
    }

    override suspend fun getThumbnail(
        uri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<ByteArrayFlow> {
        throw NotImplementedError()
    }

    lateinit var returnPrepareUploadMedia: String
    override suspend fun prepareUploadMedia(content: ByteArrayFlow, contentType: ContentType?): String {
        return returnPrepareUploadMedia
    }

    var returnPrepareUploadThumbnail: Pair<String, ThumbnailInfo>? = null
    override suspend fun prepareUploadThumbnail(
        content: ByteArrayFlow,
        contentType: ContentType?
    ): Pair<String, ThumbnailInfo>? {
        return returnPrepareUploadThumbnail
    }

    lateinit var returnPrepareUploadEncryptedMedia: EncryptedFile
    override suspend fun prepareUploadEncryptedMedia(content: ByteArrayFlow): EncryptedFile {
        return returnPrepareUploadEncryptedMedia
    }

    var returnPrepareUploadEncryptedThumbnail: Pair<EncryptedFile, ThumbnailInfo>? = null
    override suspend fun prepareUploadEncryptedThumbnail(
        content: ByteArrayFlow,
        contentType: ContentType?
    ): Pair<EncryptedFile, ThumbnailInfo>? {
        return returnPrepareUploadEncryptedThumbnail
    }

    var returnUploadMedia: Result<String> = Result.success("")
    val uploadMediaCalled = MutableStateFlow<String?>(null)
    override suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        keepMediaInCache: Boolean
    ): Result<String> {
        uploadMediaCalled.value = cacheUri
        return returnUploadMedia
    }
}
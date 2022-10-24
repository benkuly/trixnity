package net.folivo.trixnity.client.mocks

import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.core.ByteFlow
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ThumbnailInfo

class MediaServiceMock : MediaService {
    override suspend fun getMedia(
        uri: String,
        saveToCache: Boolean,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<ByteFlow> {
        throw NotImplementedError()
    }

    override suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        saveToCache: Boolean,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<ByteFlow> {
        throw NotImplementedError()
    }

    override suspend fun getThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        saveToCache: Boolean,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<ByteFlow> {
        throw NotImplementedError()
    }

    lateinit var returnPrepareUploadMedia: String
    override suspend fun prepareUploadMedia(content: ByteFlow, contentType: ContentType?): String {
        return returnPrepareUploadMedia
    }

    var returnPrepareUploadThumbnail: Pair<String, ThumbnailInfo>? = null
    override suspend fun prepareUploadThumbnail(
        content: ByteFlow,
        contentType: ContentType?
    ): Pair<String, ThumbnailInfo>? {
        return returnPrepareUploadThumbnail
    }

    lateinit var returnPrepareUploadEncryptedMedia: EncryptedFile
    override suspend fun prepareUploadEncryptedMedia(content: ByteFlow): EncryptedFile {
        return returnPrepareUploadEncryptedMedia
    }

    var returnPrepareUploadEncryptedThumbnail: Pair<EncryptedFile, ThumbnailInfo>? = null
    override suspend fun prepareUploadEncryptedThumbnail(
        content: ByteFlow,
        contentType: ContentType?
    ): Pair<EncryptedFile, ThumbnailInfo>? {
        return returnPrepareUploadEncryptedThumbnail
    }

    var returnUploadMedia: Result<String> = Result.success("")
    val uploadMediaCalled = MutableStateFlow<String?>(null)
    override suspend fun uploadMedia(
        cacheUri: String,
        keepMediaInCache: Boolean,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<String> {
        uploadMediaCalled.value = cacheUri
        return returnUploadMedia
    }
}
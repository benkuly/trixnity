package net.folivo.trixnity.client.mocks

import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.media.IMediaService
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ThumbnailInfo

class MediaServiceMock : IMediaService {
    override suspend fun getMedia(uri: String, progress: MutableStateFlow<FileTransferProgress?>?): Result<ByteArray> {
        throw NotImplementedError()
    }

    override suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<ByteArray> {
        throw NotImplementedError()
    }

    override suspend fun getThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<ByteArray> {
        throw NotImplementedError()
    }

    lateinit var returnPrepareUploadMedia: String
    override suspend fun prepareUploadMedia(content: ByteArray, contentType: ContentType): String {
        return returnPrepareUploadMedia
    }

    var returnPrepareUploadThumbnail: Pair<String, ThumbnailInfo>? = null
    override suspend fun prepareUploadThumbnail(
        content: ByteArray,
        contentType: ContentType
    ): Pair<String, ThumbnailInfo>? {
        return returnPrepareUploadThumbnail
    }

    lateinit var returnPrepareUploadEncryptedMedia: EncryptedFile
    override suspend fun prepareUploadEncryptedMedia(content: ByteArray): EncryptedFile {
        return returnPrepareUploadEncryptedMedia
    }

    var returnPrepareUploadEncryptedThumbnail: Pair<EncryptedFile, ThumbnailInfo>? = null
    override suspend fun prepareUploadEncryptedThumbnail(
        content: ByteArray,
        contentType: ContentType
    ): Pair<EncryptedFile, ThumbnailInfo>? {
        return returnPrepareUploadEncryptedThumbnail
    }

    override suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<String> {
        throw NotImplementedError()
    }
}
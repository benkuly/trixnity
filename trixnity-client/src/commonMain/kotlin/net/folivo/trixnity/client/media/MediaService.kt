package net.folivo.trixnity.client.media

import com.benasher44.uuid.uuid4
import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.media.FileTransferProgress
import net.folivo.trixnity.client.api.media.ThumbnailResizingMethod
import net.folivo.trixnity.client.api.media.ThumbnailResizingMethod.CROP
import net.folivo.trixnity.client.crypto.Aes256CtrInfo
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.crypto.decryptAes256Ctr
import net.folivo.trixnity.client.crypto.encryptAes256Ctr
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.UploadMedia
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ThumbnailInfo
import net.folivo.trixnity.olm.OlmUtility
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.olm.encodeUnpaddedBase64
import net.folivo.trixnity.olm.freeAfter
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class MediaService(
    private val api: MatrixApiClient,
    private val store: Store,
    loggerFactory: LoggerFactory
) {
    private val log = newLogger(loggerFactory)

    companion object {
        const val UPLOAD_MEDIA_CACHE_URI_PREFIX = "cache://"
        const val UPLOAD_MEDIA_MXC_URI_PREFIX = "mxc://"
    }

    suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): Result<ByteArray> = kotlin.runCatching {
        when {
            uri.startsWith(UPLOAD_MEDIA_MXC_URI_PREFIX) -> store.media.getContent(uri)
                ?: api.media.download(uri, progress = progress).getOrThrow().content.toByteArray()
                    .also { mediaDownload ->
                        store.media.addContent(uri, mediaDownload)
                    }
            uri.startsWith(UPLOAD_MEDIA_CACHE_URI_PREFIX) -> store.media.getContent(uri)
                ?: store.media.getUploadMedia(uri)?.mxcUri
                    ?.let { store.media.getContent(it) }
                ?: throw IllegalArgumentException("cache uri $uri does not exists")
            else -> throw IllegalArgumentException("uri $uri is no valid cache or mxc uri")
        }
    }

    suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): Result<ByteArray> = kotlin.runCatching {
        val media = getMedia(encryptedFile.url, progress).getOrThrow()
        val hash = freeAfter(OlmUtility.create()) {
            it.sha256(media)
        }
        val originalHash = encryptedFile.hashes["sha256"]
        if (originalHash == null || hash != originalHash) {
            log.debug { "could not validate due to different hashes. Our hash: $hash, their hash: $originalHash" }
            throw DecryptionException.ValidationFailed
        }
        decryptAes256Ctr(
            Aes256CtrInfo(
                encryptedContent = media,
                initialisationVector = encryptedFile.initialisationVector.decodeUnpaddedBase64Bytes(),
                // url-safe base64 is given
                key = encryptedFile.key.key.replace("-", "+").replace("_", "/")
                    .decodeUnpaddedBase64Bytes()
            )
        )
    }

    suspend fun getThumbnail(
        mxcUri: String,
        width: UInt,
        height: UInt,
        method: ThumbnailResizingMethod = CROP,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): Result<ByteArray> = kotlin.runCatching {
        val thumbnailUrl = "$mxcUri/${width}x$height/${method.value}"
        store.media.getContent(thumbnailUrl)
            ?: api.media.downloadThumbnail(mxcUri, width, height, method, progress = progress)
                .getOrThrow().content.toByteArray()
                .also { mediaDownload ->
                    store.media.addContent(thumbnailUrl, mediaDownload)
                }
    }

    suspend fun prepareUploadMedia(content: ByteArray, contentType: ContentType): String {
        return "$UPLOAD_MEDIA_CACHE_URI_PREFIX${uuid4()}".also { cacheUri ->
            store.media.addContent(cacheUri, content)
            store.media.updateUploadMedia(cacheUri) { UploadMedia(cacheUri, contentTyp = contentType) }
        }
    }

    suspend fun prepareUploadThumbnail(content: ByteArray, contentType: ContentType): Pair<String, ThumbnailInfo>? {
        val thumbnail = try {
            createThumbnail(content, contentType, 600, 600)
        } catch (e: ThumbnailCreationException) {
            log.warning(e) { "could not create thumbnail from file with content type $contentType" }
            return null
        }
        val cacheUri = prepareUploadMedia(thumbnail.file, thumbnail.contentType)
        return cacheUri to ThumbnailInfo(
            width = thumbnail.width,
            height = thumbnail.height,
            mimeType = thumbnail.contentType.toString(),
            size = thumbnail.file.size
        )
    }

    suspend fun prepareUploadEncryptedMedia(content: ByteArray): EncryptedFile {
        val encrypted = encryptAes256Ctr(content)
        val cacheUri = prepareUploadMedia(encrypted.encryptedContent, ContentType.Application.OctetStream)
        val hash = freeAfter(OlmUtility.create()) {
            it.sha256(encrypted.encryptedContent)
        }
        return EncryptedFile(
            url = cacheUri,
            key = EncryptedFile.JWK(
                // url-safe base64 is required
                key = encrypted.key.encodeUnpaddedBase64().replace("+", "-").replace("/", "_")
            ),
            initialisationVector = encrypted.initialisationVector.encodeUnpaddedBase64(),
            hashes = mapOf("sha256" to hash)
        )
    }

    suspend fun prepareUploadEncryptedThumbnail(
        content: ByteArray,
        contentType: ContentType
    ): Pair<EncryptedFile, ThumbnailInfo>? {
        val thumbnail = try {
            createThumbnail(content, contentType, 600, 600)
        } catch (e: ThumbnailCreationException) {
            log.debug { "could not create thumbnail from file with content type $contentType" }
            return null
        }
        val encryptedFile = prepareUploadEncryptedMedia(thumbnail.file)
        return encryptedFile to ThumbnailInfo(
            width = thumbnail.width,
            height = thumbnail.height,
            mimeType = thumbnail.contentType.toString(),
            size = thumbnail.file.size
        )
    }

    suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): Result<String> {
        if (!cacheUri.startsWith(UPLOAD_MEDIA_CACHE_URI_PREFIX)) throw IllegalArgumentException("$cacheUri is no cacheUri")

        val uploadMediaCache = store.media.getUploadMedia(cacheUri)
        val cachedMxcUri = uploadMediaCache?.mxcUri

        return if (cachedMxcUri == null) {
            val content =
                store.media.getContent(cacheUri)
                    ?: throw IllegalArgumentException("content for cacheUri $cacheUri not found")
            api.media.upload(
                content = ByteReadChannel(content),
                contentLength = content.size.toLong(),
                contentType = uploadMediaCache?.contentTyp ?: ContentType.Application.OctetStream,
                progress = progress
            ).map {
                it.contentUri.also { mxcUri ->
                    store.media.changeUri(cacheUri, mxcUri)
                    store.media.updateUploadMedia(cacheUri) { it?.copy(mxcUri = mxcUri) }
                }
            }
        } else Result.success(cachedMxcUri)
    }
}
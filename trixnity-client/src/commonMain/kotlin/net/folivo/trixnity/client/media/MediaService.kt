package net.folivo.trixnity.client.media

import com.benasher44.uuid.uuid4
import com.soywiz.krypto.SecureRandom
import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.crypto.decryptAes256Ctr
import net.folivo.trixnity.client.crypto.encryptAes256Ctr
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.UploadCache
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.clientserverapi.model.media.Media
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod.CROP
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ThumbnailInfo
import net.folivo.trixnity.olm.OlmUtility
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.olm.encodeUnpaddedBase64
import net.folivo.trixnity.olm.freeAfter

private val log = KotlinLogging.logger {}

interface IMediaService {
    suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): Result<ByteArray>

    suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): Result<ByteArray>

    suspend fun getThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod = CROP,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): Result<ByteArray>

    suspend fun prepareUploadMedia(content: ByteArray, contentType: ContentType): String

    suspend fun prepareUploadThumbnail(content: ByteArray, contentType: ContentType): Pair<String, ThumbnailInfo>?

    suspend fun prepareUploadEncryptedMedia(content: ByteArray): EncryptedFile

    suspend fun prepareUploadEncryptedThumbnail(
        content: ByteArray,
        contentType: ContentType
    ): Pair<EncryptedFile, ThumbnailInfo>?

    suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): Result<String>
}

class MediaService(
    private val api: MatrixClientServerApiClient,
    private val store: Store,
) : IMediaService {
    companion object {
        const val UPLOAD_MEDIA_CACHE_URI_PREFIX = "cache://"
        const val UPLOAD_MEDIA_MXC_URI_PREFIX = "mxc://"
    }

    override suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<ByteArray> = kotlin.runCatching {
        when {
            uri.startsWith(UPLOAD_MEDIA_MXC_URI_PREFIX) -> store.media.getContent(uri)
                ?: api.media.download(uri, progress = progress).getOrThrow().content.toByteArray()
                    .also { mediaDownload ->
                        store.media.addContent(uri, mediaDownload)
                    }
            uri.startsWith(UPLOAD_MEDIA_CACHE_URI_PREFIX) -> store.media.getContent(uri)
                ?: store.media.getUploadCache(uri)?.mxcUri
                    ?.let { store.media.getContent(it) }
                ?: throw IllegalArgumentException("cache uri $uri does not exists")
            else -> throw IllegalArgumentException("uri $uri is no valid cache or mxc uri")
        }
    }

    override suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>?
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
            encryptedContent = media,
            initialisationVector = encryptedFile.initialisationVector.decodeUnpaddedBase64Bytes(),
            // url-safe base64 is given
            key = encryptedFile.key.key.replace("-", "+").replace("_", "/")
                .decodeUnpaddedBase64Bytes()
        )
    }

    override suspend fun getThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<ByteArray> = kotlin.runCatching {
        val thumbnailUrl = "$mxcUri/${width}x$height/${api.json.encodeToJsonElement(method).jsonPrimitive.content}"
        store.media.getContent(thumbnailUrl)
            ?: api.media.downloadThumbnail(mxcUri, width, height, method, progress = progress)
                .getOrThrow().content.toByteArray()
                .also { mediaDownload ->
                    store.media.addContent(thumbnailUrl, mediaDownload)
                }
    }

    override suspend fun prepareUploadMedia(content: ByteArray, contentType: ContentType): String {
        return "$UPLOAD_MEDIA_CACHE_URI_PREFIX${uuid4()}".also { cacheUri ->
            store.media.addContent(cacheUri, content)
            store.media.updateUploadCache(cacheUri) { UploadCache(cacheUri, contentType = contentType.toString()) }
        }
    }

    override suspend fun prepareUploadThumbnail(
        content: ByteArray,
        contentType: ContentType
    ): Pair<String, ThumbnailInfo>? {
        val thumbnail = try {
            createThumbnail(content, 600, 600)
        } catch (e: Exception) {
            log.warn(e) { "could not create thumbnail from file with content type $contentType" }
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

    override suspend fun prepareUploadEncryptedMedia(content: ByteArray): EncryptedFile {
        val key = SecureRandom.nextBytes(32)
        val nonce = SecureRandom.nextBytes(8)
        val initialisationVector = nonce + ByteArray(8)
        val encrypted = encryptAes256Ctr(content = content, key = key, initialisationVector = initialisationVector)
        val cacheUri = prepareUploadMedia(encrypted, ContentType.Application.OctetStream)
        val hash = freeAfter(OlmUtility.create()) {
            it.sha256(encrypted)
        }
        return EncryptedFile(
            url = cacheUri,
            key = EncryptedFile.JWK(
                // url-safe base64 is required
                key = key.encodeUnpaddedBase64().replace("+", "-").replace("/", "_")
            ),
            initialisationVector = initialisationVector.encodeUnpaddedBase64(),
            hashes = mapOf("sha256" to hash)
        )
    }

    override suspend fun prepareUploadEncryptedThumbnail(
        content: ByteArray,
        contentType: ContentType
    ): Pair<EncryptedFile, ThumbnailInfo>? {
        val thumbnail = try {
            createThumbnail(content, 600, 600)
        } catch (e: Exception) {
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

    override suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>?
    ): Result<String> {
        if (!cacheUri.startsWith(UPLOAD_MEDIA_CACHE_URI_PREFIX)) throw IllegalArgumentException("$cacheUri is no cacheUri")

        val uploadMediaCache = store.media.getUploadCache(cacheUri)
        val cachedMxcUri = uploadMediaCache?.mxcUri

        return if (cachedMxcUri == null) {
            val content =
                store.media.getContent(cacheUri)
                    ?: throw IllegalArgumentException("content for cacheUri $cacheUri not found")
            api.media.upload(
                Media(
                    content = ByteReadChannel(content),
                    contentLength = content.size.toLong(),
                    contentType = uploadMediaCache?.contentType?.let { ContentType.parse(it) }
                        ?: ContentType.Application.OctetStream,
                    null
                ),
                progress = progress
            ).map { response ->
                response.contentUri.also { mxcUri ->
                    store.media.changeUri(cacheUri, mxcUri)
                    store.media.updateUploadCache(cacheUri) { it?.copy(mxcUri = mxcUri) }
                }
            }
        } else Result.success(cachedMxcUri)
    }
}
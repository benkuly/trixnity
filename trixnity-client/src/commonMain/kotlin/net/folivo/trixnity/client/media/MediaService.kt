package net.folivo.trixnity.client.media

import com.benasher44.uuid.uuid4
import com.soywiz.krypto.SecureRandom
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.MediaCacheMappingStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.clientserverapi.model.media.Media
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod.CROP
import net.folivo.trixnity.core.ByteFlow
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ThumbnailInfo
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteFlow
import net.folivo.trixnity.core.toByteReadChannel
import net.folivo.trixnity.crypto.decryptAes256Ctr
import net.folivo.trixnity.crypto.encryptAes256Ctr
import net.folivo.trixnity.crypto.olm.DecryptionException
import net.folivo.trixnity.crypto.sha256
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.olm.encodeUnpaddedBase64

private val log = KotlinLogging.logger {}

interface MediaService {
    suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        saveToCache: Boolean = true,
    ): Result<ByteFlow>

    suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        saveToCache: Boolean = true,
    ): Result<ByteFlow>

    suspend fun getThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod = CROP,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        saveToCache: Boolean = true,
    ): Result<ByteFlow>

    suspend fun prepareUploadMedia(content: ByteFlow, contentType: ContentType?): String

    suspend fun prepareUploadThumbnail(content: ByteFlow, contentType: ContentType?): Pair<String, ThumbnailInfo>?

    suspend fun prepareUploadEncryptedMedia(content: ByteFlow): EncryptedFile

    suspend fun prepareUploadEncryptedThumbnail(
        content: ByteFlow,
        contentType: ContentType?
    ): Pair<EncryptedFile, ThumbnailInfo>?

    suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        keepMediaInCache: Boolean = true
    ): Result<String>
}

class MediaServiceImpl(
    private val api: MatrixClientServerApiClient,
    private val mediaStore: MediaStore,
    private val mediaCacheMappingStore: MediaCacheMappingStore,
) : MediaService {
    companion object {
        const val UPLOAD_MEDIA_CACHE_URI_PREFIX = "upload://"
        const val UPLOAD_MEDIA_MXC_URI_PREFIX = "mxc://"
        const val maxFileSizeForThumbnail = 1024 * 50_000 // = 50MB
    }

    private suspend fun <T : ByteFlow> Result<Media>.saveMedia(
        uri: String,
        saveToCache: Boolean,
        transform: ByteFlow.() -> T
    ): ByteFlow {
        val media = getOrThrow().content.toByteFlow().transform()
        return if (saveToCache) {
            mediaStore.addMedia(uri, media)
            requireNotNull(mediaStore.getMedia(uri)) { "media should not be null. because it has just been saved" }
        } else media
    }

    private suspend fun getMedia(
        uri: String,
        saveToCache: Boolean,
        sha256Hash: String?,
        progress: MutableStateFlow<FileTransferProgress?>?,
    ): Result<ByteFlow> = kotlin.runCatching {
        when {
            uri.startsWith(UPLOAD_MEDIA_MXC_URI_PREFIX) -> {
                mediaStore.getMedia(uri)
                    ?: if (sha256Hash == null) api.media.download(uri, progress = progress)
                        .saveMedia(uri, saveToCache) { this }
                    else {
                        api.media.download(uri, progress = progress).saveMedia(uri, saveToCache) {
                            val sha256ByteFlow = sha256()
                            sha256ByteFlow.onCompletion {
                                if (sha256ByteFlow.hash.value != sha256Hash) {
                                    mediaStore.deleteMedia(uri)
                                    throw DecryptionException.ValidationFailed("could not validate media due to different hashes. Our hash: ${sha256ByteFlow.hash.value}, their hash: $sha256Hash")
                                }
                            }
                        }
                    }
            }

            uri.startsWith(UPLOAD_MEDIA_CACHE_URI_PREFIX) -> mediaStore.getMedia(uri)
                ?: mediaCacheMappingStore.getMediaCacheMapping(uri)?.mxcUri
                    ?.let { mediaStore.getMedia(it) }
                ?: throw IllegalArgumentException("cache uri $uri does not exists")

            else -> throw IllegalArgumentException("uri $uri is no valid cache or mxc uri")
        }
    }

    override suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<ByteFlow> =
        getMedia(uri, saveToCache, null, progress)

    override suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<ByteFlow> = kotlin.runCatching {
        val originalHash = encryptedFile.hashes["sha256"]
            ?: throw DecryptionException.ValidationFailed("missing hash for media")
        val media = getMedia(encryptedFile.url, saveToCache, originalHash, progress).getOrThrow()
        media.decryptAes256Ctr(
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
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<ByteFlow> = kotlin.runCatching {
        val thumbnailUrl = "$mxcUri/${width}x$height/${api.json.encodeToJsonElement(method).jsonPrimitive.content}"
        mediaStore.getMedia(thumbnailUrl)
            ?: api.media.downloadThumbnail(mxcUri, width, height, method, progress = progress)
                .saveMedia(thumbnailUrl, saveToCache) { this }
    }

    override suspend fun prepareUploadMedia(content: ByteFlow, contentType: ContentType?): String {
        return "$UPLOAD_MEDIA_CACHE_URI_PREFIX${uuid4()}".also { cacheUri ->
            var fileSize = 0
            mediaStore.addMedia(cacheUri, content.onEach { fileSize++ })
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
                MediaCacheMapping(cacheUri, size = fileSize, contentType = contentType.toString())
            }
        }
    }

    override suspend fun prepareUploadThumbnail(
        content: ByteFlow,
        contentType: ContentType?
    ): Pair<String, ThumbnailInfo>? {
        val thumbnail = try {
            createThumbnail(content.take(maxFileSizeForThumbnail).toByteArray(), 600, 600)
        } catch (e: Exception) {
            log.warn(e) { "could not create thumbnail from file with content type $contentType" }
            return null
        }
        val cacheUri = prepareUploadMedia(thumbnail.file.toByteFlow(), thumbnail.contentType)
        return cacheUri to ThumbnailInfo(
            width = thumbnail.width,
            height = thumbnail.height,
            mimeType = thumbnail.contentType.toString(),
            size = thumbnail.file.size
        )
    }

    override suspend fun prepareUploadEncryptedMedia(content: ByteFlow): EncryptedFile {
        val key = SecureRandom.nextBytes(32)
        val nonce = SecureRandom.nextBytes(8)
        val initialisationVector = nonce + ByteArray(8)
        val encrypted =
            content.encryptAes256Ctr(key = key, initialisationVector = initialisationVector).sha256()
        val cacheUri = prepareUploadMedia(encrypted, ContentType.Application.OctetStream)

        val hash = requireNotNull(encrypted.hash.value) { "hash was null" }

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
        content: ByteFlow,
        contentType: ContentType?
    ): Pair<EncryptedFile, ThumbnailInfo>? {
        val thumbnail = try {
            createThumbnail(content.take(maxFileSizeForThumbnail).toByteArray(), 600, 600)
        } catch (e: Exception) {
            log.debug { "could not create thumbnail from file with content type $contentType" }
            return null
        }
        val encryptedFile = prepareUploadEncryptedMedia(thumbnail.file.toByteFlow())
        return encryptedFile to ThumbnailInfo(
            width = thumbnail.width,
            height = thumbnail.height,
            mimeType = thumbnail.contentType.toString(),
            size = thumbnail.file.size
        )
    }

    override suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        keepMediaInCache: Boolean
    ): Result<String> {
        if (!cacheUri.startsWith(UPLOAD_MEDIA_CACHE_URI_PREFIX)) throw IllegalArgumentException("$cacheUri is no cacheUri")

        val uploadMediaCache = requireNotNull(mediaCacheMappingStore.getMediaCacheMapping(cacheUri))
        val cachedMxcUri = uploadMediaCache.mxcUri

        return if (cachedMxcUri == null) {
            val content =
                mediaStore.getMedia(cacheUri)
                    ?: throw IllegalArgumentException("content for cacheUri $cacheUri not found")
            api.media.upload(
                Media(
                    content = content.toByteReadChannel(),
                    contentLength = uploadMediaCache.size?.toLong(),
                    contentType = uploadMediaCache.contentType?.let { ContentType.parse(it) }
                        ?: ContentType.Application.OctetStream,
                    null
                ),
                progress = progress
            ).map { response ->
                response.contentUri.also { mxcUri ->
                    if (keepMediaInCache) {
                        mediaStore.changeMediaUrl(cacheUri, mxcUri)
                        mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) { it?.copy(mxcUri = mxcUri) }
                    } else {
                        mediaStore.deleteMedia(cacheUri)
                        mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) { null }
                    }
                }
            }
        } else Result.success(cachedMxcUri)
    }
}
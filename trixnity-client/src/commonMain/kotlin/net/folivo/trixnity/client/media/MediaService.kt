package net.folivo.trixnity.client.media

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.MediaCacheMappingStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.clientserverapi.model.media.Media
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod.CROP
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ThumbnailInfo
import net.folivo.trixnity.crypto.core.SecureRandom
import net.folivo.trixnity.crypto.core.decryptAes256Ctr
import net.folivo.trixnity.crypto.core.encryptAes256Ctr
import net.folivo.trixnity.crypto.core.sha256
import net.folivo.trixnity.utils.*

private val log = KotlinLogging.logger {}

interface MediaService {
    suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        saveToCache: Boolean = true,
    ): Result<ByteArrayFlow>

    suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        saveToCache: Boolean = true,
    ): Result<ByteArrayFlow>

    suspend fun getThumbnail(
        uri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod = CROP,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        saveToCache: Boolean = true,
    ): Result<ByteArrayFlow>

    suspend fun prepareUploadMedia(content: ByteArrayFlow, contentType: ContentType?): String

    suspend fun prepareUploadThumbnail(content: ByteArrayFlow, contentType: ContentType?): Pair<String, ThumbnailInfo>?

    suspend fun prepareUploadEncryptedMedia(content: ByteArrayFlow): EncryptedFile

    suspend fun prepareUploadEncryptedThumbnail(
        content: ByteArrayFlow,
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

    private suspend fun <T : ByteArrayFlow> Media.saveMedia(
        uri: String,
        transform: ByteArrayFlow.() -> T
    ) {
        val media = content.toByteArrayFlow().transform()
        log.debug { "save media to store: $uri" }
        mediaStore.addMedia(uri, media)
        log.debug { "completed save media to store: $uri" }
    }

    private suspend fun getMedia(
        uri: String,
        saveToCache: Boolean,
        sha256Hash: String?,
        progress: MutableStateFlow<FileTransferProgress?>?,
    ): Result<ByteArrayFlow> = kotlin.runCatching {
        when {
            uri.startsWith(UPLOAD_MEDIA_MXC_URI_PREFIX) -> {
                val existingMedia = mediaStore.getMedia(uri)
                if (existingMedia == null) {
                    log.debug { "download media: $uri" }
                    if (sha256Hash == null) {
                        api.media.download(uri, progress = progress) {
                            it.saveMedia(uri) { this }
                        }.getOrThrow()
                    } else {
                        api.media.download(uri, progress = progress) {
                            it.saveMedia(uri) {
                                val sha256ByteFlow = sha256()
                                sha256ByteFlow.onCompletion {
                                    val expectedHash = sha256ByteFlow.hash.value
                                    if (expectedHash != sha256Hash) {
                                        mediaStore.deleteMedia(uri)
                                        throw MediaValidationException(expectedHash, sha256Hash)
                                    }
                                }
                            }
                        }.getOrThrow()
                    }
                    requireNotNull(mediaStore.getMedia(uri)) { "media should not be null, because it has just been saved" }
                        .onCompletion { if (!saveToCache) mediaStore.deleteMedia(uri) }
                } else {
                    log.debug { "found media in store: $uri" }
                    existingMedia
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
    ): Result<ByteArrayFlow> =
        getMedia(uri, saveToCache, null, progress)

    override suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<ByteArrayFlow> = kotlin.runCatching {
        val originalHash = encryptedFile.hashes["sha256"]
            ?: throw MediaValidationException(null, null)
        val media = getMedia(encryptedFile.url, saveToCache, originalHash, progress).getOrThrow()
        media.decryptAes256Ctr(
            initialisationVector = encryptedFile.initialisationVector.decodeUnpaddedBase64Bytes(),
            // url-safe base64 is given
            key = encryptedFile.key.key.replace("-", "+").replace("_", "/")
                .decodeUnpaddedBase64Bytes()
        )
    }

    override suspend fun getThumbnail(
        uri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<ByteArrayFlow> = kotlin.runCatching {
        val thumbnailUrl = "$uri/${width}x$height/${api.json.encodeToJsonElement(method).jsonPrimitive.content}"
        val existingMedia = mediaStore.getMedia(thumbnailUrl)
        if (existingMedia == null) {
            api.media.downloadThumbnail(uri, width, height, method, progress = progress) {
                it.saveMedia(thumbnailUrl) { this }
            }.getOrThrow()
            requireNotNull(mediaStore.getMedia(thumbnailUrl)) { "media should not be null, because it has just been saved" }
                .onCompletion { if (!saveToCache) mediaStore.deleteMedia(thumbnailUrl) }
        } else existingMedia
    }

    override suspend fun prepareUploadMedia(content: ByteArrayFlow, contentType: ContentType?): String {
        return "$UPLOAD_MEDIA_CACHE_URI_PREFIX${SecureRandom.nextString(22)}".also { cacheUri ->
            var fileSize = 0
            mediaStore.addMedia(cacheUri, content.onEach { fileSize += it.size })
            mediaCacheMappingStore.saveMediaCacheMapping(
                cacheUri,
                MediaCacheMapping(cacheUri, size = fileSize, contentType = contentType.toString())
            )
        }
    }

    override suspend fun prepareUploadThumbnail(
        content: ByteArrayFlow,
        contentType: ContentType?
    ): Pair<String, ThumbnailInfo>? {
        val thumbnail =
            if (contentType?.contentType == "image") try {
                createThumbnail(content.takeBytes(maxFileSizeForThumbnail).toByteArray(), 600, 600)
            } catch (e: Exception) {
                log.warn(e) { "could not create thumbnail from file with content type $contentType" }
                return null
            }
            else return null
        val cacheUri = prepareUploadMedia(thumbnail.file.toByteArrayFlow(), thumbnail.contentType)
        return cacheUri to ThumbnailInfo(
            width = thumbnail.width,
            height = thumbnail.height,
            mimeType = thumbnail.contentType.toString(),
            size = thumbnail.file.size
        )
    }

    override suspend fun prepareUploadEncryptedMedia(content: ByteArrayFlow): EncryptedFile {
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
        content: ByteArrayFlow,
        contentType: ContentType?
    ): Pair<EncryptedFile, ThumbnailInfo>? {
        val thumbnail =
            if (contentType?.contentType == "image") try {
                createThumbnail(content.takeBytes(maxFileSizeForThumbnail).toByteArray(), 600, 600)
            } catch (e: Exception) {
                log.debug { "could not create thumbnail from file with content type $contentType" }
                return null
            }
            else return null
        val encryptedFile = prepareUploadEncryptedMedia(thumbnail.file.toByteArrayFlow())
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
                        mediaCacheMappingStore.deleteMediaCacheMapping(cacheUri)
                    }
                }
            }
        } else Result.success(cachedMxcUri)
    }
}
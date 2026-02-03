package de.connect2x.trixnity.client.media

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.store.MediaCacheMapping
import de.connect2x.trixnity.client.store.MediaCacheMappingStore
import de.connect2x.trixnity.client.store.ServerDataStore
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.MediaApiClient
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.clientserverapi.model.media.Media
import de.connect2x.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import de.connect2x.trixnity.clientserverapi.model.media.ThumbnailResizingMethod.CROP
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.core.decryptAes256Ctr
import de.connect2x.trixnity.crypto.core.encryptAes256Ctr
import de.connect2x.trixnity.crypto.core.sha256
import de.connect2x.trixnity.utils.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

private val log = Logger("de.connect2x.trixnity.client.media.MediaService")

interface MediaService {
    suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        saveToCache: Boolean = true,
    ): Result<PlatformMedia>

    suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        saveToCache: Boolean = true,
    ): Result<PlatformMedia>

    suspend fun getThumbnail(
        uri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod = CROP,
        animated: Boolean = false,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        saveToCache: Boolean = true,
    ): Result<PlatformMedia>

    suspend fun prepareUploadMedia(content: ByteArrayFlow, contentType: ContentType?): String

    suspend fun prepareUploadEncryptedMedia(content: ByteArrayFlow): EncryptedFile

    suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        keepMediaInCache: Boolean = true
    ): Result<String>
}

class MediaServiceImpl(
    private val api: MatrixClientServerApiClient,
    private val mediaStore: MediaStore,
    private val serverDataStore: ServerDataStore,
    private val mediaCacheMappingStore: MediaCacheMappingStore,
) : MediaService {
    companion object {
        private const val MATRIX_SPEC_1_11 = "v1.11"
        const val UPLOAD_MEDIA_CACHE_URI_PREFIX = "upload://"
        const val UPLOAD_MEDIA_MXC_URI_PREFIX = "mxc://"
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

    private suspend fun MediaApiClient.downloadDependingOnServerVersion(
        mxcUri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit> =
        if (serverDataStore.getServerData().versions.versions.contains(MATRIX_SPEC_1_11)) {
            download(mxcUri, progress = progress, downloadHandler = downloadHandler)
        } else {
            @Suppress("DEPRECATION")
            downloadLegacy(mxcUri, progress = progress, downloadHandler = downloadHandler)
        }

    private suspend fun getMedia(
        uri: String,
        saveToCache: Boolean,
        sha256Hash: String?,
        progress: MutableStateFlow<FileTransferProgress?>?,
    ): Result<PlatformMedia> = kotlin.runCatching {
        when {
            uri.startsWith(UPLOAD_MEDIA_MXC_URI_PREFIX) -> {
                val existingMedia = mediaStore.getMedia(uri)
                if (existingMedia == null) {
                    log.debug { "download media: $uri" }
                    if (sha256Hash == null) {
                        api.media.downloadDependingOnServerVersion(uri, progress = progress) {
                            it.saveMedia(uri) { this }
                        }.getOrThrow()
                    } else {
                        api.media.downloadDependingOnServerVersion(uri, progress = progress) {
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
                    requireNotNull(
                        mediaStore.getMedia(uri)
                    ) { "media should not be null, because it has just been saved" }
                        .transformByteArrayFlow { it.onCompletion { if (!saveToCache) mediaStore.deleteMedia(uri) } }
                } else {
                    log.debug { "found media in store: $uri" }
                    existingMedia
                }
            }

            uri.startsWith(UPLOAD_MEDIA_CACHE_URI_PREFIX) -> mediaStore.getMedia(uri)
                ?: mediaCacheMappingStore.getMediaCacheMapping(uri)?.mxcUri
                    ?.let { getMedia(it, saveToCache, sha256Hash, progress).getOrThrow() }
                ?: throw IllegalArgumentException("cache uri $uri does not exists")

            else -> throw IllegalArgumentException("uri $uri is no valid cache or mxc uri")
        }
    }

    override suspend fun getMedia(
        uri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<PlatformMedia> = getMedia(uri, saveToCache, null, progress)

    override suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<PlatformMedia> = kotlin.runCatching {
        val originalHash = encryptedFile.hashes["sha256"]
            ?: throw MediaValidationException(null, null)
        getMedia(encryptedFile.url, saveToCache, originalHash, progress).getOrThrow()
            .transformByteArrayFlow {
                it.decryptAes256Ctr(
                    initialisationVector = encryptedFile.initialisationVector.decodeUnpaddedBase64Bytes(),
                    // url-safe base64 is given
                    key = encryptedFile.key.key.replace("-", "+").replace("_", "/")
                        .decodeUnpaddedBase64Bytes()
                )
            }
    }

    override suspend fun getThumbnail(
        uri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        animated: Boolean,
        progress: MutableStateFlow<FileTransferProgress?>?,
        saveToCache: Boolean
    ): Result<PlatformMedia> = kotlin.runCatching {
        val thumbnailUrl = "$uri/${width}x$height/${api.json.encodeToJsonElement(method).jsonPrimitive.content}"
        val existingMedia = mediaStore.getMedia(thumbnailUrl)
        if (existingMedia == null) {
            api.media.downloadThumbnailDependingOnServerVersion(
                mxcUri = uri,
                width = width,
                height = height,
                method = method,
                animated = animated,
                progress = progress
            ) {
                it.saveMedia(thumbnailUrl) { this }
            }.getOrThrow()
            requireNotNull(
                mediaStore.getMedia(thumbnailUrl)
            ) { "media should not be null, because it has just been saved" }
                .transformByteArrayFlow { it.onCompletion { if (!saveToCache) mediaStore.deleteMedia(thumbnailUrl) } }
        } else existingMedia
    }

    private suspend fun MediaApiClient.downloadThumbnailDependingOnServerVersion(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        animated: Boolean,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit> =
        if (serverDataStore.getServerData().versions.versions.contains(MATRIX_SPEC_1_11)) {
            downloadThumbnail(
                mxcUri = mxcUri,
                width = width,
                height = height,
                method = method,
                animated = animated,
                progress = progress,
                downloadHandler = downloadHandler
            )
        } else {
            @Suppress("DEPRECATION")
            downloadThumbnailLegacy(
                mxcUri = mxcUri,
                width = width,
                height = height,
                method = method,
                progress = progress,
                downloadHandler = downloadHandler
            )
        }

    override suspend fun prepareUploadMedia(content: ByteArrayFlow, contentType: ContentType?): String {
        return "$UPLOAD_MEDIA_CACHE_URI_PREFIX${SecureRandom.nextString(22)}".also { cacheUri ->
            var fileSize = 0L
            mediaStore.addMedia(cacheUri, content.onEach { fileSize += it.size })
            mediaCacheMappingStore.saveMediaCacheMapping(
                cacheUri,
                MediaCacheMapping(cacheUri, size = fileSize, contentType = contentType?.toString())
            )
        }
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

    override suspend fun uploadMedia(
        cacheUri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        keepMediaInCache: Boolean
    ): Result<String> {
        if (!cacheUri.startsWith(UPLOAD_MEDIA_CACHE_URI_PREFIX)) throw IllegalArgumentException("$cacheUri is no cacheUri")

        val uploadMediaCache = requireNotNull(mediaCacheMappingStore.getMediaCacheMapping(cacheUri))
        val cachedMxcUri = uploadMediaCache.mxcUri
        if (uploadMediaCache.size > (serverDataStore.getServerData().mediaConfig.maxUploadSize ?: Long.MAX_VALUE))
            throw MediaTooLargeException

        return if (cachedMxcUri == null) {
            val content =
                mediaStore.getMedia(cacheUri)
                    ?: throw IllegalArgumentException("content for cacheUri $cacheUri not found")
            api.media.upload(
                Media(
                    content = content.toByteReadChannel(),
                    contentLength = uploadMediaCache.size,
                    contentType = uploadMediaCache.contentType
                        ?.let {
                            try {
                                ContentType.parse(it)
                            } catch (_: Exception) {
                                null
                            }
                        }
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
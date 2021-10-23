package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.MediaRepository
import net.folivo.trixnity.client.store.repository.UploadMediaRepository

class MediaStore(
    private val mediaRepository: MediaRepository,
    uploadMediaRepository: UploadMediaRepository,
    storeScope: CoroutineScope
) {
    private val mediaCache = StateFlowCache(storeScope, mediaRepository)

    suspend fun addContent(uri: String, content: ByteArray) = mediaCache.update(uri) { content }

    suspend fun getContent(uri: String): ByteArray? = mediaCache.get(uri)

    suspend fun deleteContent(uri: String) = mediaCache.update(uri) { null }

    suspend fun changeUri(oldUri: String, newUri: String) {
        mediaRepository.changeUri(oldUri, newUri)
    }

    private val uploadMediaCache = StateFlowCache(storeScope, uploadMediaRepository)

    suspend fun getUploadMedia(cacheUri: String): UploadMedia? =
        uploadMediaCache.get(cacheUri)

    suspend fun updateUploadMedia(
        cacheUri: String,
        updater: suspend (oldUploadMedia: UploadMedia?) -> UploadMedia?
    ) = uploadMediaCache.update(cacheUri, updater)
}
package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.MediaRepository
import net.folivo.trixnity.client.store.repository.UploadMediaRepository

class MediaStore(
    private val mediaRepository: MediaRepository,
    uploadMediaRepository: UploadMediaRepository,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) {
    private val mediaCache = RepositoryStateFlowCache(storeScope, mediaRepository, rtm)

    suspend fun addContent(uri: String, content: ByteArray) = mediaCache.update(uri) { content }

    suspend fun getContent(uri: String): ByteArray? = mediaCache.get(uri)

    suspend fun deleteContent(uri: String) = mediaCache.update(uri) { null }

    suspend fun changeUri(oldUri: String, newUri: String) = rtm.transaction {
        mediaRepository.changeUri(oldUri, newUri)
    }

    private val uploadMediaCache = RepositoryStateFlowCache(storeScope, uploadMediaRepository, rtm)

    suspend fun getUploadMedia(cacheUri: String): UploadMedia? =
        uploadMediaCache.get(cacheUri)

    suspend fun updateUploadMedia(
        cacheUri: String,
        updater: suspend (oldUploadMedia: UploadMedia?) -> UploadMedia?
    ) = uploadMediaCache.update(cacheUri, updater = updater)
}
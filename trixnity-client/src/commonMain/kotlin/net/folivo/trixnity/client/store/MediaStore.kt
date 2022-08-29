package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.MediaRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.UploadMediaRepository

class MediaStore(
    private val mediaRepository: MediaRepository,
    private val uploadMediaRepository: UploadMediaRepository,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) : IStore {
    private val mediaCache = RepositoryStateFlowCache(storeScope, mediaRepository, rtm)

    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        rtm.transaction {
            mediaRepository.deleteAll()
            uploadMediaRepository.deleteAll()
        }
        mediaCache.reset()
        uploadMediaCache.reset()
    }

    suspend fun addContent(uri: String, content: ByteArray) = mediaCache.update(uri) { content }

    suspend fun getContent(uri: String): ByteArray? = mediaCache.get(uri)

    suspend fun deleteContent(uri: String) = mediaCache.update(uri) { null }

    suspend fun changeUri(oldUri: String, newUri: String) = rtm.transaction {
        mediaRepository.changeUri(oldUri, newUri)
    }

    private val uploadMediaCache = RepositoryStateFlowCache(storeScope, uploadMediaRepository, rtm)

    suspend fun getUploadCache(cacheUri: String): UploadCache? =
        uploadMediaCache.get(cacheUri)

    suspend fun updateUploadCache(
        cacheUri: String,
        updater: suspend (oldUploadCache: UploadCache?) -> UploadCache?
    ) = uploadMediaCache.update(cacheUri, updater = updater)
}
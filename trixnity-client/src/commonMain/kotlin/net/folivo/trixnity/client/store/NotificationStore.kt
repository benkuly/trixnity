package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flattenNotNull
import net.folivo.trixnity.client.store.cache.FullRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.NotificationRepository
import net.folivo.trixnity.client.store.repository.NotificationStateRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Clock

class NotificationStore(
    private val notificationRepository: NotificationRepository,
    notificationStateRepository: NotificationStateRepository,
    private val tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val notificationCache =
        FullRepositoryObservableCache(
            notificationRepository,
            tm,
            storeScope,
            clock,
            config.cacheExpireDurations.room,
        ) { it.id }.also(statisticCollector::addCache)

    private val notificationStateCache =
        FullRepositoryObservableCache(
            notificationStateRepository,
            tm,
            storeScope,
            clock,
            config.cacheExpireDurations.room,
        ) { it.roomId }.also(statisticCollector::addCache)

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        notificationCache.deleteAll()
        notificationStateCache.deleteAll()
    }

    fun getAll(): Flow<Map<String, Flow<StoredNotification?>>> = notificationCache.readAll()
    fun getById(id: String): Flow<StoredNotification?> = notificationCache.get(id)

    suspend fun save(
        value: StoredNotification
    ) = notificationCache.set(value.id, value)

    suspend fun update(
        id: String,
        updater: suspend (oldNotification: StoredNotification?) -> StoredNotification?
    ) = notificationCache.update(id, updater = updater)

    suspend fun save(
        id: String,
        value: StoredNotification?
    ) = notificationCache.set(id, value)

    suspend fun delete(id: String) = notificationCache.set(id, null)
    suspend fun deleteByRoomId(roomId: RoomId) {
        tm.writeTransaction {
            notificationRepository.deleteByRoomId(roomId)
        }
        notificationCache.readAll().flattenNotNull().first().forEach { (key, value) ->
            if (value.roomId == roomId) notificationCache.set(key, null, persistEnabled = false)
        }
    }

    suspend fun deleteAllNotifications() = notificationCache.deleteAll()

    suspend fun updateState(
        roomId: RoomId,
        updater: suspend (oldNotificationState: StoredNotificationState?) -> StoredNotificationState?
    ) = notificationStateCache.update(roomId, updater = updater)

    fun getAllState() = notificationStateCache.readAll()
}
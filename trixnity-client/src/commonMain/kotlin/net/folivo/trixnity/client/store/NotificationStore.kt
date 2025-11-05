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
import net.folivo.trixnity.client.store.repository.NotificationUpdateRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Clock

class NotificationStore(
    private val notificationRepository: NotificationRepository,
    private val notificationUpdateRepository: NotificationUpdateRepository,
    private val notificationStateRepository: NotificationStateRepository,
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
            config.cacheExpireDurations.notification,
        ) { it.id }.also(statisticCollector::addCache)

    private val notificationUpdateCache =
        FullRepositoryObservableCache(
            notificationUpdateRepository,
            tm,
            storeScope,
            clock,
            config.cacheExpireDurations.notification,
        ) { it.id }.also(statisticCollector::addCache)

    private val notificationStateCache =
        FullRepositoryObservableCache(
            notificationStateRepository,
            tm,
            storeScope,
            clock,
            config.cacheExpireDurations.notification,
        ) { it.roomId }.also(statisticCollector::addCache)

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        notificationCache.deleteAll()
        notificationUpdateCache.deleteAll()
        notificationStateCache.deleteAll()
    }

    fun getAll(): Flow<Map<String, Flow<StoredNotification?>>> = notificationCache.readAll()
    fun getAllUpdates(): Flow<Map<String, Flow<StoredNotificationUpdate?>>> =
        notificationUpdateCache.readAll()

    fun getAllState() = notificationStateCache.readAll()


    fun getById(id: String): Flow<StoredNotification?> = notificationCache.get(id)

    suspend fun save(
        value: StoredNotification
    ) = notificationCache.set(value.id, value)

    suspend fun save(
        id: String,
        value: StoredNotification?
    ) = notificationCache.set(id, value)

    suspend fun saveAllUpdates(values: List<StoredNotificationUpdate>) {
        values.forEach { notificationUpdateCache.set(it.id, it) }
    }

    suspend fun update(
        id: String,
        updater: suspend (oldNotification: StoredNotification?) -> StoredNotification?
    ) = notificationCache.update(id, updater = updater)

    suspend fun updateState(
        roomId: RoomId,
        updater: suspend (oldNotificationState: StoredNotificationState?) -> StoredNotificationState?
    ) = notificationStateCache.update(roomId, updater = updater)

    suspend fun updateUpdate(
        id: String,
        updater: suspend (oldNotificationUpdate: StoredNotificationUpdate?) -> StoredNotificationUpdate?
    ) = notificationUpdateCache.update(id, updater = updater)

    suspend fun delete(id: String) = notificationCache.set(id, null)
    suspend fun deleteByRoomId(roomId: RoomId) {
        tm.writeTransaction {
            notificationRepository.deleteByRoomId(roomId)
        }
        notificationCache.readAll().flattenNotNull().first().forEach { (key, value) ->
            if (value.roomId == roomId) notificationCache.set(key, null, persistEnabled = false)
        }
        tm.writeTransaction {
            notificationUpdateRepository.deleteByRoomId(roomId)
        }
        notificationUpdateCache.readAll().flattenNotNull().first().forEach { (key, value) ->
            if (value.roomId == roomId) notificationUpdateCache.set(key, null, persistEnabled = false)
        }
        notificationStateCache.set(roomId, null)
    }
}
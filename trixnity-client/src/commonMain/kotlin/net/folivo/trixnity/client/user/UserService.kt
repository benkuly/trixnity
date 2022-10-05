package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.retryWhenSyncIs
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import kotlin.reflect.KClass

private val log = KotlinLogging.logger {}

interface IUserService {
    val userPresence: StateFlow<Map<UserId, PresenceEventContent>>
    suspend fun loadMembers(roomId: RoomId, wait: Boolean = true)

    suspend fun getAll(roomId: RoomId): Flow<Set<RoomUser>?>

    suspend fun getById(userId: UserId, roomId: RoomId): Flow<RoomUser?>

    suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<C?>
}

class UserService(
    private val roomUserStore: RoomUserStore,
    private val roomStore: RoomStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val api: IMatrixClientServerApiClient,
    presenceEventHandler: PresenceEventHandler,
    private val currentSyncState: CurrentSyncState,
    private val scope: CoroutineScope,
) : IUserService {

    private val currentlyLoadingMembers = MutableStateFlow<Set<RoomId>>(setOf())
    override val userPresence = presenceEventHandler.userPresence

    override suspend fun loadMembers(roomId: RoomId, wait: Boolean) {
        if (currentlyLoadingMembers.getAndUpdate { it + roomId }.contains(roomId).not()) {
            scope.launch {
                currentSyncState.retryWhenSyncIs(
                    SyncState.RUNNING,
                    onError = { log.warn(it) { "failed loading members" } },
                ) {
                    val room = roomStore.get(roomId).first()
                    if (room?.membersLoaded != true) {
                        val memberEvents = api.rooms.getMembers(
                            roomId = roomId,
                            notMembership = LEAVE
                        ).getOrThrow().toList()
                        memberEvents.forEach { api.sync.emitEvent(it) }
                        roomStore.update(roomId) { it?.copy(membersLoaded = true) }
                    }
                }
                currentlyLoadingMembers.update { it - roomId }
            }
        }
        if (wait) roomStore.get(roomId).first { it?.membersLoaded == true }
    }

    override suspend fun getAll(roomId: RoomId): Flow<Set<RoomUser>?> {
        return roomUserStore.getAll(roomId)
    }

    override suspend fun getById(userId: UserId, roomId: RoomId): Flow<RoomUser?> {
        return roomUserStore.get(userId, roomId)
    }

    override suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        return globalAccountDataStore.get(eventContentClass, key)
            .map { it?.content }
    }
}
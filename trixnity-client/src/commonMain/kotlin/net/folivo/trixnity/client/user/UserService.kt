package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.retryWhenSyncIs
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import kotlin.reflect.KClass

private val log = KotlinLogging.logger {}

interface UserService {
    val userPresence: StateFlow<Map<UserId, PresenceEventContent>>
    suspend fun loadMembers(roomId: RoomId, wait: Boolean = true)

    fun getAll(roomId: RoomId): Flow<Set<RoomUser>?>

    fun getById(userId: UserId, roomId: RoomId): Flow<RoomUser?>

    fun canKickUser(userId: UserId, roomId: RoomId): Flow<Boolean>
    fun canBanUser(userId: UserId, roomId: RoomId): Flow<Boolean>
    fun canUnbanUser(userId: UserId, roomId: RoomId): Flow<Boolean>
    fun canInviteUser(userId: UserId, roomId: RoomId): Flow<Boolean>
    fun canInvite(roomId: RoomId): Flow<Boolean>
    fun canSetPowerLevelToMax(userId: UserId, roomId: RoomId): Flow<Int?>

    fun getPowerLevel(userId: UserId, roomId: RoomId): Flow<Int>
    fun getPowerLevel(
        userId: UserId,
        powerLevelsEventContent: PowerLevelsEventContent?,
        createEventContent: CreateEventContent?
    ): Int

    fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<C?>
}

class UserServiceImpl(
    private val roomUserStore: RoomUserStore,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val api: MatrixClientServerApiClient,
    presenceEventHandler: PresenceEventHandler,
    private val currentSyncState: CurrentSyncState,
    userInfo: UserInfo,
    private val tm: TransactionManager,
    private val scope: CoroutineScope,
) : UserService {

    private val currentlyLoadingMembers = MutableStateFlow<Set<RoomId>>(setOf())
    override val userPresence = presenceEventHandler.userPresence
    private val ownUserId = userInfo.userId

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
                        ).getOrThrow()
                        memberEvents.chunked(500).forEach { chunk ->
                            tm.withWriteTransaction {
                                chunk.forEach { api.sync.emitEvent(it) }
                            }?.first { it } // wait for transaction to be applied
                        }
                        roomStore.update(roomId) { it?.copy(membersLoaded = true) }

                    }
                }
                currentlyLoadingMembers.update { it - roomId }
            }
        }
        if (wait) roomStore.get(roomId).first { it?.membersLoaded == true }
    }

    override fun getAll(roomId: RoomId): Flow<Set<RoomUser>?> {
        return roomUserStore.getAll(roomId)
    }

    override fun getById(userId: UserId, roomId: RoomId): Flow<RoomUser?> {
        return roomUserStore.get(userId, roomId)
    }

    override fun getPowerLevel(
        userId: UserId,
        roomId: RoomId
    ): Flow<Int> =
        combine(
            roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId)
        ) { powerLevelsEvent, createEvent ->
            getPowerLevel(userId, powerLevelsEvent?.content, createEvent?.content)
        }

    override fun getPowerLevel(
        userId: UserId,
        powerLevelsEventContent: PowerLevelsEventContent?,
        createEventContent: CreateEventContent?
    ): Int {
        return when (powerLevelsEventContent) {
            null -> if (createEventContent?.creator == userId) 100 else 0
            else -> powerLevelsEventContent.users[userId] ?: powerLevelsEventContent.usersDefault
        }
    }

    override fun canKickUser(userId: UserId, roomId: RoomId): Flow<Boolean> =
        combine(
            roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId)
        ) { powerLevelsEvent, createEvent ->
            val powerLevelsEventContent = powerLevelsEvent?.content
            val createEventContent = createEvent?.content

            val myUserPowerLevel =
                getPowerLevel(ownUserId, powerLevelsEventContent, createEventContent)
            val toKickUserPowerLevel =
                getPowerLevel(userId, powerLevelsEventContent, createEventContent)

            val kickLevel = powerLevelsEventContent?.kick ?: 50

            myUserPowerLevel >= kickLevel && myUserPowerLevel > toKickUserPowerLevel
        }

    override fun canBanUser(userId: UserId, roomId: RoomId): Flow<Boolean> =
        combine(
            roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId)
        ) { powerLevelsEvent, createEvent ->
            val powerLevelsEventContent = powerLevelsEvent?.content
            val createEventContent = createEvent?.content

            val ownUserIdPowerLevel =
                getPowerLevel(ownUserId, powerLevelsEventContent, createEventContent)
            val toBanUserPowerLevel =
                getPowerLevel(userId, powerLevelsEventContent, createEventContent)

            val banLevel = powerLevelsEventContent?.ban ?: 50

            ownUserIdPowerLevel >= banLevel && ownUserIdPowerLevel > toBanUserPowerLevel
        }

    override fun canUnbanUser(userId: UserId, roomId: RoomId): Flow<Boolean> =
        combine(
            roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId)
        ) { powerLevelsEvent, createEvent ->
            val powerLevelsEventContent = powerLevelsEvent?.content
            val createEventContent = createEvent?.content

            val ownUserIdPowerLevel =
                getPowerLevel(ownUserId, powerLevelsEventContent, createEventContent)
            val toUnbanUserPowerLevel =
                getPowerLevel(userId, powerLevelsEventContent, createEventContent)

            val banLevel = powerLevelsEventContent?.ban ?: 50
            val kickLevel = powerLevelsEventContent?.kick ?: 50

            ownUserIdPowerLevel >= banLevel && ownUserIdPowerLevel >= kickLevel && ownUserIdPowerLevel > toUnbanUserPowerLevel
        }

    override fun canInviteUser(userId: UserId, roomId: RoomId): Flow<Boolean> =
        combine(
            roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId),
            getById(userId, roomId).map { it?.membership }
        ) { powerLevelsEvent, createEvent, membership ->
            val powerLevelsEventContent = powerLevelsEvent?.content
            val createEventContent = createEvent?.content

            val ownUserIdPowerLevel =
                getPowerLevel(ownUserId, powerLevelsEventContent, createEventContent)

            val inviteLevel = powerLevelsEventContent?.invite ?: 0

            ownUserIdPowerLevel >= inviteLevel && membership != Membership.BAN
        }

    override fun canInvite(roomId: RoomId): Flow<Boolean> =
        combine(
            roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId),
        ) { powerLevelsEvent, createEvent ->
            val powerLevelsEventContent = powerLevelsEvent?.content
            val createEventContent = createEvent?.content

            val ownUserIdPowerLevel =
                getPowerLevel(ownUserId, powerLevelsEventContent, createEventContent)

            val inviteLevel = powerLevelsEventContent?.invite ?: 0

            ownUserIdPowerLevel >= inviteLevel
        }

    override fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        return globalAccountDataStore.get(eventContentClass, key)
            .map { it?.content }
    }

    override fun canSetPowerLevelToMax(
        userId: UserId,
        roomId: RoomId
    ): Flow<Int?> {
        return combine(
            getPowerLevel(userId = ownUserId, roomId),
            getPowerLevel(userId = userId, roomId),
            roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId),
        )
        { ownPowerLevel, oldOtherUserPowerLevel, powerLevelsEvent ->
            if (powerLevelsEvent != null && ((powerLevelsEvent.content.events["m.room.power_levels"]
                    ?: powerLevelsEvent.content.stateDefault) > ownPowerLevel)
            ) return@combine null
            if (oldOtherUserPowerLevel >= ownPowerLevel && userId != ownUserId) return@combine null
            return@combine ownPowerLevel
        }
    }
}
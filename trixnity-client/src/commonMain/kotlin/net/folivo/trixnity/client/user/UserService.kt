package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.retryWhenSyncIs
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass

private val log = KotlinLogging.logger {}

interface UserService {
    val userPresence: StateFlow<Map<UserId, PresenceEventContent>>
    suspend fun loadMembers(roomId: RoomId, wait: Boolean = true)

    fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>?>

    fun getById(roomId: RoomId, userId: UserId): Flow<RoomUser?>

    fun getPowerLevel(roomId: RoomId, userId: UserId): Flow<Int>
    fun getPowerLevel(
        userId: UserId,
        powerLevelsEventContent: PowerLevelsEventContent?,
        createEventContent: CreateEventContent?
    ): Int

    fun canKickUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canBanUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canUnbanUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canInviteUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canInvite(roomId: RoomId): Flow<Boolean>
    fun canRedactEvent(roomId: RoomId, eventId: EventId): Flow<Boolean>

    fun canSetPowerLevelToMax(roomId: RoomId, userId: UserId): Flow<Int?>

    @Deprecated("use canSendEvent instead", ReplaceWith("canSendEvent(roomId, RoomMessageEventContent::class)"))
    fun canSendMessages(roomId: RoomId): Flow<Boolean>
    fun canSendEvent(roomId: RoomId, eventClass: KClass<out EventContent>): Flow<Boolean>


    fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<C?>
}

class UserServiceImpl(
    private val roomUserStore: RoomUserStore,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val api: MatrixClientServerApiClient,
    presenceEventHandler: PresenceEventHandler,
    private val currentSyncState: CurrentSyncState,
    userInfo: UserInfo,
    private val mappings: EventContentSerializerMappings,
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
                            tm.withAsyncWriteTransaction {
                                // TODO We should synchronize this with the sync. Otherwise this could overwrite a newer event.
                                chunk.forEach { api.sync.emitEvent(it) }
                            }
                        }
                        roomStore.update(roomId) { it?.copy(membersLoaded = true) }
                    }
                }
                currentlyLoadingMembers.update { it - roomId }
            }
        }
        if (wait) roomStore.get(roomId).first { it?.membersLoaded == true }
    }

    override fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>?> {
        return roomUserStore.getAll(roomId)
    }

    override fun getById(roomId: RoomId, userId: UserId): Flow<RoomUser?> {
        return roomUserStore.get(userId, roomId)
    }

    override fun getPowerLevel(
        roomId: RoomId,
        userId: UserId
    ): Flow<Int> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getContentByStateKey<CreateEventContent>(roomId)
        ) { powerLevels, createEvent ->
            getPowerLevel(userId, powerLevels, createEvent)
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

    override fun canKickUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getContentByStateKey<CreateEventContent>(roomId)
        ) { powerLevels, createEvent ->
            val myUserPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val toKickUserPowerLevel = getPowerLevel(userId, powerLevels, createEvent)
            val kickLevel = powerLevels.kick

            myUserPowerLevel >= kickLevel && myUserPowerLevel > toKickUserPowerLevel
        }

    override fun canBanUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getContentByStateKey<CreateEventContent>(roomId)
        ) { powerLevels, createEvent ->
            val ownUserIdPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val toBanUserPowerLevel = getPowerLevel(userId, powerLevels, createEvent)
            val banLevel = powerLevels.ban

            ownUserIdPowerLevel >= banLevel && ownUserIdPowerLevel > toBanUserPowerLevel
        }

    override fun canUnbanUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getContentByStateKey<CreateEventContent>(roomId)
        ) { powerLevels, createEvent ->
            val ownUserIdPowerLevel =
                getPowerLevel(ownUserId, powerLevels, createEvent)
            val toUnbanUserPowerLevel =
                getPowerLevel(userId, powerLevels, createEvent)
            val banLevel = powerLevels.ban
            val kickLevel = powerLevels.kick

            ownUserIdPowerLevel >= banLevel && ownUserIdPowerLevel >= kickLevel && ownUserIdPowerLevel > toUnbanUserPowerLevel
        }

    override fun canInviteUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getContentByStateKey<CreateEventContent>(roomId),
            getById(roomId, userId).map { it?.membership }
        ) { powerLevels, createEvent, membership ->
            val ownUserIdPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val inviteLevel = powerLevels.invite

            ownUserIdPowerLevel >= inviteLevel && membership != Membership.BAN
        }

    override fun canInvite(roomId: RoomId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getContentByStateKey<CreateEventContent>(roomId),
        ) { powerLevels, createEvent ->
            val ownUserIdPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val inviteLevel = powerLevels.invite

            ownUserIdPowerLevel >= inviteLevel
        }

    override fun canRedactEvent(
        roomId: RoomId,
        eventId: EventId,
    ): Flow<Boolean> {
        return combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomTimelineStore.get(eventId, roomId).filterNotNull(),
        ) { powerLevels, timelineEvent ->
            val userPowerLevel = powerLevels.users[ownUserId] ?: powerLevels.usersDefault
            val sendRedactionEventPowerLevel =
                powerLevels.events.get<RedactionEventContent>() ?: powerLevels.eventsDefault
            val redactPowerLevelNeeded = powerLevels.redact
            val isOwnMessage by lazy { timelineEvent.event.sender == ownUserId }
            val allowRedactOwnMessages by lazy { userPowerLevel >= sendRedactionEventPowerLevel }
            val allowRedactOtherMessages by lazy { userPowerLevel >= redactPowerLevelNeeded }
            val content = timelineEvent.content?.getOrNull()
            content is MessageEventContent && content !is RedactedMessageEventContent &&
                    (isOwnMessage && allowRedactOwnMessages || allowRedactOtherMessages)
        }
    }

    override fun canSendEvent(roomId: RoomId, eventClass: KClass<out EventContent>): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getContentByStateKey<CreateEventContent>(roomId),
        ) { powerLevels, createEvent ->
            val userPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val sendEventPowerLevel = powerLevels.events[eventClass]
                ?: when {
                    mappings.state.any { it.kClass == eventClass } -> powerLevels.stateDefault
                    mappings.message.any { it.kClass == eventClass } -> powerLevels.eventsDefault
                    else -> throw IllegalArgumentException("eventClass $eventClass does not match any event in mappings")
                }
            userPowerLevel >= sendEventPowerLevel
        }

    @Deprecated("use canSendEvent instead", ReplaceWith("canSendEvent(roomId, RoomMessageEventContent::class)"))
    override fun canSendMessages(
        roomId: RoomId,
    ): Flow<Boolean> {
        return roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId)
            .map { powerLevels ->
                val eventsDefault = powerLevels.eventsDefault
                val ownPowerLevel = powerLevels.users[ownUserId] ?: powerLevels.usersDefault
                ownPowerLevel >= eventsDefault
            }
    }

    override fun canSetPowerLevelToMax(
        roomId: RoomId,
        userId: UserId
    ): Flow<Int?> {
        return combine(
            getPowerLevel(roomId, ownUserId),
            getPowerLevel(roomId, userId),
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
        ) { ownPowerLevel, oldOtherUserPowerLevel, powerLevels ->
            when {
                (powerLevels.events.get<PowerLevelsEventContent>() ?: powerLevels.stateDefault) > ownPowerLevel -> null
                oldOtherUserPowerLevel >= ownPowerLevel && userId != ownUserId -> null
                else -> ownPowerLevel

            }
        }
    }

    override fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        return globalAccountDataStore.get(eventContentClass, key)
            .map { it?.content }
    }
}
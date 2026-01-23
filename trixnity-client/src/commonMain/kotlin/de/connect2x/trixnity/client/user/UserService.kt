package de.connect2x.trixnity.client.user

import de.connect2x.lognity.api.logger.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.*
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.*
import de.connect2x.trixnity.core.model.events.m.room.*
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.BAN_DEFAULT
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.EVENTS_DEFAULT
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.INVITE_DEFAULT
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.KICK_DEFAULT
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.REDACT_DEFAULT
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.STATE_DEFAULT
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration

private val log = Logger("de.connect2x.trixnity.client.user.UserService")


interface UserService {
    suspend fun loadMembers(roomId: RoomId, wait: Boolean = true)

    fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>>

    fun getById(roomId: RoomId, userId: UserId): Flow<RoomUser?>

    fun getAllReceipts(roomId: RoomId): Flow<Map<UserId, Flow<RoomUserReceipts?>>>

    fun getReceiptsById(roomId: RoomId, userId: UserId): Flow<RoomUserReceipts?>

    fun getPowerLevel(roomId: RoomId, userId: UserId): Flow<PowerLevel>
    fun getPowerLevel(
        userId: UserId,
        createEvent: ClientEvent.StateBaseEvent<CreateEventContent>,
        powerLevelsEventContent: PowerLevelsEventContent?,
    ): PowerLevel

    fun canKickUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canBanUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canUnbanUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canInviteUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canInvite(roomId: RoomId): Flow<Boolean>
    fun canRedactEvent(roomId: RoomId, eventId: EventId): Flow<Boolean>

    fun canSetPowerLevelToMax(roomId: RoomId, userId: UserId): Flow<PowerLevel.User?>

    fun canSendEvent(roomId: RoomId, eventClass: KClass<out RoomEventContent>): Flow<Boolean>
    fun canSendEvent(roomId: RoomId, eventContent: RoomEventContent): Flow<Boolean>

    fun getPresence(userId: UserId): Flow<UserPresence?>

    fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<C?>
}

class UserServiceImpl(
    private val roomStore: RoomStore,
    private val roomUserStore: RoomUserStore,
    private val roomStateStore: RoomStateStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val userPresenceStore: UserPresenceStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val loadMembersService: LoadMembersService,
    userInfo: UserInfo,
    private val currentSyncState: CurrentSyncState,
    private val getPowerLevelDelegate: GetPowerLevel,
    private val canDoAction: CanDoAction,
    private val clock: Clock,
    private val mappings: EventContentSerializerMappings,
    private val config: MatrixClientConfiguration,
) : UserService {

    private val ownUserId = userInfo.userId

    override suspend fun loadMembers(roomId: RoomId, wait: Boolean) = loadMembersService(roomId, wait)

    override fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>> {
        return roomUserStore.getAll(roomId)
    }

    override fun getById(roomId: RoomId, userId: UserId): Flow<RoomUser?> {
        return roomUserStore.get(userId, roomId)
    }

    override fun getAllReceipts(roomId: RoomId): Flow<Map<UserId, Flow<RoomUserReceipts?>>> {
        return roomUserStore.getAllReceipts(roomId)
    }

    override fun getReceiptsById(roomId: RoomId, userId: UserId): Flow<RoomUserReceipts?> {
        return roomUserStore.getReceipts(userId, roomId)
    }

    override fun getPowerLevel(
        roomId: RoomId,
        userId: UserId
    ): Flow<PowerLevel> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull()
        ) { powerLevels, createEvent ->
            getPowerLevel(userId, createEvent, powerLevels)
        }.distinctUntilChanged()

    private val legacyRoomVersions = setOf(null, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")
    private fun ClientEvent.StateBaseEvent<CreateEventContent>.isCreator(userId: UserId) =
        if (legacyRoomVersions.contains(content.roomVersion)) false
        else (content.additionalCreators.orEmpty() + sender).contains(userId)

    override fun getPowerLevel(
        userId: UserId,
        createEvent: ClientEvent.StateBaseEvent<CreateEventContent>,
        powerLevelsEventContent: PowerLevelsEventContent?
    ): PowerLevel = getPowerLevelDelegate(userId, createEvent, powerLevelsEventContent)

    override fun canKickUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStore.get(roomId).map { it?.membership }.filterNotNull(),
            roomUserStore.get(userId, roomId).map { it?.membership },
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
        ) { ownMembership, otherMembership, createEvent, powerLevelsEventContent ->
            if (ownMembership != Membership.JOIN || otherMembership == Membership.LEAVE) return@combine false
            canDoAction.toUser(userId, createEvent, powerLevelsEventContent) { ownPowerLevel ->
                ownPowerLevel >= (powerLevelsEventContent?.kick ?: KICK_DEFAULT)
            }
        }.distinctUntilChanged()

    override fun canBanUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStore.get(roomId).map { it?.membership }.filterNotNull(),
            roomUserStore.get(userId, roomId).map { it?.membership },
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
        ) { ownMembership, otherMembership, createEvent, powerLevelsEventContent ->
            if (ownMembership != Membership.JOIN || otherMembership == Membership.BAN) return@combine false
            canDoAction.toUser(userId, createEvent, powerLevelsEventContent) { ownPowerLevel ->
                ownPowerLevel >= (powerLevelsEventContent?.ban ?: BAN_DEFAULT)
            }
        }.distinctUntilChanged()

    override fun canUnbanUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStore.get(roomId).map { it?.membership }.filterNotNull(),
            roomUserStore.get(userId, roomId).map { it?.membership },
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
        ) { ownMembership, otherMembership, createEvent, powerLevelsEventContent ->
            if (ownMembership != Membership.JOIN || otherMembership != Membership.BAN) return@combine false
            canDoAction.toUser(userId, createEvent, powerLevelsEventContent) { ownPowerLevel ->
                ownPowerLevel >= (powerLevelsEventContent?.ban ?: BAN_DEFAULT) &&
                        ownPowerLevel >= (powerLevelsEventContent?.kick ?: KICK_DEFAULT)
            }
        }.distinctUntilChanged()

    override fun canInviteUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStore.get(roomId).map { it?.membership }.filterNotNull(),
            roomUserStore.get(userId, roomId).map { it?.membership },
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
        ) { ownMembership, otherMembership, powerLevelsEventContent, createEvent ->
            if (ownMembership != Membership.JOIN || otherMembership == Membership.INVITE || otherMembership == Membership.JOIN || otherMembership == Membership.BAN) return@combine false
            canDoAction.asUser(ownUserId, createEvent, powerLevelsEventContent) { ownPowerLevel ->
                ownPowerLevel >= (powerLevelsEventContent?.invite ?: INVITE_DEFAULT)
            }
        }.distinctUntilChanged()

    override fun canInvite(roomId: RoomId): Flow<Boolean> =
        combine(
            roomStore.get(roomId).map { it?.membership }.filterNotNull(),
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
        ) { ownMembership, powerLevelsEventContent, createEvent ->
            if (ownMembership != Membership.JOIN) return@combine false
            canDoAction.asUser(ownUserId, createEvent, powerLevelsEventContent) { ownPowerLevel ->
                ownPowerLevel >= (powerLevelsEventContent?.invite ?: INVITE_DEFAULT)
            }
        }.distinctUntilChanged()

    override fun canRedactEvent(
        roomId: RoomId,
        eventId: EventId,
    ): Flow<Boolean> =
        combine(
            roomStore.get(roomId).map { it?.membership }.filterNotNull(),
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
            roomTimelineStore.get(eventId, roomId).filterNotNull(),
        ) { ownMembership, powerLevelsEventContent, createEvent, timelineEvent ->
            if (ownMembership != Membership.JOIN) return@combine false
            canDoAction.asUser(ownUserId, createEvent, powerLevelsEventContent) { ownPowerLevel ->
                val content = timelineEvent.content?.getOrNull()
                if (content !is MessageEventContent || content is RedactedEventContent) return@asUser false

                if (timelineEvent.event.sender == ownUserId) {
                    val sendRedactionEventPowerLevel =
                        powerLevelsEventContent?.events?.get<RedactionEventContent>()
                            ?: powerLevelsEventContent?.eventsDefault
                            ?: EVENTS_DEFAULT
                    ownPowerLevel >= sendRedactionEventPowerLevel
                } else {
                    ownPowerLevel >= (powerLevelsEventContent?.redact ?: REDACT_DEFAULT)
                }
            }
        }.distinctUntilChanged()


    override fun canSendEvent(roomId: RoomId, eventClass: KClass<out RoomEventContent>): Flow<Boolean> =
        combine(
            roomStore.get(roomId).map { it?.membership }.filterNotNull(),
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
        ) { membership, powerLevelsEventContent, createEvent ->
            if (membership != Membership.JOIN) return@combine false
            canDoAction.asUser(ownUserId, createEvent, powerLevelsEventContent) { ownPowerLevel ->
                val sendEventPowerLevel = powerLevelsEventContent?.events?.get(eventClass)
                    ?: when {
                        mappings.state.any { it.kClass == eventClass } ->
                            powerLevelsEventContent?.stateDefault ?: STATE_DEFAULT

                        mappings.message.any { it.kClass == eventClass } ->
                            powerLevelsEventContent?.eventsDefault ?: EVENTS_DEFAULT

                        else -> throw IllegalArgumentException("eventClass $eventClass does not match any event in mappings")
                    }
                ownPowerLevel >= sendEventPowerLevel
            }
        }.distinctUntilChanged()

    override fun canSendEvent(roomId: RoomId, eventContent: RoomEventContent): Flow<Boolean> {
        val baseEventClass =
            mappings.message.find { it.kClass.isInstance(eventContent) }?.kClass
                ?: mappings.state.find { it.kClass.isInstance(eventContent) }?.kClass
                ?: throw IllegalArgumentException("eventContent ${eventContent::class.simpleName} does not match any event in mappings")
        return canSendEvent(roomId, baseEventClass)
    }

    override fun canSetPowerLevelToMax(
        roomId: RoomId,
        userId: UserId
    ): Flow<PowerLevel.User?> =
        combine(
            roomStore.get(roomId).map { it?.membership }.filterNotNull(),
            roomUserStore.get(userId, roomId).map { it?.membership },
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
        ) { ownMembership, otherMembership, powerLevelsEventContent, createEvent ->
            if (ownMembership != Membership.JOIN || otherMembership == null) return@combine null
            when (val otherPowerLevel = getPowerLevel(userId, createEvent, powerLevelsEventContent)) {
                is PowerLevel.Creator -> null
                is PowerLevel.User ->
                    when (val ownPowerLevel = getPowerLevel(ownUserId, createEvent, powerLevelsEventContent)) {
                        is PowerLevel.Creator -> PowerLevel.User(Long.MAX_VALUE)
                        is PowerLevel.User -> {
                            val sendPowerLevelEventPowerLevel =
                                (powerLevelsEventContent?.events?.get<PowerLevelsEventContent>()
                                    ?: powerLevelsEventContent?.stateDefault
                                    ?: STATE_DEFAULT)

                            when {
                                ownPowerLevel.level < sendPowerLevelEventPowerLevel -> null
                                userId != ownUserId && ownPowerLevel.level <= otherPowerLevel.level -> null
                                else -> PowerLevel.User(ownPowerLevel.level)
                            }
                        }
                    }
            }
        }.distinctUntilChanged()

    override fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        return globalAccountDataStore.get(eventContentClass, key)
            .map { it?.content }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPresence(userId: UserId): Flow<UserPresence?> =
        combine(currentSyncState, userPresenceStore.getPresence(userId)) { syncState, userPresence ->
            syncState to userPresence
        }.transformLatest { (syncState, userPresence) ->
            val now by lazy { clock.now() }
            log.trace { "getPresence: syncState=$syncState, userPresence=$userPresence, now=$now" }
            when {
                userPresence == null -> emit(null)
                syncState != SyncState.RUNNING -> emit(null)
                userPresence.isCurrentlyActive == true -> emit(userPresence)
                else -> {
                    val lastActive = userPresence.lastActive ?: userPresence.lastUpdate
                    val markAsUnknownDelay = config.userPresenceActivityThreshold - now.minus(lastActive)
                    if (markAsUnknownDelay > Duration.ZERO) {
                        emit(userPresence)
                        delay(markAsUnknownDelay)
                        emit(null)
                    } else emit(null)
                }
            }
        }.distinctUntilChanged()
}
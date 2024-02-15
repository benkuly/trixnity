package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.BAN_DEFAULT
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.EVENTS_DEFAULT
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.INVITE_DEFAULT
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.KICK_DEFAULT
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.REDACT_DEFAULT
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.STATE_DEFAULT
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent.Companion.USERS_DEFAULT
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass

private val log = KotlinLogging.logger {}

interface UserService {
    val userPresence: StateFlow<Map<UserId, PresenceEventContent>>
    suspend fun loadMembers(roomId: RoomId, wait: Boolean = true)

    fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>>

    fun getById(roomId: RoomId, userId: UserId): Flow<RoomUser?>

    fun getAllReceipts(roomId: RoomId): Flow<Map<UserId, Flow<RoomUserReceipts?>>>

    fun getReceiptsById(roomId: RoomId, userId: UserId): Flow<RoomUserReceipts?>

    fun getPowerLevel(roomId: RoomId, userId: UserId): Flow<Long>

    fun canKickUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canBanUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canUnbanUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canInviteUser(roomId: RoomId, userId: UserId): Flow<Boolean>
    fun canInvite(roomId: RoomId): Flow<Boolean>
    fun canRedactEvent(roomId: RoomId, eventId: EventId): Flow<Boolean>

    fun canSetPowerLevelToMax(roomId: RoomId, userId: UserId): Flow<Long?>

    fun canSendEvent(roomId: RoomId, eventClass: KClass<out EventContent>): Flow<Boolean>


    fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<C?>
}

class UserServiceImpl(
    private val roomUserStore: RoomUserStore,
    private val roomStateStore: RoomStateStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val loadMembersService: LoadMembersService,
    presenceEventHandler: PresenceEventHandler,
    userInfo: UserInfo,
    private val mappings: EventContentSerializerMappings,
) : UserService {

    override val userPresence = presenceEventHandler.userPresence
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
    ): Flow<Long> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull()
        ) { powerLevels, createEvent ->
            getPowerLevel(userId, powerLevels, createEvent)
        }

    private fun getPowerLevel(
        userId: UserId,
        powerLevelsEventContent: PowerLevelsEventContent?,
        createEventContent: ClientEvent.StateBaseEvent<CreateEventContent>
    ): Long {
        return when (powerLevelsEventContent) {
            null -> if (createEventContent.sender == userId) 100 else 0
            else -> powerLevelsEventContent.users[userId] ?: powerLevelsEventContent.usersDefault
        }
    }

    override fun canKickUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull()
        ) { powerLevels, createEvent ->
            val myUserPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val toKickUserPowerLevel = getPowerLevel(userId, powerLevels, createEvent)
            val kickLevel = powerLevels?.kick ?: KICK_DEFAULT

            myUserPowerLevel >= kickLevel && myUserPowerLevel > toKickUserPowerLevel
        }

    override fun canBanUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull()
        ) { powerLevels, createEvent ->
            val ownUserIdPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val toBanUserPowerLevel = getPowerLevel(userId, powerLevels, createEvent)
            val banLevel = powerLevels?.ban ?: BAN_DEFAULT

            ownUserIdPowerLevel >= banLevel && ownUserIdPowerLevel > toBanUserPowerLevel
        }

    override fun canUnbanUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull()
        ) { powerLevels, createEvent ->
            val ownUserIdPowerLevel =
                getPowerLevel(ownUserId, powerLevels, createEvent)
            val toUnbanUserPowerLevel =
                getPowerLevel(userId, powerLevels, createEvent)
            val banLevel = powerLevels?.ban ?: BAN_DEFAULT
            val kickLevel = powerLevels?.kick ?: KICK_DEFAULT

            ownUserIdPowerLevel >= banLevel && ownUserIdPowerLevel >= kickLevel && ownUserIdPowerLevel > toUnbanUserPowerLevel
        }

    override fun canInviteUser(roomId: RoomId, userId: UserId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
            getById(roomId, userId).map { it?.membership }
        ) { powerLevels, createEvent, membership ->
            val ownUserIdPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val inviteLevel = powerLevels?.invite ?: INVITE_DEFAULT

            ownUserIdPowerLevel >= inviteLevel && membership != Membership.BAN
        }

    override fun canInvite(roomId: RoomId): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
        ) { powerLevels, createEvent ->
            val ownUserIdPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val inviteLevel = powerLevels?.invite ?: INVITE_DEFAULT

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
            val userPowerLevel = powerLevels?.users?.get(ownUserId) ?: powerLevels?.usersDefault ?: USERS_DEFAULT
            val sendRedactionEventPowerLevel =
                powerLevels?.events?.get<RedactionEventContent>() ?: powerLevels?.eventsDefault ?: EVENTS_DEFAULT
            val redactPowerLevelNeeded = powerLevels?.redact ?: REDACT_DEFAULT
            val isOwnMessage by lazy { timelineEvent.event.sender == ownUserId }
            val allowRedactOwnMessages by lazy { userPowerLevel >= sendRedactionEventPowerLevel }
            val allowRedactOtherMessages by lazy { userPowerLevel >= redactPowerLevelNeeded }
            val content = timelineEvent.content?.getOrNull()
            content is MessageEventContent && content !is RedactedEventContent &&
                    (isOwnMessage && allowRedactOwnMessages || allowRedactOtherMessages)
        }
    }

    override fun canSendEvent(roomId: RoomId, eventClass: KClass<out EventContent>): Flow<Boolean> =
        combine(
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
            roomStateStore.getByStateKey<CreateEventContent>(roomId).filterNotNull(),
        ) { powerLevels, createEvent ->
            val userPowerLevel = getPowerLevel(ownUserId, powerLevels, createEvent)
            val sendEventPowerLevel = powerLevels?.events?.get(eventClass)
                ?: when {
                    mappings.state.any { it.kClass == eventClass } -> powerLevels?.stateDefault ?: STATE_DEFAULT
                    mappings.message.any { it.kClass == eventClass } -> powerLevels?.eventsDefault ?: EVENTS_DEFAULT
                    else -> throw IllegalArgumentException("eventClass $eventClass does not match any event in mappings")
                }
            userPowerLevel >= sendEventPowerLevel
        }

    override fun canSetPowerLevelToMax(
        roomId: RoomId,
        userId: UserId
    ): Flow<Long?> {
        return combine(
            getPowerLevel(roomId, ownUserId),
            getPowerLevel(roomId, userId),
            roomStateStore.getContentByStateKey<PowerLevelsEventContent>(roomId),
        ) { ownPowerLevel, oldOtherUserPowerLevel, powerLevels ->
            when {
                (powerLevels?.events?.get<PowerLevelsEventContent>() ?: powerLevels?.stateDefault
                ?: STATE_DEFAULT) > ownPowerLevel -> null

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
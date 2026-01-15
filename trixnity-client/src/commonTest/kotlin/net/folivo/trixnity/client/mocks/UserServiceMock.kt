package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.client.store.UserPresence
import net.folivo.trixnity.client.user.PowerLevel
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import kotlin.reflect.KClass

class UserServiceMock : UserService {
    val loadMembersCalled = MutableStateFlow<RoomId?>(null)
    override suspend fun loadMembers(roomId: RoomId, wait: Boolean) {
        loadMembersCalled.value = roomId
    }

    override fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>> {
        throw NotImplementedError()
    }

    val roomUsers: MutableMap<Pair<UserId, RoomId>, Flow<RoomUser?>> = mutableMapOf()
    override fun getById(roomId: RoomId, userId: UserId): Flow<RoomUser?> {
        return roomUsers[userId to roomId] ?: flowOf(null)
    }

    override fun getAllReceipts(roomId: RoomId): Flow<Map<UserId, Flow<RoomUserReceipts?>>> {
        throw NotImplementedError()
    }

    override fun getReceiptsById(roomId: RoomId, userId: UserId): Flow<RoomUserReceipts?> {
        throw NotImplementedError()
    }

    override fun canKickUser(
        roomId: RoomId,
        userId: UserId
    ): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun canBanUser(
        roomId: RoomId,
        userId: UserId
    ): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun canUnbanUser(
        roomId: RoomId,
        userId: UserId
    ): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun canInviteUser(
        roomId: RoomId,
        userId: UserId
    ): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun canInvite(roomId: RoomId): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun canRedactEvent(roomId: RoomId, eventId: EventId): Flow<Boolean> {
        throw NotImplementedError()
    }

    val canSendEvent = mutableMapOf<Pair<RoomId, KClass<out RoomEventContent>>, Flow<Boolean>>()
    override fun canSendEvent(roomId: RoomId, eventClass: KClass<out RoomEventContent>): Flow<Boolean> {
        return canSendEvent[roomId to eventClass] ?: MutableStateFlow(true)
    }

    override fun canSendEvent(roomId: RoomId, eventContent: RoomEventContent): Flow<Boolean> {
        return canSendEvent.entries.find { it.key.first == roomId && it.key.second.isInstance(eventContent) }
            ?.value
            ?: MutableStateFlow(true)
    }

    override fun getPowerLevel(roomId: RoomId, userId: UserId): Flow<PowerLevel> {
        throw NotImplementedError()
    }

    override fun getPowerLevel(
        userId: UserId,
        createEvent: ClientEvent.StateBaseEvent<CreateEventContent>,
        powerLevelsEventContent: PowerLevelsEventContent?
    ): PowerLevel {
        throw NotImplementedError()
    }

    override fun canSetPowerLevelToMax(
        roomId: RoomId,
        userId: UserId
    ): Flow<PowerLevel.User?> {
        throw NotImplementedError()
    }

    override fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        throw NotImplementedError()
    }

    override fun getPresence(userId: UserId): Flow<UserPresence?> {
        throw NotImplementedError()
    }
}
package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import kotlin.reflect.KClass

class UserServiceMock : UserService {
    override val userPresence: StateFlow<Map<UserId, PresenceEventContent>>
        get() = throw NotImplementedError()

    val loadMembersCalled = MutableStateFlow<RoomId?>(null)
    override suspend fun loadMembers(roomId: RoomId, wait: Boolean) {
        loadMembersCalled.value = roomId
    }

    override fun getAll(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>?> {
        throw NotImplementedError()
    }


    override fun getById(roomId: RoomId, userId: UserId): Flow<RoomUser?> {
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

    @Deprecated("use canSendEvent instead", ReplaceWith("canSendEvent(roomId, RoomMessageEventContent::class)"))
    override fun canSendMessages(roomId: RoomId): Flow<Boolean> {
        TODO("Not yet implemented")
    }

    override fun canSendEvent(roomId: RoomId, eventClass: KClass<out EventContent>): Flow<Boolean> {
        TODO("Not yet implemented")
    }

    override fun getPowerLevel(roomId: RoomId, userId: UserId): Flow<Int> {
        throw NotImplementedError()
    }

    override fun getPowerLevel(
        userId: UserId,
        powerLevelsEventContent: PowerLevelsEventContent?,
        createEventContent: CreateEventContent?
    ): Int {
        throw NotImplementedError()
    }

    override fun canSetPowerLevelToMax(
        roomId: RoomId,
        userId: UserId
    ): Flow<Int?> {
        throw NotImplementedError()
    }

    override fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        throw NotImplementedError()
    }

}
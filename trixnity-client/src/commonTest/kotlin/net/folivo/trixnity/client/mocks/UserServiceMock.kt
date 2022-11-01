package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
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

    override fun getAll(roomId: RoomId): Flow<Set<RoomUser>?> {
        throw NotImplementedError()
    }


    override fun getById(userId: UserId, roomId: RoomId): Flow<RoomUser?> {
        throw NotImplementedError()
    }

    override fun canKickUser(
        userId: UserId,
        roomId: RoomId
    ): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun canBanUser(
        userId: UserId,
        roomId: RoomId
    ): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun canUnbanUser(
        userId: UserId,
        roomId: RoomId
    ): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun canInviteUser(
        userId: UserId,
        roomId: RoomId
    ): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun canInvite(roomId: RoomId): Flow<Boolean> {
        throw NotImplementedError()
    }

    override fun getPowerLevel(userId: UserId, roomId: RoomId): Flow<Int> {
        throw NotImplementedError()
    }

    override fun getPowerLevel(
        userId: UserId,
        powerLevelsEventContent: PowerLevelsEventContent?,
        createEventContent: CreateEventContent?
    ): Int {
        throw NotImplementedError()
    }

    override fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        throw NotImplementedError()
    }

}
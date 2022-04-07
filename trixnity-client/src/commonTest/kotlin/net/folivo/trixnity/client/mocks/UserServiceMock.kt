package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import kotlin.reflect.KClass

class UserServiceMock : IUserService {
    override val userPresence: StateFlow<Map<UserId, PresenceEventContent>>
        get() = throw NotImplementedError()

    override fun loadMembers(roomId: RoomId) {
        throw NotImplementedError()
    }

    override suspend fun getAll(roomId: RoomId, scope: CoroutineScope): StateFlow<Set<RoomUser>?> {
        throw NotImplementedError()
    }

    override suspend fun getAll(roomId: RoomId): Set<RoomUser>? {
        throw NotImplementedError()
    }

    override suspend fun getById(userId: UserId, roomId: RoomId, scope: CoroutineScope): StateFlow<RoomUser?> {
        throw NotImplementedError()
    }

    override suspend fun getById(userId: UserId, roomId: RoomId): RoomUser? {
        throw NotImplementedError()
    }

    override suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
        scope: CoroutineScope
    ): StateFlow<C?> {
        throw NotImplementedError()
    }

    override suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String
    ): C? {
        throw NotImplementedError()
    }
}
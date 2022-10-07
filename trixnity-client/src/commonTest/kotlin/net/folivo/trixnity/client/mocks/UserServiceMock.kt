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
import kotlin.reflect.KClass

class UserServiceMock : UserService {
    override val userPresence: StateFlow<Map<UserId, PresenceEventContent>>
        get() = throw NotImplementedError()

    val loadMembersCalled = MutableStateFlow<RoomId?>(null)
    override suspend fun loadMembers(roomId: RoomId, wait: Boolean) {
        loadMembersCalled.value = roomId
    }

    override suspend fun getAll(roomId: RoomId): Flow<Set<RoomUser>?> {
        throw NotImplementedError()
    }


    override suspend fun getById(userId: UserId, roomId: RoomId): Flow<RoomUser?> {
        throw NotImplementedError()
    }

    override suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        throw NotImplementedError()
    }

}
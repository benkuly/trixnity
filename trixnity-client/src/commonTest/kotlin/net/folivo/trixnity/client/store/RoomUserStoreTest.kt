package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.repository.InMemoryRoomUserReceiptsRepository
import net.folivo.trixnity.client.store.repository.InMemoryRoomUserRepository
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN

class RoomUserStoreTest : ShouldSpec({
    timeout = 60_000
    lateinit var roomUserRepository: RoomUserRepository
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomUserStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        roomUserRepository = InMemoryRoomUserRepository()
        cut = RoomUserStore(
            roomUserRepository,
            InMemoryRoomUserReceiptsRepository(),
            RepositoryTransactionManagerMock(),
            MatrixClientConfiguration(),
            storeScope
        )
    }
    afterTest {
        storeScope.cancel()
    }

    val roomId = RoomId("room", "server")
    val aliceId = UserId("alice", "server")
    val bobId = UserId("bob", "server")
    val aliceUser = RoomUser(
        roomId = roomId,
        userId = aliceId,
        name = "A",
        event = StateEvent(
            content = MemberEventContent(displayName = "A", membership = JOIN),
            id = EventId("event1"),
            sender = aliceId,
            roomId = roomId,
            originTimestamp = 1,
            stateKey = aliceId.full
        )
    )
    val bobUser = RoomUser(
        roomId = roomId,
        userId = bobId,
        name = "A",
        event = StateEvent(
            content = MemberEventContent(displayName = "A", membership = JOIN),
            id = EventId("event2"),
            sender = bobId,
            roomId = roomId,
            originTimestamp = 1,
            stateKey = bobId.full
        )
    )

    context("getAll") {
        should("get all users of a room") {
            val scope = CoroutineScope(Dispatchers.Default)

            roomUserRepository.save(roomId, aliceId, aliceUser)
            roomUserRepository.save(roomId, bobId, bobUser)

            cut.getAll(roomId).flatten().first()?.values shouldContainExactly listOf(aliceUser, bobUser)

            scope.cancel()
        }
    }
})
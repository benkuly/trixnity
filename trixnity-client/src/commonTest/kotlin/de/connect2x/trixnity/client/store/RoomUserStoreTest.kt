package de.connect2x.trixnity.client.store

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.mocks.RepositoryTransactionManagerMock
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.InMemoryRoomUserReceiptsRepository
import de.connect2x.trixnity.client.store.repository.InMemoryRoomUserRepository
import de.connect2x.trixnity.client.store.repository.RoomUserRepository
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership.JOIN
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import kotlin.test.Test

class RoomUserStoreTest : TrixnityBaseTest() {

    private val roomUserRepository = InMemoryRoomUserRepository() as RoomUserRepository
    private val cut = RoomUserStore(
        roomUserRepository,
        InMemoryRoomUserReceiptsRepository(),
        RepositoryTransactionManagerMock(),
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    )

    private val roomId = RoomId("!room:server")
    private val aliceId = UserId("alice", "server")
    private val bobId = UserId("bob", "server")
    private val aliceUser = RoomUser(
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
    private val bobUser = RoomUser(
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

    @Test
    fun `getAll » get all users of a room`() = runTest {
        roomUserRepository.save(roomId, aliceId, aliceUser)
        roomUserRepository.save(roomId, bobId, bobUser)

        cut.getAll(roomId).flatten().first().values shouldContainExactly listOf(aliceUser, bobUser)
    }
}
package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.repository.InMemoryRoomUserRepository
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE

class RoomUserStoreTest : ShouldSpec({
    timeout = 60_000
    lateinit var roomUserRepository: RoomUserRepository
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomUserStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        roomUserRepository = InMemoryRoomUserRepository()
        cut = RoomUserStore(roomUserRepository, RepositoryTransactionManagerMock(), MatrixClientConfiguration(), storeScope)
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
    context(RoomUserStore::getByOriginalNameAndMembership.name) {
        should("return matching userIds") {
            val user3 = UserId("user3", "server")
            val user4 = UserId("user4", "server")
            roomUserRepository.save(roomId, aliceId, aliceUser)
            roomUserRepository.save(roomId, bobId, bobUser)
            roomUserRepository.save(
                roomId, user3, RoomUser(
                    roomId = roomId,
                    userId = user3,
                    name = "A",
                    event = StateEvent(
                        content = MemberEventContent(displayName = "A", membership = LEAVE),
                        id = EventId("event3"),
                        sender = user3,
                        roomId = roomId,
                        originTimestamp = 1,
                        stateKey = user3.full
                    )
                )
            )
            roomUserRepository.save(
                roomId, user4, RoomUser(
                    roomId = roomId,
                    userId = user4,
                    name = "AB",
                    event = StateEvent(
                        content = MemberEventContent(displayName = "AB", membership = JOIN),
                        id = EventId("event3"),
                        sender = user4,
                        roomId = roomId,
                        originTimestamp = 1,
                        stateKey = user4.full
                    )
                )
            )

            cut.getByOriginalNameAndMembership("A", setOf(JOIN), roomId) shouldBe setOf(aliceId, bobId)
        }
    }
})
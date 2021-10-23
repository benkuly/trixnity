package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.LEAVE
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings

class RoomStateStoreTest : ShouldSpec({
    val roomStateRepository = mockk<RoomStateRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomStateStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = RoomStateStore(roomStateRepository, DefaultEventContentSerializerMappings, storeScope)
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    val roomId = RoomId("room", "server")
    val event1 = Event.StateEvent(
        MemberEventContent(membership = LEAVE),
        EventId("\$event"),
        UserId("alice", "server"),
        roomId,
        1234,
        stateKey = "@user:server"
    )
    val event2 = Event.StateEvent(
        MemberEventContent(membership = JOIN),
        EventId("\$event"),
        UserId("alice", "server"),
        roomId,
        1234,
        stateKey = "@alice:server"
    )

    context(RoomStateStore::update.name) {
        should("insert event into stateKey map of events") {
            cut.update(event1)
            cut.update(event2)

            coVerifyAll {
                roomStateRepository.saveByStateKey(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    "@user:server",
                    event1
                )
                roomStateRepository.saveByStateKey(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    "@alice:server",
                    event2
                )
            }
        }
    }
    context("get") {
        should("return matching event") {
            coEvery {
                roomStateRepository.get(RoomStateRepositoryKey(roomId, "m.room.member"))
            } returns mapOf("@user:server" to event1)
            cut.get<MemberEventContent>(roomId) shouldBe mapOf("@user:server" to event1)
        }
        should("prefer cache") {
            coEvery {
                roomStateRepository.get(RoomStateRepositoryKey(roomId, "m.room.member"))
            } returns mapOf("@user:server" to event1)
            cut.get<MemberEventContent>(roomId) shouldBe mapOf("@user:server" to event1)
            cut.get<MemberEventContent>(roomId) shouldBe mapOf("@user:server" to event1)
            coVerify(exactly = 1) {
                roomStateRepository.get(RoomStateRepositoryKey(roomId, "m.room.member"))
            }
        }
    }
    context("getByStateKey") {
        should("return matching event") {
            coEvery {
                roomStateRepository.getByStateKey(RoomStateRepositoryKey(roomId, "m.room.member"), "@user:server")
            } returns event1
            cut.getByStateKey<MemberEventContent>(roomId, "@user:server") shouldBe event1
        }
        should("prefer cache") {
            coEvery {
                roomStateRepository.getByStateKey(RoomStateRepositoryKey(roomId, "m.room.member"), "@user:server")
            } returns event1
            cut.getByStateKey<MemberEventContent>(roomId, "@user:server") shouldBe event1
            cut.getByStateKey<MemberEventContent>(roomId, "@user:server") shouldBe event1
            coVerify(exactly = 1) {
                roomStateRepository.getByStateKey(RoomStateRepositoryKey(roomId, "m.room.member"), "@user:server")
            }
        }
    }
})
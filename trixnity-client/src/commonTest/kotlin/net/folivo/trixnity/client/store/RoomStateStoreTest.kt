package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.NoopRepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.InMemoryRoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings

class RoomStateStoreTest : ShouldSpec({
    timeout = 60_000
    lateinit var roomStateRepository: RoomStateRepository
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomStateStore

    beforeTest {
        roomStateRepository = InMemoryRoomStateRepository()
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = RoomStateStore(
            roomStateRepository,
            NoopRepositoryTransactionManager,
            DefaultEventContentSerializerMappings,
            storeScope
        )
    }
    afterTest {
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

            roomStateRepository.getBySecondKey(
                RoomStateRepositoryKey(roomId, "m.room.member"),
                "@user:server"
            ) shouldBe event1
            roomStateRepository.getBySecondKey(
                RoomStateRepositoryKey(roomId, "m.room.member"),
                "@alice:server"
            ) shouldBe event2
        }
        context("skipWhenAlreadyPresent is true") {
            should("only change, when already present") {
                cut.update(event1, true)
                cut.update(event1.copy(originTimestamp = 0), true)
                roomStateRepository.getBySecondKey(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    "@user:server"
                ) shouldBe event1
            }
        }

    }
    context("get") {
        context("without scope") {
            should("return matching event") {
                roomStateRepository.save(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    mapOf("@user:server" to event1)
                )
                cut.get<MemberEventContent>(roomId).first() shouldBe mapOf("@user:server" to event1)
            }
            should("prefer cache") {
                roomStateRepository.save(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    mapOf("@user:server" to event1)
                )
                cut.get<MemberEventContent>(roomId).first() shouldBe mapOf("@user:server" to event1)
                roomStateRepository.save(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    mapOf("@user:server" to event1.copy(originTimestamp = 0))
                )
                cut.get<MemberEventContent>(roomId).first() shouldBe mapOf("@user:server" to event1)
            }
            should("ignore unknown state event") {
                roomStateRepository.save(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    mapOf(
                        "@user:server" to event1, "@bob:server" to Event.StateEvent(
                            UnknownStateEventContent(JsonObject(mapOf()), "m.room.member"),
                            EventId("\$event"),
                            UserId("alice", "server"),
                            roomId,
                            1234,
                            stateKey = "@bob:server"
                        )
                    )
                )
                cut.get<MemberEventContent>(roomId).first() shouldBe mapOf(
                    "@user:server" to event1,
                    "@bob:server" to null
                )
            }
        }
        context("with scope") {
            should("ignore unknown state event") {
                roomStateRepository.save(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    mapOf("@user:server" to event1)
                )
                val scope = CoroutineScope(Dispatchers.Default)
                val result = cut.get<MemberEventContent>(roomId).shareIn(scope, SharingStarted.Eagerly, 3)
                result.first { it?.size == 1 }
                cut.update(
                    Event.StateEvent(
                        UnknownStateEventContent(JsonObject(mapOf()), "m.room.member"),
                        EventId("\$event"),
                        UserId("alice", "server"),
                        roomId,
                        1234,
                        stateKey = "@bob:server"
                    )
                )
                result.first { it?.size == 2 }
                result.replayCache shouldBe listOf(
                    mapOf("@user:server" to event1),
                    mapOf("@user:server" to event1, "@bob:server" to null)
                )
            }
        }
    }
    context("getByStateKey") {
        context("without scope") {
            should("return matching event") {
                roomStateRepository.save(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    mapOf("@user:server" to event1)
                )
                cut.getByStateKey<MemberEventContent>(roomId, "@user:server").first() shouldBe event1
            }
            should("prefer cache") {
                roomStateRepository.save(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    mapOf("@user:server" to event1)
                )
                cut.getByStateKey<MemberEventContent>(roomId, "@user:server").first() shouldBe event1
                roomStateRepository.save(
                    RoomStateRepositoryKey(roomId, "m.room.member"),
                    mapOf("@user:server" to event1.copy(originTimestamp = 0))
                )
                cut.getByStateKey<MemberEventContent>(roomId, "@user:server").first() shouldBe event1
            }
            should("ignore unknown state event") {
                cut.update(
                    Event.StateEvent(
                        UnknownStateEventContent(JsonObject(mapOf()), "m.room.member"),
                        EventId("\$event"),
                        UserId("alice", "server"),
                        roomId,
                        1234,
                        stateKey = "@user:server"
                    )
                )
                cut.getByStateKey<MemberEventContent>(roomId, "@user:server").first() shouldBe null
            }
        }
        context("with scope") {
            should("ignore unknown state event") {
                cut.update(event1)

                val scope = CoroutineScope(Dispatchers.Default)
                val result = cut.getByStateKey<MemberEventContent>(roomId, "@user:server")
                    .shareIn(scope, SharingStarted.Eagerly, 3)
                result.first { it != null }
                cut.update(
                    Event.StateEvent(
                        UnknownStateEventContent(JsonObject(mapOf()), "m.room.member"),
                        EventId("\$event"),
                        UserId("alice", "server"),
                        roomId,
                        1234,
                        stateKey = "@user:server"
                    )
                )
                result.first { it == null }
                result.replayCache shouldBe listOf(event1, null)
                scope.cancel()
            }
        }
    }
})
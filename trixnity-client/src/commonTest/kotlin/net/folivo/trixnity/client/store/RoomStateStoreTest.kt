package net.folivo.trixnity.client.store

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.flattenNotNull
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.InMemoryRoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RoomStateStoreTest : TrixnityBaseTest() {
    private val roomStateRepository = InMemoryRoomStateRepository() as RoomStateRepository
    private val cut = RoomStateStore(
        roomStateRepository,
        RepositoryTransactionManagerMock(),
        DefaultEventContentSerializerMappings,
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    )

    private val roomId = RoomId("!room:server")
    private val event1 = StateEvent(
        MemberEventContent(membership = LEAVE),
        EventId("\$event"),
        UserId("alice", "server"),
        roomId,
        1234,
        stateKey = "@user:server"
    )
    private val event2 = StateEvent(
        MemberEventContent(membership = JOIN),
        EventId("\$event"),
        UserId("alice", "server"),
        roomId,
        1234,
        stateKey = "@alice:server"
    )

    @Test
    fun `save » insert event into stateKey map of events`() = runTest {
        cut.save(event1)
        cut.save(event2)

        roomStateRepository.get(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server"
        ) shouldBe event1
        roomStateRepository.get(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@alice:server"
        ) shouldBe event2
    }

    @Test
    fun `get » without scope » return matching event`() = runTest {
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server",
            event1
        )
        cut.get<MemberEventContent>(roomId).flatten().first() shouldBe mapOf("@user:server" to event1)
    }

    @Test
    fun `get » without scope » prefer cache`() = runTest {
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server",
            event1
        )
        cut.get<MemberEventContent>(roomId).flatten().first() shouldBe mapOf("@user:server" to event1)
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server",
            event1.copy(originTimestamp = 0)
        )
        cut.get<MemberEventContent>(roomId).flatten().first() shouldBe mapOf("@user:server" to event1)
    }

    @Test
    fun `get » without scope » ignore unknown state event`() = runTest {
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server",
            event1,
        )
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@bob:server",
            StateEvent(
                UnknownEventContent(JsonObject(mapOf()), "m.room.member"),
                EventId("\$event"),
                UserId("alice", "server"),
                roomId,
                1234,
                stateKey = "@bob:server"
            )
        )
        cut.get<MemberEventContent>(roomId).flattenNotNull().first() shouldBe mapOf(
            "@user:server" to event1,
        )
    }

    @Test
    fun `get » with scope » ignore unknown state event`() = runTest {
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server",
            event1
        )
        val result = cut.get<MemberEventContent>(roomId).flatten(throttle = Duration.ZERO).stateIn(backgroundScope)
        result.value shouldBe mapOf(
            "@user:server" to event1,
        )
        cut.save(
            StateEvent(
                UnknownEventContent(JsonObject(mapOf()), "m.room.member"),
                EventId("\$event"),
                UserId("alice", "server"),
                roomId,
                1234,
                stateKey = "@bob:server"
            )
        )
        delay(100.milliseconds)
        result.value shouldBe mapOf(
            "@user:server" to event1,
            "@bob:server" to null
        )
    }

    @Test
    fun `getByStateKey » without scope » return matching event`() = runTest {
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server",
            event1
        )
        cut.getByStateKey<MemberEventContent>(roomId, "@user:server").first() shouldBe event1
    }

    @Test
    fun `getByStateKey » without scope » return matching content`() = runTest {
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server",
            event1
        )
        cut.getContentByStateKey<MemberEventContent>(roomId, "@user:server").first() shouldBe event1.content
    }

    @Test
    fun `getByStateKey » without scope » prefer cache`() = runTest {
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server",
            event1
        )
        cut.getByStateKey<MemberEventContent>(roomId, "@user:server").first() shouldBe event1
        roomStateRepository.save(
            RoomStateRepositoryKey(roomId, "m.room.member"),
            "@user:server",
            event1.copy(originTimestamp = 0)
        )
        cut.getByStateKey<MemberEventContent>(roomId, "@user:server").first() shouldBe event1
    }

    @Test
    fun `getByStateKey » without scope » ignore unknown state event`() = runTest {
        cut.save(
            StateEvent(
                UnknownEventContent(JsonObject(mapOf()), "m.room.member"),
                EventId("\$event"),
                UserId("alice", "server"),
                roomId,
                1234,
                stateKey = "@user:server"
            )
        )
        cut.getByStateKey<MemberEventContent>(roomId, "@user:server").first() shouldBe null
    }

    @Test
    fun `getByStateKey » with scope » ignore unknown state event`() = runTest {
        cut.save(event1)

        val result = cut.getByStateKey<MemberEventContent>(roomId, "@user:server")
            .shareIn(backgroundScope, SharingStarted.Eagerly, 3)
        result.first { it != null }
        cut.save(
            StateEvent(
                UnknownEventContent(JsonObject(mapOf()), "m.room.member"),
                EventId("\$event"),
                UserId("alice", "server"),
                roomId,
                1234,
                stateKey = "@user:server"
            )
        )
        result.first { it == null }
        result.replayCache shouldBe listOf(event1, null)
    }

    @Test
    fun `getByStateKey » return empty event contents`() = runTest {
        val event =
            StateEvent(
                AvatarEventContent(),
                EventId("\$event"),
                UserId("alice", "server"),
                roomId,
                1234,
                stateKey = ""
            )
        cut.save(event)
        roomStateRepository.get(RoomStateRepositoryKey(roomId, "m.room.avatar"), "") shouldBe event
        cut.getByStateKey<AvatarEventContent>(roomId).first()?.content shouldBe AvatarEventContent()
    }
}
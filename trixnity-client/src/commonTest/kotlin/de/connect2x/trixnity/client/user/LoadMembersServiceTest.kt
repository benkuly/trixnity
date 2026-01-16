package de.connect2x.trixnity.client.user

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.room.GetMembers
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.Event
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership.JOIN
import de.connect2x.trixnity.core.model.events.m.room.Membership.LEAVE
import de.connect2x.trixnity.core.subscribeContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LoadMembersServiceTest : TrixnityBaseTest() {
    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val roomId = simpleRoom.roomId

    private val aliceEvent = StateEvent(
        MemberEventContent(membership = JOIN),
        EventId("\$event1"),
        alice,
        roomId,
        1234,
        stateKey = alice.full
    )
    private val bobEvent = StateEvent(
        MemberEventContent(membership = JOIN),
        EventId("\$event2"),
        bob,
        roomId,
        1234,
        stateKey = bob.full
    )

    private val roomStore = getInMemoryRoomStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(config = apiConfig)

    private val currentSyncState = MutableStateFlow(SyncState.RUNNING)

    private val cut = LoadMembersServiceImpl(
        roomStore = roomStore,
        lazyMemberEventHandlers = listOf(),
        currentSyncState = CurrentSyncState(currentSyncState),
        api = api,
        scope = testScope.backgroundScope,
    )

    @Test
    fun `invoke » do nothing when members already loaded`() = runTest {
        val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = true)
        roomStore.update(roomId) { storedRoom }
        cut(roomId, true)

        continually(500.milliseconds) {
            roomStore.get(roomId).first() shouldBe storedRoom
        }
    }

    @Test
    fun `invoke » load members`() = runTest {
        apiConfig.endpoints {
            matrixJsonEndpoint(GetMembers(roomId, notMembership = LEAVE)) {
                GetMembers.Response(
                    setOf(aliceEvent, bobEvent)
                )
            }
        }
        val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = false)
        roomStore.update(roomId) { storedRoom }
        val newMemberEvents = mutableListOf<Event<MemberEventContent>>()
        api.sync.subscribeContent<MemberEventContent> {
            newMemberEvents += it
        }
        cut(roomId, true)
        roomStore.get(roomId).first { it?.membersLoaded == true }?.membersLoaded shouldBe true
        newMemberEvents shouldContainExactly listOf(aliceEvent, bobEvent)
    }

    @Test
    fun `invoke » do nothing when room not loaded yet`() = runTest {
        apiConfig.endpoints {
            matrixJsonEndpoint(GetMembers(roomId, notMembership = LEAVE)) {
                GetMembers.Response(
                    setOf(aliceEvent, bobEvent)
                )
            }
        }
        val newMemberEvents = mutableListOf<Event<MemberEventContent>>()
        api.sync.subscribeContent<MemberEventContent> {
            newMemberEvents += it
        }

        val loadMembers = launch {
            cut(roomId, true)
        }

        delay(1.seconds)
        loadMembers.isCompleted shouldBe false

        roomStore.update(roomId) { simpleRoom.copy(roomId = roomId, membersLoaded = false) }

        delay(1.seconds)
        loadMembers.join()
        roomStore.get(roomId).first { it?.membersLoaded == true }?.membersLoaded shouldBe true
        loadMembers.isCompleted shouldBe true
        newMemberEvents shouldContainExactly listOf(aliceEvent, bobEvent)
    }

    @Test
    fun `invoke » do not suspend infinitely on MatrixServerException`() = runTest {
        val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = false)
        roomStore.update(roomId) { storedRoom }
        apiConfig.endpoints {
            matrixJsonEndpoint(GetMembers(roomId, notMembership = LEAVE)) {
                throw MatrixServerException(HttpStatusCode.Unauthorized, ErrorResponse.Unauthorized("not allowed"))
            }
        }
        val newMemberEvents = mutableListOf<Event<MemberEventContent>>()
        api.sync.subscribeContent<MemberEventContent> {
            newMemberEvents += it
        }

        val loadMembers = launch {
            cut(roomId, true)
        }
        delay(1.seconds)
        loadMembers.join()
        roomStore.get(roomId).first()?.membersLoaded shouldBe false
        loadMembers.isCompleted shouldBe true
    }
}
package net.folivo.trixnity.client.user

import io.kotest.assertions.nondeterministic.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetMembers
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.subscribeContent
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.milliseconds

class LoadMembersServiceTest : ShouldSpec({
    timeout = 30_000
    coroutineTestScope = true

    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val roomId = simpleRoom.roomId

    val aliceEvent = StateEvent(
        MemberEventContent(membership = JOIN),
        EventId("\$event1"),
        alice,
        roomId,
        1234,
        stateKey = alice.full
    )
    val bobEvent = StateEvent(
        MemberEventContent(membership = JOIN),
        EventId("\$event2"),
        bob,
        roomId,
        1234,
        stateKey = bob.full
    )

    lateinit var roomStore: RoomStore
    lateinit var scope: CoroutineScope
    lateinit var api: MatrixClientServerApiClient
    lateinit var apiConfig: PortableMockEngineConfig
    val json = createMatrixEventJson()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: LoadMembersServiceImpl

    beforeTest {
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        api = newApi
        apiConfig = newApiConfig
        currentSyncState.value = SyncState.RUNNING
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        cut = LoadMembersServiceImpl(
            roomStore = roomStore,
            lazyMemberEventHandlers = listOf(),
            currentSyncState = CurrentSyncState(currentSyncState),
            api = api,
            scope = scope,
        )
    }

    afterTest {
        scope.cancel()
    }

    context(LoadMembersService::invoke.name) {
        should("do nothing when members already loaded") {
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = true)
            roomStore.update(roomId) { storedRoom }
            cut(roomId, true)
            continually(500.milliseconds) {
                roomStore.get(roomId).first() shouldBe storedRoom
            }
        }
        should("load members") {
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
        should("retry when room not loaded yet") {
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

            val loadMembers = launch{
                cut(roomId, true)
            }

            delay(10000)

            loadMembers.isCompleted shouldBe false

            roomStore.update(roomId) { simpleRoom.copy(roomId = roomId, membersLoaded = false) }
            roomStore.get(roomId).first { it?.membersLoaded == true }
            newMemberEvents shouldContainExactly listOf(aliceEvent, bobEvent)
        }
    }
})
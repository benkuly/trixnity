package net.folivo.trixnity.client.user

import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetMembers
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.milliseconds

class UserServiceTest : ShouldSpec({
    timeout = 30_000
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val roomId = simpleRoom.roomId
    lateinit var roomUserStore: RoomUserStore
    lateinit var roomStore: RoomStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    lateinit var scope: CoroutineScope
    lateinit var api: IMatrixClientServerApiClient
    lateinit var apiConfig: PortableMockEngineConfig
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: UserService

    beforeTest {
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        api = newApi
        apiConfig = newApiConfig
        currentSyncState.value = SyncState.RUNNING
        scope = CoroutineScope(Dispatchers.Default)
        roomUserStore = getInMemoryRoomUserStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        roomStore = getInMemoryRoomStore(scope)
        cut = UserService(
            roomUserStore, roomStore, globalAccountDataStore, api, PresenceEventHandler(api),
            CurrentSyncState(currentSyncState), scope
        )
    }

    afterTest {
        scope.cancel()
    }

    context(UserService::loadMembers.name) {
        should("do nothing when members already loaded") {
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = true)
            roomStore.update(roomId) { storedRoom }
            cut.loadMembers(roomId)
            continually(500.milliseconds) {
                roomStore.get(roomId).first() shouldBe storedRoom
            }
        }
        should("load members") {
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
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, GetMembers(roomId.e(), notMembership = LEAVE)) {
                    GetMembers.Response(
                        setOf(aliceEvent, bobEvent)
                    )
                }
            }
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = false)
            roomStore.update(roomId) { storedRoom }
            val newMemberEvents = mutableListOf<Event<MemberEventContent>>()
            api.sync.subscribe {
                newMemberEvents += it
            }
            cut.loadMembers(roomId)
            roomStore.get(roomId).first { it?.membersLoaded == true }?.membersLoaded shouldBe true
            newMemberEvents shouldContainExactly listOf(aliceEvent, bobEvent)
        }
    }
})
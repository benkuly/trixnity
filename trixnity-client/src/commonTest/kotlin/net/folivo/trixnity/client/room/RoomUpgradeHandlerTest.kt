package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.rooms.JoinRoom
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class RoomUpgradeHandlerTest : ShouldSpec({
    timeout = 10_000
    lateinit var roomStore: RoomStore
    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var config: MatrixClientConfiguration
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()

    lateinit var cut: RoomUpgradeHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        config = MatrixClientConfiguration()
        cut = RoomUpgradeHandler(
            api,
            roomStore,
            config,
        )
    }

    afterTest {
        scope.cancel()
    }

    context(RoomUpgradeHandler::joinUpgradedRooms.name) {
        val roomId1 = RoomId("room1", "server")
        val roomId2 = RoomId("room2", "server")
        val roomId3 = RoomId("room3", "server")

        var joinCalled = false
        beforeTest {
            joinCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(JoinRoom("!room2:server")) {
                    joinCalled = true
                    JoinRoom.Response(roomId2)
                }
            }
        }
        should("join upgraded room") {
            roomStore.update(roomId1) {
                Room(roomId = roomId1, nextRoomId = roomId2)
            }
            roomStore.update(roomId2) {
                Room(roomId = roomId2, previousRoomId = roomId1, membership = INVITE)
            }
            roomStore.getAll().first { it.size == 2 }
            cut.joinUpgradedRooms()

            joinCalled shouldBe true
        }
        should("not join upgraded room when disabled") {
            config.autoJoinUpgradedRooms = false
            roomStore.update(roomId1) {
                Room(roomId = roomId1, nextRoomId = roomId2)
            }
            roomStore.update(roomId2) {
                Room(roomId = roomId2, previousRoomId = roomId1, membership = INVITE)
            }
            roomStore.getAll().first { it.size == 2 }
            cut.joinUpgradedRooms()

            joinCalled shouldBe false
        }
        should("not join upgraded room, when previous room does not match") {
            roomStore.update(roomId1) {
                Room(roomId = roomId1, nextRoomId = roomId3)// nextRoomId does not match!
            }
            roomStore.update(roomId2) {
                Room(roomId = roomId2, previousRoomId = roomId1, membership = INVITE)
            }
            roomStore.getAll().first { it.size == 2 }
            cut.joinUpgradedRooms()

            joinCalled shouldBe false
        }
        should("not join upgraded room, when previous room membership is not JOIN") {
            roomStore.update(roomId1) {
                Room(roomId = roomId1, nextRoomId = roomId2, membership = INVITE) // invite!
            }
            roomStore.update(roomId2) {
                Room(roomId = roomId2, previousRoomId = roomId1, membership = INVITE)
            }
            roomStore.getAll().first { it.size == 2 }
            cut.joinUpgradedRooms()

            joinCalled shouldBe false
        }
    }
})
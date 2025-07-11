package net.folivo.trixnity.client.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.clientserverapi.model.rooms.JoinRoom
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test

class RoomUpgradeHandlerTest : TrixnityBaseTest() {

    private val roomId1 = RoomId("!room1:server")
    private val roomId2 = RoomId("!room2:server")
    private val roomId3 = RoomId("!room3:server")

    private val config = MatrixClientConfiguration()

    private val joinCalled = MutableStateFlow(false)

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(config = apiConfig)

    private val roomStore = getInMemoryRoomStore()
    private val cut = RoomUpgradeHandler(
        api,
        roomStore,
        config,
    )

    init {
        apiConfig.apply {
            endpoints {
                matrixJsonEndpoint(JoinRoom("!room2:server")) {
                    joinCalled.update { true }
                    JoinRoom.Response(roomId2)
                }
            }
        }
    }

    @Test
    fun `joinUpgradedRooms » join upgraded room`() = runTest {
        with(roomStore) {
            update(roomId1) {
                Room(roomId = roomId1, nextRoomId = roomId2)
            }
            update(roomId2) {
                Room(
                    roomId = roomId2,
                    createEventContent = CreateEventContent(
                        predecessor = CreateEventContent.PreviousRoom(roomId1, EventId(""))
                    ),
                    membership = INVITE
                )
            }
            getAll().first { it.size == 2 }
        }

        cut.joinUpgradedRooms()

        joinCalled.value shouldBe true
    }

    @Test
    fun `joinUpgradedRooms » not join upgraded room when disabled`() = runTest {
        config.autoJoinUpgradedRooms = false

        with(roomStore) {
            update(roomId1) {
                Room(roomId = roomId1, nextRoomId = roomId2)
            }
            update(roomId2) {
                Room(
                    roomId = roomId2,
                    createEventContent = CreateEventContent(
                        predecessor = CreateEventContent.PreviousRoom(roomId1, EventId(""))
                    ),
                    membership = INVITE
                )
            }
            getAll().first { it.size == 2 }
        }

        cut.joinUpgradedRooms()

        joinCalled.value shouldBe false
    }

    @Test
    fun `joinUpgradedRooms » not join upgraded room when previous room does not match`() = runTest {
        with(roomStore) {
            update(roomId1) {
                Room(roomId = roomId1, nextRoomId = roomId3)// nextRoomId does not match!
            }
            update(roomId2) {
                Room(
                    roomId = roomId2,
                    createEventContent = CreateEventContent(
                        predecessor = CreateEventContent.PreviousRoom(roomId1, EventId(""))
                    ),
                    membership = INVITE
                )
            }
            getAll().first { it.size == 2 }
        }

        cut.joinUpgradedRooms()

        joinCalled.value shouldBe false
    }

    @Test
    fun `joinUpgradedRooms » not join upgraded room when previous room membership is not JOIN`() = runTest {
        with(roomStore) {
            update(roomId1) {
                Room(roomId = roomId1, nextRoomId = roomId2, membership = INVITE) // invite!
            }
            update(roomId2) {
                Room(
                    roomId = roomId2,
                    createEventContent = CreateEventContent(
                        predecessor = CreateEventContent.PreviousRoom(roomId1, EventId(""))
                    ),
                    membership = INVITE
                )
            }
            getAll().first { it.size == 2 }
        }

        cut.joinUpgradedRooms()

        joinCalled.value shouldBe false
    }

}
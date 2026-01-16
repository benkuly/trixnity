package de.connect2x.trixnity.client.room

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.clientserverapi.model.room.JoinRoom
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.TombstoneEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.BeforeTest
import kotlin.test.Test

class RoomUpgradeHandlerTest : TrixnityBaseTest() {

    private val oldRoom = RoomId("!room1:server")
    private val upgradedRoom = RoomId("!room2:server")

    private val config = MatrixClientConfiguration()

    private val joinCalled = MutableStateFlow(false)
    private var exception: Exception? = null

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
                matrixJsonEndpoint(JoinRoom(upgradedRoom.full)) {
                    joinCalled.update { true }
                    exception?.let { throw it }
                    JoinRoom.Response(upgradedRoom)
                }
            }
        }
    }

    private fun tombstoneEvent() =
        ClientEvent.RoomEvent.StateEvent(
            TombstoneEventContent("room upgrade", upgradedRoom),
            EventId("!tombstone"),
            UserId("@alice:server"),
            oldRoom,
            1234,
            stateKey = "",
        )

    @BeforeTest
    fun setup() {
        exception = null
    }

    @Test
    fun `joinUpgradedRooms » room unknown » join`() = runTest {
        cut.joinUpgradedRooms(listOf(tombstoneEvent()))

        joinCalled.value shouldBe true
    }

    @Test
    fun `joinUpgradedRooms » room invite » join`() = runTest {
        with(roomStore) {
            update(upgradedRoom) {
                Room(
                    roomId = upgradedRoom,
                    createEventContent = CreateEventContent(
                        predecessor = CreateEventContent.PreviousRoom(oldRoom, EventId(""))
                    ),
                    membership = Membership.INVITE
                )
            }
        }
        cut.joinUpgradedRooms(listOf(tombstoneEvent()))

        joinCalled.value shouldBe true
    }

    @Test
    fun `joinUpgradedRooms » room already known » not join`() = runTest {
        with(roomStore) {
            update(upgradedRoom) {
                Room(
                    roomId = upgradedRoom,
                    createEventContent = CreateEventContent(
                        predecessor = CreateEventContent.PreviousRoom(oldRoom, EventId(""))
                    ),
                    membership = Membership.JOIN
                )
            }
        }

        cut.joinUpgradedRooms(listOf(tombstoneEvent()))

        joinCalled.value shouldBe false
    }

    @Test
    fun `joinUpgradedRooms » disabled » not join`() = runTest {
        config.autoJoinUpgradedRooms = false

        cut.joinUpgradedRooms(listOf(tombstoneEvent()))

        joinCalled.value shouldBe false
    }

    @Test
    fun `joinUpgradedRooms » ignore on MatrixServerException`() = runTest {
        exception = MatrixServerException(HttpStatusCode.Unauthorized, ErrorResponse.Unauthorized("no rights"))
        cut.joinUpgradedRooms(listOf(tombstoneEvent()))
        joinCalled.value shouldBe true
    }

    @Test
    fun `joinUpgradedRooms » throw on other exceptions`() = runTest {
        exception = RuntimeException("http timeout")
        shouldThrow<RuntimeException> {
            cut.joinUpgradedRooms(listOf(tombstoneEvent()))
        }
        joinCalled.value shouldBe true
    }
}
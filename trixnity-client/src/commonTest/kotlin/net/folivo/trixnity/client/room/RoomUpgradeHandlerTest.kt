package net.folivo.trixnity.client.room

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.clientserverapi.model.rooms.JoinRoom
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.TombstoneEventContent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
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
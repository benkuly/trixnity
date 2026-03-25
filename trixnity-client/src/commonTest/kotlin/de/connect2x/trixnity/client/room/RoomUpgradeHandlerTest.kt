package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.getInMemoryRoomStateStore
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
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.TombstoneEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RoomUpgradeHandlerTest : TrixnityBaseTest() {

    private val oldRoom = RoomId("!room1:server")
    private val upgradedRoom = RoomId("!room2:server")

    private val config = MatrixClientConfiguration()

    private val joinCalled = MutableStateFlow(0)
    private var exception: Exception? = null

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(config = apiConfig)

    private val roomStore = getInMemoryRoomStore()
    private val roomStateStore = getInMemoryRoomStateStore()
    private val cut = RoomUpgradeHandler(
        api,
        roomStore,
        roomStateStore,
        config,
    )

    init {
        apiConfig.apply {
            endpoints {
                matrixJsonEndpoint(JoinRoom(upgradedRoom)) {
                    joinCalled.update { it + 1 }
                    exception?.let {
                        exception = null
                        throw it
                    }
                    JoinRoom.Response(upgradedRoom)
                }
            }
        }
    }

    private fun tombstoneEvent(): ClientEvent.RoomEvent.StateEvent<TombstoneEventContent> =
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
        joinCalled.value = 0
        config.autoJoinUpgradedRooms = true
    }

    @Test
    fun `tombstone from sync - upgrade using join via`() = runTest {
        roomStore.update(upgradedRoom) {
            Room(
                upgradedRoom,
                membership = Membership.INVITE
            )
        }
        roomStateStore.save(tombstoneEvent())

        backgroundScope.launch { cut.joinUpgradedRooms() }

        delay(5.minutes + 11.seconds) // skip initial upgrade
        joinCalled.value = 0
        cut.onTombstone(listOf(tombstoneEvent()))
        delay(11.seconds)
        joinCalled.value shouldBe 1
    }

    @Test
    fun `tombstone from sync - exception - retry`() = runTest {
        roomStore.update(upgradedRoom) {
            Room(
                upgradedRoom,
                membership = Membership.INVITE
            )
        }
        roomStateStore.save(tombstoneEvent())
        exception = MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown(""))

        backgroundScope.launch { cut.joinUpgradedRooms() }

        delay(5.minutes + 11.seconds) // skip initial upgrade
        joinCalled.value = 0
        cut.onTombstone(listOf(tombstoneEvent()))
        delay(11.seconds)
        joinCalled.value shouldBe 1
        delay(11.seconds)
        joinCalled.value shouldBe 2
    }

    @Test
    fun `tombstone from sync - 4xx exception - no retry`() = runTest {
        roomStore.update(upgradedRoom) {
            Room(
                upgradedRoom,
                membership = Membership.INVITE
            )
        }
        roomStateStore.save(tombstoneEvent())
        exception = MatrixServerException(HttpStatusCode.Forbidden, ErrorResponse.Unknown(""))

        backgroundScope.launch { cut.joinUpgradedRooms() }

        delay(5.minutes + 11.seconds) // skip initial upgrade
        joinCalled.value = 0
        cut.onTombstone(listOf(tombstoneEvent()))
        delay(11.seconds)
        joinCalled.value shouldBe 1
    }

    @Test
    fun `tombstone from sync - upgrade is present and not invite - do nothing`() = runTest {
        (Membership.entries - Membership.INVITE).forEach { membership ->
            withClue("membership: $membership") {
                roomStore.update(upgradedRoom) {
                    Room(
                        upgradedRoom,
                        membership = membership
                    )
                }
                roomStateStore.save(tombstoneEvent())

                backgroundScope.launch { cut.joinUpgradedRooms() }

                delay(5.minutes + 11.seconds) // skip initial upgrade
                joinCalled.value = 0
                cut.onTombstone(listOf(tombstoneEvent()))
                delay(11.seconds)
                joinCalled.value shouldBe 0
            }
        }
    }

    @Test
    fun `disabled - do nothing`() = runTest {
        config.autoJoinUpgradedRooms = false
        roomStore.update(upgradedRoom) {
            Room(
                upgradedRoom,
                membership = Membership.INVITE
            )
        }
        roomStateStore.save(tombstoneEvent())

        backgroundScope.launch { cut.joinUpgradedRooms() }

        delay(5.minutes + 11.seconds) // skip initial upgrade
        joinCalled.value = 0
        cut.onTombstone(listOf(tombstoneEvent()))
        delay(11.seconds)
        joinCalled.value shouldBe 0
    }

    @Test
    fun `tombstone from room list - old room is JOIN - upgrade`() = runTest {
        roomStore.update(oldRoom) {
            Room(
                oldRoom,
                nextRoomId = upgradedRoom,
                membership = Membership.JOIN
            )
        }
        roomStore.update(upgradedRoom) {
            Room(
                upgradedRoom,
                membership = Membership.INVITE
            )
        }
        roomStateStore.save(tombstoneEvent())

        backgroundScope.launch { cut.joinUpgradedRooms() }

        delay(5.minutes)
        joinCalled.value shouldBe 0
        delay(11.seconds)
        joinCalled.value shouldBe 1
    }

    @Test
    fun `tombstone from room list - old room is not JOIN - do nothing`() = runTest {
        (Membership.entries - Membership.JOIN).forEach { membership ->
            withClue("membership: $membership") {
                roomStore.update(oldRoom) {
                    Room(
                        oldRoom,
                        nextRoomId = upgradedRoom,
                        membership = membership
                    )
                }
                roomStore.update(upgradedRoom) {
                    Room(
                        upgradedRoom,
                        membership = Membership.INVITE
                    )
                }
                roomStateStore.save(tombstoneEvent())

                backgroundScope.launch { cut.joinUpgradedRooms() }

                delay(5.minutes)
                joinCalled.value shouldBe 0
                delay(11.seconds)
                joinCalled.value shouldBe 0
            }
        }
    }


    @Test
    fun `tombstone from room list - got MatrixServerException - upgrade`() = runTest {
        roomStore.update(oldRoom) {
            Room(
                oldRoom,
                nextRoomId = upgradedRoom,
                membership = Membership.JOIN
            )
        }
        roomStore.update(upgradedRoom) {
            Room(
                upgradedRoom,
                membership = Membership.INVITE
            )
        }
        roomStateStore.save(tombstoneEvent())

        backgroundScope.launch { cut.joinUpgradedRooms() }

        delay(5.minutes)
        joinCalled.value shouldBe 0
        delay(11.seconds)
        joinCalled.value shouldBe 1
    }
}

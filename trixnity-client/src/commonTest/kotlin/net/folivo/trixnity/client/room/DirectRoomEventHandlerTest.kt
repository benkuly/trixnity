package net.folivo.trixnity.client.room

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.getInMemoryGlobalAccountDataStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.clientserverapi.model.user.SetGlobalAccountData
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test

class DirectRoomEventHandlerTest : TrixnityBaseTest() {

    private val bob = UserId("bob", "server")
    private val alice = UserId("alice", "server")
    private val room = simpleRoom.roomId

    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val cut = DirectRoomEventHandler(
        UserInfo(bob, "", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        api,
        globalAccountDataStore,
    )

    private val otherRoom = RoomId("!other:server")
    private val event = StateEvent(
        MemberEventContent(membership = Membership.JOIN, isDirect = true),
        EventId("$123"),
        sender = bob,
        room,
        1234,
        stateKey = alice.full
    )

    @Test
    fun `setNewDirectEventFromMemberEvent » membership is direct » there are direct rooms with that user » add direct room`() =
        runTest {
            globalAccountDataStore.save(
                GlobalAccountDataEvent(DirectEventContent(mapOf(alice to setOf(otherRoom))))
            )
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(mapOf(alice to setOf(otherRoom, room)))
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(listOf(event))
            setDirectCalled shouldBe true
        }

    @Test
    fun `setNewDirectEventFromMemberEvent » membership is direct » there are no direct rooms with that user » add direct room`() =
        runTest {
            noDirectRoomsWithThatUserSetup()
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(
                        mapOf(
                            UserId("nobody", "server") to setOf(otherRoom),
                            alice to setOf(room)
                        )
                    )
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(listOf(event))
            setDirectCalled shouldBe true
        }

    @Test
    fun `setNewDirectEventFromMemberEvent » membership is direct » there are no direct rooms with that user » add multiple direct rooms`() =
        runTest {
            noDirectRoomsWithThatUserSetup()
            val yetAnotherRoom = RoomId("!yar:server")
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(
                        mapOf(
                            UserId("nobody", "server") to setOf(otherRoom),
                            alice to setOf(room),
                            UserId("other", "server") to setOf(yetAnotherRoom)
                        )
                    )
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(
                listOf(
                    event,
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN, isDirect = true),
                        EventId("$123"),
                        sender = bob,
                        yetAnotherRoom,
                        1234,
                        stateKey = UserId("other", "server").full
                    )
                )
            )
            setDirectCalled shouldBe true
        }

    @Test
    fun `setNewDirectEventFromMemberEvent » membership is direct » there are no direct rooms with that user » ignore own direct room join`() =
        runTest {
            noDirectRoomsWithThatUserSetup()
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(mapOf(UserId("nobody", "server") to setOf(otherRoom)))
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN, isDirect = true),
                        EventId("$123"),
                        sender = bob,
                        room,
                        1234,
                        stateKey = bob.full
                    )
                )
            )
            setDirectCalled shouldBe false
        }

    @Test
    fun `setNewDirectEventFromMemberEvent » membership is direct » there are no direct rooms at all » add direct room`() =
        runTest {
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(listOf(event))
            setDirectCalled shouldBe true
        }

    @Test
    fun `setNewDirectEventFromMemberEvent » membership is direct » we are the invitee of a direct room » add the room as a direct room`() =
        runTest {
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN, isDirect = true),
                        EventId("$123"),
                        sender = alice,
                        room,
                        1234,
                        stateKey = bob.full
                    )
                )
            )
            setDirectCalled shouldBe true
        }

    @Test
    fun `setNewDirectEventFromMemberEvent » membership is direct » we invite to a direct room » add the room as a direct room`() =
        runTest {
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN, isDirect = true),
                        EventId("$123"),
                        sender = bob,
                        room,
                        1234,
                        stateKey = alice.full
                    )
                )
            )
            setDirectCalled shouldBe true
        }

    @Test
    fun `setNewDirectEventFromMemberEvent » membership is direct » invitation is from our own to our own » ignore this invitation`() =
        runTest {
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(mapOf())
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN, isDirect = true),
                        EventId("$123"),
                        sender = bob,
                        room,
                        1234,
                        stateKey = bob.full
                    )
                )
            )
            setDirectCalled shouldBe false
        }

    @Test
    fun `setNewDirectEventFromMemberEvent » own membership is leave or ban » remove direct room on leave`() =
        runTest {
            ownMembershipIsLeaveOrBanSetup()
            val event = StateEvent(
                MemberEventContent(membership = Membership.LEAVE),
                EventId("$123"),
                alice,
                room,
                1234,
                stateKey = bob.full
            )
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(
                        mapOf(
                            UserId("2", "server") to setOf(otherRoom)
                        )
                    )
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(listOf(event))
            setDirectCalled shouldBe true
        }

    @Test
    fun `setNewDirectEventFromMemberEvent » own membership is leave or ban » remove direct room on ban`() =
        runTest {
            ownMembershipIsLeaveOrBanSetup()
            val event = StateEvent(
                MemberEventContent(membership = Membership.BAN),
                EventId("$123"),
                bob,
                room,
                1234,
                stateKey = bob.full
            )
            var setDirectCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                    it shouldBe DirectEventContent(
                        mapOf(
                            UserId("2", "server") to setOf(otherRoom)
                        )
                    )
                    setDirectCalled = true
                }
            }
            cut.setNewDirectEventFromMemberEvent(listOf(event))
            setDirectCalled shouldBe true
        }


    private suspend fun noDirectRoomsWithThatUserSetup() {
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                DirectEventContent(
                    mapOf(UserId("nobody", "server") to setOf(otherRoom))
                )
            )
        )
    }

    private suspend fun ownMembershipIsLeaveOrBanSetup() {
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                DirectEventContent(
                    mapOf(
                        UserId("1", "server") to setOf(room),
                        UserId("2", "server") to setOf(room, otherRoom)
                    )
                )
            )
        )
    }
}
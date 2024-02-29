package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.model.users.SetGlobalAccountData
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class DirectRoomEventHandlerTest : ShouldSpec({
    timeout = 30_000

    val bob = UserId("bob", "server")
    val alice = UserId("alice", "server")
    val room = simpleRoom.roomId
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    lateinit var scope: CoroutineScope
    lateinit var apiConfig: PortableMockEngineConfig
    val json = createMatrixEventJson()

    lateinit var cut: DirectRoomEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = DirectRoomEventHandler(
            UserInfo(bob, "", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            api,
            roomStore, roomStateStore, globalAccountDataStore,
        )
    }

    afterTest {
        scope.cancel()
    }

    context(DirectRoomEventHandler::setNewDirectEventFromMemberEvent.name) {
        val otherRoom = RoomId("other", "server")
        context("membership is direct") {
            val event = StateEvent(
                MemberEventContent(membership = Membership.JOIN, isDirect = true),
                EventId("$123"),
                sender = bob,
                room,
                1234,
                stateKey = alice.full
            )
            context("there are direct rooms with that user") {
                beforeTest {
                    globalAccountDataStore.save(
                        GlobalAccountDataEvent(DirectEventContent(mapOf(alice to setOf(otherRoom))))
                    )
                }
                should("add direct room") {
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
            }
            context("there are no direct rooms with that user") {
                beforeTest {
                    globalAccountDataStore.save(
                        GlobalAccountDataEvent(
                            DirectEventContent(
                                mapOf(UserId("nobody", "server") to setOf(otherRoom))
                            )
                        )
                    )
                }
                should("add direct room") {
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
                should("add multiple direct rooms") {
                    val yetAnotherRoom = RoomId("yar", "server")
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
                should("ignore own direct room join") {
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
            }
            context("there are no direct rooms at all") {
                should("add direct room") {
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
            }
            context("we are the invitee of a direct room") {
                val joinEvent = StateEvent(
                    MemberEventContent(membership = Membership.JOIN, isDirect = true),
                    EventId("$123"),
                    sender = alice,
                    room,
                    1234,
                    stateKey = bob.full
                )
                should("add the room as a direct room") {
                    var setDirectCalled = false
                    apiConfig.endpoints {
                        matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                            it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            setDirectCalled = true
                        }
                    }
                    cut.setNewDirectEventFromMemberEvent(listOf(joinEvent))
                    setDirectCalled shouldBe true
                }
            }
            context("we invite to a direct room") {
                val joinEvent = StateEvent(
                    MemberEventContent(membership = Membership.JOIN, isDirect = true),
                    EventId("$123"),
                    sender = bob,
                    room,
                    1234,
                    stateKey = alice.full
                )
                should("add the room as a direct room") {
                    var setDirectCalled = false
                    apiConfig.endpoints {
                        matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                            it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            setDirectCalled = true
                        }
                    }
                    cut.setNewDirectEventFromMemberEvent(listOf(joinEvent))
                    setDirectCalled shouldBe true
                }
            }
            context("invitation is from our own to our own") {
                val joinEvent = StateEvent(
                    MemberEventContent(membership = Membership.JOIN, isDirect = true),
                    EventId("$123"),
                    sender = bob,
                    room,
                    1234,
                    stateKey = bob.full
                )
                should("ignore this invitation") {
                    var setDirectCalled = false
                    apiConfig.endpoints {
                        matrixJsonEndpoint(SetGlobalAccountData(bob, "m.direct")) {
                            it shouldBe DirectEventContent(mapOf())
                            setDirectCalled = true
                        }
                    }
                    cut.setNewDirectEventFromMemberEvent(listOf(joinEvent))
                    setDirectCalled shouldBe false
                }
            }
        }
        context("own membership is leave or ban") {
            beforeTest {
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
            should("remove direct room on leave") {
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
            should("remove direct room on ban") {
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
        }
    }
    context(DirectRoomEventHandler::setDirectRoomProperties.name) {
        should("set the room to direct == 'true' when a DirectEventContent is found for the room") {
            roomStore.update(room) { Room(room, isDirect = false) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(RoomId("room2", "localhost"), room)
                )
            )
            roomStore.getAll().first { it.size == 1 }

            cut.setDirectRoomProperties(GlobalAccountDataEvent(eventContent))

            roomStore.get(room).first()?.isDirect shouldBe true
        }
        should("set the room to direct == 'false' when no DirectEventContent is found for the room") {
            val room1 = RoomId("room1", "localhost")
            val room2 = RoomId("room2", "localhost")
            roomStore.update(room1) { Room(room1, isDirect = true) }
            roomStore.update(room2) { Room(room2, isDirect = true) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(room2)
                )
            )
            roomStore.getAll().first { it.size == 2 }

            cut.setDirectRoomProperties(GlobalAccountDataEvent(eventContent))

            roomStore.get(room1).first()?.isDirect shouldBe false
            roomStore.get(room2).first()?.isDirect shouldBe true
        }
        should("set the avatar URL to a member's avatar URL") {
            roomStore.update(room) { Room(room, avatarUrl = null) }
            roomStateStore.save(
                StateEvent(
                    MemberEventContent("mxc://localhost/abcdef", membership = Membership.JOIN),
                    EventId("1"),
                    bob,
                    room,
                    0L,
                    stateKey = alice.full,
                )
            )
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    alice to setOf(
                        room,
                        RoomId("room2", "localhost")
                    )
                )
            )

            cut.setDirectRoomProperties(GlobalAccountDataEvent(eventContent))

            roomStore.get(room).first()?.avatarUrl shouldBe "mxc://localhost/abcdef"
        }
        should("when the avatar URL is explicitly set use it instead of member's avatar URL") {
            roomStore.update(room) { Room(room, avatarUrl = "mxc://localhost/123456") }
            roomStateStore.save(
                StateEvent(
                    MemberEventContent("mxc://localhost/abcdef", membership = Membership.JOIN),
                    EventId("1"),
                    bob,
                    room,
                    0L,
                    stateKey = alice.full,
                )
            )
            roomStateStore.save(
                StateEvent(
                    AvatarEventContent("mxc://localhost/123456"),
                    EventId("1"),
                    bob,
                    room,
                    0L,
                    stateKey = "",
                )
            )
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    alice to setOf(
                        room,
                        RoomId("room2", "localhost")
                    )
                )
            )

            cut.setDirectRoomProperties(GlobalAccountDataEvent(eventContent))

            roomStore.get(room).first()?.avatarUrl shouldBe "mxc://localhost/123456"
        }
    }
})
package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.users.SetGlobalAccountData
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class RoomServiceDirectTest : ShouldSpec({
    timeout = 30_000

    val bob = UserId("bob", "server")
    val alice = UserId("alice", "server")
    val room = simpleRoom.roomId
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    lateinit var apiConfig: PortableMockEngineConfig
    val users = mockk<UserService>(relaxUnitFun = true)
    val olm = mockk<OlmService>()
    val key = mockk<KeyService>()
    val media = mockk<MediaService>()
    val json = createMatrixJson()
    val mappings = createEventContentSerializerMappings()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)


    lateinit var cut: RoomService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = RoomService(bob, store, api, olm, key, users, media, currentSyncState)
    }

    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(RoomService::setDirectRooms.name) {
        val otherRoom = RoomId("other", "server")
        context("membership is direct") {
            val event = Event.StateEvent(
                MemberEventContent(membership = Membership.JOIN, isDirect = true),
                EventId("$123"),
                sender = bob,
                room,
                1234,
                stateKey = alice.full
            )
            context("there are direct rooms with that user") {
                beforeTest {
                    store.globalAccountData.update(
                        Event.GlobalAccountDataEvent(DirectEventContent(mapOf(alice to setOf(otherRoom))))
                    )
                }
                should("add direct room") {
                    var setDirectCalled = false
                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                            it shouldBe DirectEventContent(mapOf(alice to setOf(otherRoom, room)))
                            setDirectCalled = true
                        }
                    }
                    cut.setDirectRooms(event)
                    cut.setDirectRoomsAfterSync()
                    setDirectCalled shouldBe true
                }
            }
            context("there are no direct rooms with that user") {
                beforeTest {
                    store.globalAccountData.update(
                        Event.GlobalAccountDataEvent(
                            DirectEventContent(
                                mapOf(UserId("nobody", "server") to setOf(otherRoom))
                            )
                        )
                    )
                }
                should("add direct room") {
                    var setDirectCalled = false
                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                            it shouldBe DirectEventContent(
                                mapOf(
                                    UserId("nobody", "server") to setOf(otherRoom),
                                    alice to setOf(room)
                                )
                            )
                            setDirectCalled = true
                        }
                    }
                    cut.setDirectRooms(event)
                    cut.setDirectRoomsAfterSync()
                    setDirectCalled shouldBe true
                }
                should("add multiple direct rooms") {
                    val yetAnotherRoom = RoomId("yar", "server")
                    var setDirectCalled = false
                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
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
                    cut.setDirectRooms(event)
                    cut.setDirectRooms(
                        Event.StateEvent(
                            MemberEventContent(membership = Membership.JOIN, isDirect = true),
                            EventId("$123"),
                            sender = bob,
                            yetAnotherRoom,
                            1234,
                            stateKey = UserId("other", "server").full
                        )
                    )
                    cut.setDirectRoomsAfterSync()
                    // ensure, that cache is cleared
                    cut.setDirectRoomsAfterSync()
                    setDirectCalled shouldBe true
                }
            }
            context("there are no direct rooms at all") {
                should("add direct room") {
                    var setDirectCalled = false
                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                            it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            setDirectCalled = true
                        }
                    }
                    cut.setDirectRooms(event)
                    cut.setDirectRoomsAfterSync()
                    setDirectCalled shouldBe true
                }
            }
            context("we are the invitee of a direct room") {
                val joinEvent = Event.StateEvent(
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
                        matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                            it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            setDirectCalled = true
                        }
                    }
                    cut.setDirectRooms(joinEvent)
                    cut.setDirectRoomsAfterSync()
                    setDirectCalled shouldBe true
                }
            }
            context("we invite to a direct room") {
                val joinEvent = Event.StateEvent(
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
                        matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                            it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            setDirectCalled = true
                        }
                    }
                    cut.setDirectRooms(joinEvent)
                    cut.setDirectRoomsAfterSync()
                    setDirectCalled shouldBe true
                }
            }
            context("invitation is from our own to our own") {
                val joinEvent = Event.StateEvent(
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
                        matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                            it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            setDirectCalled = true
                        }
                    }
                    cut.setDirectRooms(joinEvent)
                    setDirectCalled shouldBe false
                }
            }
            context("invitation does not affect us") {
                val joinEvent = Event.StateEvent(
                    MemberEventContent(membership = Membership.JOIN, isDirect = true),
                    EventId("$123"),
                    sender = alice,
                    room,
                    1234,
                    stateKey = UserId("someoneElse", "localhost").full
                )
                should("ignore this invitation") {
                    var setDirectCalled = false
                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                            it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            setDirectCalled = true
                        }
                    }
                    cut.setDirectRooms(joinEvent)
                    setDirectCalled shouldBe false
                }
            }
        }
        context("own membership is leave or ban") {
            beforeTest {
                store.globalAccountData.update(
                    Event.GlobalAccountDataEvent(
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
                val event = Event.StateEvent(
                    MemberEventContent(membership = Membership.LEAVE),
                    EventId("$123"),
                    bob,
                    room,
                    1234,
                    stateKey = bob.full
                )
                var setDirectCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                        it shouldBe DirectEventContent(
                            mapOf(
                                UserId("2", "server") to setOf(otherRoom)
                            )
                        )
                        setDirectCalled = true
                    }
                }
                cut.setDirectRooms(event)
                cut.setDirectRoomsAfterSync()
                setDirectCalled shouldBe true
            }
            should("remove direct room on ban") {
                val event = Event.StateEvent(
                    MemberEventContent(membership = Membership.BAN),
                    EventId("$123"),
                    bob,
                    room,
                    1234,
                    stateKey = bob.full
                )
                var setDirectCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                        it shouldBe DirectEventContent(
                            mapOf(
                                UserId("2", "server") to setOf(otherRoom)
                            )
                        )
                        setDirectCalled = true
                    }
                }
                cut.setDirectRooms(event)
                cut.setDirectRoomsAfterSync()
                setDirectCalled shouldBe true
            }
        }
        context("others membership is leave or ban") {
            beforeTest {
                store.globalAccountData.update(
                    Event.GlobalAccountDataEvent(
                        DirectEventContent(
                            mapOf(
                                alice to setOf(room),
                                UserId("2", "server") to setOf(room, otherRoom)
                            )
                        )
                    )
                )
            }
            should("remove direct room on leave") {
                val event = Event.StateEvent(
                    MemberEventContent(membership = Membership.LEAVE),
                    EventId("$123"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
                var setDirectCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                        it shouldBe DirectEventContent(mapOf(UserId("2", "server") to setOf(room, otherRoom)))
                        setDirectCalled = true
                    }
                }
                cut.setDirectRooms(event)
                cut.setDirectRoomsAfterSync()
                setDirectCalled shouldBe true
            }
            should("remove direct room on ban") {
                val event = Event.StateEvent(
                    MemberEventContent(membership = Membership.BAN),
                    EventId("$123"),
                    bob,
                    room,
                    1234,
                    stateKey = alice.full
                )
                var setDirectCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, SetGlobalAccountData(bob.e(), "m.direct")) {
                        it shouldBe DirectEventContent(mapOf(UserId("2", "server") to setOf(room, otherRoom)))
                        setDirectCalled = true
                    }
                }
                cut.setDirectRooms(event)
                cut.setDirectRoomsAfterSync()
                setDirectCalled shouldBe true
            }
        }
    }

    context(RoomService::setRoomIsDirect.name) {
        should("set the room to direct == 'true' when a DirectEventContent is found for the room") {
            store.room.update(room) { Room(room, isDirect = false) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(RoomId("room2", "localhost"), room)
                )
            )
            cut.getAll().first { it.size == 1 }

            cut.setRoomIsDirect(eventContent)

            store.room.get(room).value?.isDirect shouldBe true
        }
        should("set the room to direct == 'false' when no DirectEventContent is found for the room") {
            val room1 = RoomId("room1", "localhost")
            val room2 = RoomId("room2", "localhost")
            store.room.update(room1) { Room(room1, isDirect = true) }
            store.room.update(room2) { Room(room2, isDirect = true) }
            val eventContent = DirectEventContent(
                mappings = mapOf(
                    UserId("user1", "localhost") to setOf(room2)
                )
            )
            cut.getAll().first { it.size == 2 }

            cut.setRoomIsDirect(eventContent)

            store.room.get(room1).value?.isDirect shouldBe false
            store.room.get(room2).value?.isDirect shouldBe true
        }
    }

    context(RoomService::handleDirectEventContent.name) {
        should("call DirectEventContent handlers") {
            val spyCut = spyk(cut) {
                coEvery { setRoomIsDirect(any()) } just Runs
                coEvery { setAvatarUrlForDirectRooms(any()) } just Runs
            }
            val eventContent = mockk<DirectEventContent>()
            spyCut.setDirectEventContent(Event.GlobalAccountDataEvent(eventContent))
            spyCut.handleDirectEventContent()
            coVerify {
                spyCut.setRoomIsDirect(eventContent)
                spyCut.setAvatarUrlForDirectRooms(eventContent)
            }
        }
    }

})
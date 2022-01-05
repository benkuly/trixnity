package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

class RoomServiceDirectTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val room = simpleRoom.roomId
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val api = mockk<MatrixApiClient>(relaxed = true)
    val users = mockk<UserService>(relaxUnitFun = true)
    val olm = mockk<OlmService>()
    val media = mockk<MediaService>()

    lateinit var cut: RoomService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        cut = RoomService(store, api, olm, users, media)
    }

    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(RoomService::setDirectRooms.name) {
        val otherRoom = RoomId("other", "server")
        val bob = UserId("bob", "server")
        beforeTest {
            store.account.userId.value = bob
        }
        context("membership is direct") {
            val event = Event.StateEvent(
                MemberEventContent(membership = MemberEventContent.Membership.JOIN, isDirect = true),
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
                    cut.setDirectRooms(event)
                    coVerify {
                        api.users.setAccountData(
                            withArg {
                                it shouldBe DirectEventContent(mapOf(alice to setOf(otherRoom, room)))
                            },
                            bob
                        )
                    }
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
                    cut.setDirectRooms(event)
                    coVerify {
                        api.users.setAccountData(
                            withArg {
                                it shouldBe DirectEventContent(
                                    mapOf(
                                        UserId("nobody", "server") to setOf(otherRoom),
                                        alice to setOf(room)
                                    )
                                )
                            },
                            bob
                        )
                    }
                }
            }
            context("there are no direct rooms at all") {
                should("add direct room") {
                    cut.setDirectRooms(event)
                    coVerify {
                        api.users.setAccountData(
                            withArg {
                                it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            },
                            bob
                        )
                    }
                }
            }
            context("we are the invitee of a direct room") {
                val joinEvent = Event.StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.JOIN, isDirect = true),
                    EventId("$123"),
                    sender = alice,
                    room,
                    1234,
                    stateKey = bob.full
                )
                should("add the room as a direct room") {
                    cut.setDirectRooms(joinEvent)
                    coVerify {
                        api.users.setAccountData(
                            withArg {
                                it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            },
                            bob
                        )
                    }
                }
            }
            context("we invite to a direct room") {
                val joinEvent = Event.StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.JOIN, isDirect = true),
                    EventId("$123"),
                    sender = bob,
                    room,
                    1234,
                    stateKey = alice.full
                )
                should("add the room as a direct room") {
                    cut.setDirectRooms(joinEvent)
                    coVerify {
                        api.users.setAccountData(
                            withArg {
                                it shouldBe DirectEventContent(mapOf(alice to setOf(room)))
                            },
                            bob
                        )
                    }
                }
            }
            context("invitation does not affect us") {
                val joinEvent = Event.StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.JOIN, isDirect = true),
                    EventId("$123"),
                    sender = alice,
                    room,
                    1234,
                    stateKey = UserId("someoneElse", "localhost").full
                )
                should("ignore this invitation") {
                    cut.setDirectRooms(joinEvent)
                    coVerify(exactly = 0) {
                        api.users.setAccountData(
                            any(),
                            any(),
                        )
                    }
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
                    MemberEventContent(membership = MemberEventContent.Membership.LEAVE),
                    EventId("$123"),
                    bob,
                    room,
                    1234,
                    stateKey = bob.full
                )
                cut.setDirectRooms(event)
                coVerify {
                    api.users.setAccountData(
                        withArg {
                            it shouldBe DirectEventContent(
                                mapOf(
                                    UserId("2", "server") to setOf(otherRoom)
                                )
                            )
                        },
                        bob
                    )
                }
            }
            should("remove direct room on ban") {
                val event = Event.StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.BAN),
                    EventId("$123"),
                    bob,
                    room,
                    1234,
                    stateKey = bob.full
                )
                cut.setDirectRooms(event)
                coVerify {
                    api.users.setAccountData(
                        withArg {
                            it shouldBe DirectEventContent(
                                mapOf(
                                    UserId("2", "server") to setOf(otherRoom)
                                )
                            )
                        },
                        bob
                    )
                }
            }
        }
    }

    context(RoomService::setRoomIsDirect.name) {
        should("set the room to direct == 'true' when a DirectEventContent is found for the room") {
            store.room.update(room) { Room(room, isDirect = false) }
            val event = Event.GlobalAccountDataEvent(
                DirectEventContent(
                    mappings = mapOf(
                        UserId("user1", "localhost") to setOf(RoomId("room2", "localhost"), room)
                    )
                )
            )
            cut.getAll().first { it.size == 1 }

            cut.setRoomIsDirect(event)

            store.room.get(room).value?.isDirect shouldBe true
        }
        should("set the room to direct == 'false' when no DirectEventContent is found for the room") {
            val room1 = RoomId("room1", "localhost")
            val room2 = RoomId("room2", "localhost")
            store.room.update(room1) { Room(room1, isDirect = true) }
            store.room.update(room2) { Room(room2, isDirect = true) }
            val event = Event.GlobalAccountDataEvent(
                DirectEventContent(
                    mappings = mapOf(
                        UserId("user1", "localhost") to setOf(room2)
                    )
                )
            )
            cut.getAll().first { it.size == 2 }

            cut.setRoomIsDirect(event)

            store.room.get(room1).value?.isDirect shouldBe false
            store.room.get(room2).value?.isDirect shouldBe true
        }
    }

})
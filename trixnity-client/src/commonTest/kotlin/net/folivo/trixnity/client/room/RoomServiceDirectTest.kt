package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.user.getAccountData
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import org.kodein.log.LoggerFactory

class RoomServiceDirectTest : ShouldSpec({

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
        cut = RoomService(store, api, olm, users, media, loggerFactory = LoggerFactory.default)
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

    context(RoomService::isDirectRoom.name) {
        should("return 'true' when room is found in user's direct room data") {
            coEvery { users.getAccountData<DirectEventContent>(any()) } returns MutableStateFlow(
                DirectEventContent(
                    mappings = mapOf(
                        UserId("user1", "localhost") to setOf(RoomId("room2", "localhost"), room)
                    )
                )
            )
            val scope = CoroutineScope(Dispatchers.Default)
            cut.isDirectRoom(room, scope).value shouldBe true
        }
        should("return 'false' when room is not found in user's room data") {
            coEvery { users.getAccountData<DirectEventContent>(any()) } returns MutableStateFlow(
                DirectEventContent(
                    mappings = mapOf(
                        UserId("user1", "localhost") to setOf(RoomId("room2", "localhost"))
                    )
                )
            )
            val scope = CoroutineScope(Dispatchers.Default)
            cut.isDirectRoom(room, scope).value shouldBe false
        }
    }

})
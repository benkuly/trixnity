package net.folivo.trixnity.client.user

import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.SyncApiClient
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent.Presence
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.LEAVE
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import kotlin.time.Duration.Companion.milliseconds

class UserServiceTest : ShouldSpec({
    timeout = 30_000
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val roomId = simpleRoom.roomId
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val api = mockk<MatrixApiClient>()
    lateinit var cut: UserService

    beforeTest {
        every { api.eventContentSerializerMappings } returns DefaultEventContentSerializerMappings
        coEvery { api.sync.currentSyncState } returns MutableStateFlow(SyncApiClient.SyncState.RUNNING)
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        cut = UserService(store, api)
    }

    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(UserService::handleLoadMembersQueue.name) {
        should("do nothing when members already loaded") {
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = true)
            store.room.update(roomId) { storedRoom }
            cut.loadMembers(roomId)
            val job = launch { cut.handleLoadMembersQueue() }
            continually(500.milliseconds) {
                store.room.get(roomId).value shouldBe storedRoom
            }
            job.cancel()
        }
        should("load members") {
            coEvery { api.rooms.getMembers(any(), any(), any(), any(), any()) } returns Result.success(
                flowOf(
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        EventId("\$event1"),
                        alice,
                        roomId,
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        EventId("\$event2"),
                        bob,
                        roomId,
                        1234,
                        stateKey = bob.full
                    )
                )
            )
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = false)
            store.room.update(roomId) { storedRoom }
            cut.loadMembers(roomId)
            val job = launch { cut.handleLoadMembersQueue() }
            store.room.get(roomId).first { it?.membersLoaded == true }?.membersLoaded shouldBe true
            store.keys.outdatedKeys.value.shouldBeEmpty()
            store.roomState.getByStateKey<MemberEventContent>(roomId, alice.full)?.content?.membership shouldBe JOIN
            store.roomState.getByStateKey<MemberEventContent>(roomId, bob.full)?.content?.membership shouldBe JOIN
            job.cancel()
        }
        should("add outdated keys when room is encrypted") {
            coEvery { api.rooms.getMembers(any(), any(), any(), any(), any()) } returns Result.success(
                flowOf(
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        EventId("\$event1"),
                        alice,
                        roomId,
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        EventId("\$event2"),
                        bob,
                        roomId,
                        1234,
                        stateKey = bob.full
                    )
                )
            )
            store.keys.updateDeviceKeys(alice) { mapOf("alice" to mockk()) } // we know alice keys, so only update bob keys
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = false, encryptionAlgorithm = Megolm)
            store.room.update(roomId) { storedRoom }
            cut.loadMembers(roomId)
            val job = launch { cut.handleLoadMembersQueue() }
            store.room.get(roomId).first { it?.membersLoaded == true }?.membersLoaded shouldBe true
            store.keys.outdatedKeys.value shouldBe setOf(bob)
            store.roomState.getByStateKey<MemberEventContent>(roomId, alice.full)?.content?.membership shouldBe JOIN
            store.roomState.getByStateKey<MemberEventContent>(roomId, bob.full)?.content?.membership shouldBe JOIN
            job.cancel()
        }
        should("skip members, that are already stored") {
            store.roomState.update(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event1"),
                    alice,
                    roomId,
                    1234,
                    stateKey = alice.full
                )
            )
            coEvery { api.rooms.getMembers(any(), any(), any(), any(), any()) } returns Result.success(
                flowOf(
                    StateEvent(
                        MemberEventContent(membership = LEAVE),
                        EventId("\$event1"),
                        alice,
                        roomId,
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        EventId("\$event2"),
                        bob,
                        roomId,
                        1234,
                        stateKey = bob.full
                    )
                )
            )
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = false)
            store.room.update(roomId) { storedRoom }
            cut.loadMembers(roomId)
            val job = launch { cut.handleLoadMembersQueue() }
            store.room.get(roomId).first { it?.membersLoaded == true }?.membersLoaded shouldBe true
            store.keys.outdatedKeys.value.shouldBeEmpty()
            store.roomState.getByStateKey<MemberEventContent>(roomId, alice.full)?.content?.membership shouldBe JOIN
            store.roomState.getByStateKey<MemberEventContent>(roomId, bob.full)?.content?.membership shouldBe JOIN
            job.cancel()
        }
    }
    context(UserService::setRoomUser.name) {
        val user1 = UserId("user1", "server")
        val user2 = UserId("user2", "server")
        val user3 = UserId("user3", "server")
        val user4 = UserId("user4", "server")
        val user1Event = StateEvent(
            mockk<MemberEventContent>(),
            EventId("\$event1"),
            UserId("sender", "server"),
            roomId,
            1234,
            stateKey = user1.full
        )
        val user2Event = StateEvent(
            mockk<MemberEventContent>(),
            EventId("\$event2"),
            UserId("sender", "server"),
            roomId,
            1234,
            stateKey = user2.full
        )
        val user3Event = StateEvent(
            mockk<MemberEventContent>(),
            EventId("\$event3"),
            UserId("sender", "server"),
            roomId,
            1234,
            stateKey = user3.full
        )
        beforeTest {
            // This should be ignored
            store.roomUser.update(user4, roomId) {
                RoomUser(
                    roomId,
                    user4,
                    "U1 (@user4:server)",
                    StateEvent(
                        MemberEventContent(displayName = "U1", membership = MemberEventContent.Membership.BAN),
                        EventId("\$event4"),
                        UserId("sender", "server"),
                        roomId,
                        1234,
                        stateKey = user4.full
                    )
                )
            }
        }
        should("skip when user already present") {
            val event = user1Event.copy(
                content = MemberEventContent(
                    displayName = "U1",
                    membership = JOIN
                )
            )
            cut.setRoomUser(event)
            cut.setRoomUser(
                event.copy(content = event.content.copy(displayName = "CHANGED!!!")),
                skipWhenAlreadyPresent = true
            )
            store.roomUser.get(user1, roomId) shouldBe RoomUser(roomId, user1, "U1", event)
        }
        context("user is new member") {
            should("set our displayName to 'DisplayName'") {
                val event = user1Event.copy(
                    content = MemberEventContent(
                        displayName = "U1",
                        membership = JOIN
                    )
                )
                cut.setRoomUser(event)
                store.roomUser.get(user1, roomId) shouldBe RoomUser(roomId, user1, "U1", event)
            }
        }
        context("user is member") {
            beforeTest {
                store.roomUser.update(user1, roomId) {
                    RoomUser(
                        roomId,
                        user1,
                        "OLD",
                        user1Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    )
                }
            }
            context("no other user has same displayName") {
                beforeTest {
                    store.roomUser.update(user2, roomId) {
                        RoomUser(
                            roomId,
                            user2,
                            "U2",
                            user2Event.copy(content = MemberEventContent(displayName = "U2", membership = JOIN))
                        )
                    }
                }
                should("set our displayName to 'DisplayName'") {
                    val event = user1Event.copy(
                        content = MemberEventContent(
                            displayName = "U1",
                            membership = JOIN
                        )
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(roomId, user1, "U1", event)
                }
                should("not change our displayName when it has not changed") {
                    val event = user1Event.copy(
                        content = MemberEventContent(
                            displayName = "OLD",
                            membership = JOIN
                        )
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(roomId, user1, "OLD", event)
                }
                should("set our displayName to '@user:server' when no displayName set") {
                    val event = user1Event.copy(content = MemberEventContent(membership = JOIN))
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId,
                        user1,
                        "@user1:server",
                        event
                    )
                }
                should("set our displayName to '@user:server' when displayName is empty") {
                    val event = user1Event.copy(content = MemberEventContent(displayName = "", membership = JOIN))
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId,
                        user1,
                        "@user1:server",
                        event
                    )
                }
            }
            context("one other user has same displayName") {
                should("set displayName of the other and us to 'DisplayName (@user1:server)'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = JOIN)
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                }
            }
            context("two other users have same displayName") {
                should("set our displayName to 'DisplayName (@user:server)'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user2, roomId) {
                        RoomUser(roomId, user2, "U1 (@user2:server)", event2)
                    }
                    val event3 =
                        user3Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user3, roomId) {
                        RoomUser(roomId, user3, "U1 (@user3:server)", event3)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = JOIN)
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                    store.roomUser.get(user3, roomId) shouldBe RoomUser(
                        roomId, user3, "U1 (@user3:server)", event3
                    )
                }
            }
            context("one other user has same old displayName") {
                should("set displayName of the other to 'DisplayName'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    store.roomUser.update(user2, roomId) {
                        RoomUser(roomId, user2, "OLD (@user2:server)", event2)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = JOIN)
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "OLD", event2
                    )
                }
            }
            context("two other users have same old displayName") {
                should("keep displayName of the others'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    store.roomUser.update(user2, roomId) {
                        RoomUser(roomId, user2, "OLD (@user2:server)", event2)
                    }
                    val event3 =
                        user3Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    store.roomUser.update(user3, roomId) {
                        RoomUser(roomId, user3, "OLD (@user3:server)", event3)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = JOIN)
                    )
                    cut.setRoomUser(event)

                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "OLD (@user2:server)", event2
                    )
                    store.roomUser.get(user3, roomId) shouldBe RoomUser(
                        roomId, user3, "OLD (@user3:server)", event3
                    )
                }
            }
        }
        context("user is not member anymore") {
            beforeTest {
                store.roomUser.update(user1, roomId) {
                    RoomUser(
                        roomId,
                        user1,
                        "OLD",
                        user1Event.copy(content = MemberEventContent(displayName = "OLD", membership = JOIN))
                    )
                }
            }
            should("set our displayName to 'DisplayName (@user:server)'") {
                val event = user1Event.copy(
                    content = MemberEventContent(displayName = "OLD", membership = LEAVE)
                )
                cut.setRoomUser(event)
                store.roomUser.get(user1, roomId) shouldBe RoomUser(
                    roomId, user1, "OLD (@user1:server)", event
                )
            }
            context("one other user has same displayName") {
                should("set displayName of the other to 'DisplayName'") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user2, roomId) { RoomUser(roomId, user2, "U1", event2) }
                    val event = user1Event.copy(
                        content = MemberEventContent(displayName = "U1", membership = MemberEventContent.Membership.BAN)
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "U1", event2
                    )
                }
            }
            context("two other users have same displayName") {
                should("keep displayName of the others") {
                    val event2 =
                        user2Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user2, roomId) {
                        RoomUser(roomId, user2, "U1 (@user2:server)", event2)
                    }
                    val event3 =
                        user3Event.copy(content = MemberEventContent(displayName = "U1", membership = JOIN))
                    store.roomUser.update(user3, roomId) {
                        RoomUser(roomId, user3, "U1 (@user3:server)", event3)
                    }
                    val event = user1Event.copy(
                        content = MemberEventContent(
                            displayName = "U1",
                            membership = LEAVE
                        )
                    )
                    cut.setRoomUser(event)
                    store.roomUser.get(user1, roomId) shouldBe RoomUser(
                        roomId, user1, "U1 (@user1:server)", event
                    )
                    store.roomUser.get(user2, roomId) shouldBe RoomUser(
                        roomId, user2, "U1 (@user2:server)", event2
                    )
                    store.roomUser.get(user3, roomId) shouldBe RoomUser(
                        roomId, user3, "U1 (@user3:server)", event3
                    )
                }
            }
        }
    }

    context(UserService::setPresence.name) {
        should("set the presence for a user whose presence is not known") {
            cut.userPresence.value[alice] shouldBe null
            cut.setPresence(Event.EphemeralEvent(PresenceEventContent(Presence.ONLINE), sender = alice))
            cut.userPresence.value[alice] shouldBe PresenceEventContent(Presence.ONLINE)
        }

        should("overwrite the presence of a user when a new status is known") {
            cut.setPresence(Event.EphemeralEvent(PresenceEventContent(Presence.ONLINE), sender = bob))
            cut.setPresence(Event.EphemeralEvent(PresenceEventContent(Presence.UNAVAILABLE), sender = bob))

            cut.userPresence.value[bob] shouldBe PresenceEventContent(Presence.UNAVAILABLE)
        }
    }
})
package net.folivo.trixnity.client.user

import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetMembers
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.milliseconds

class UserServiceTest : ShouldSpec({
    timeout = 30_000
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val me = UserId("me", "server")
    val roomId = simpleRoom.roomId
    lateinit var roomUserStore: RoomUserStore
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    lateinit var scope: CoroutineScope
    lateinit var api: MatrixClientServerApiClient
    lateinit var apiConfig: PortableMockEngineConfig
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: UserServiceImpl

    fun getCreateEvent(creator: UserId) = StateEvent(
        CreateEventContent(creator = creator),
        EventId("\$event"),
        creator,
        roomId,
        1234,
        stateKey = ""
    )

    fun getPowerLevelsEvent(powerLevelsEventContent: PowerLevelsEventContent) = StateEvent(
        powerLevelsEventContent,
        EventId("\$event"),
        me,
        roomId,
        1234,
        stateKey = ""
    )

    beforeTest {
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        api = newApi
        apiConfig = newApiConfig
        currentSyncState.value = SyncState.RUNNING
        scope = CoroutineScope(Dispatchers.Default)
        roomUserStore = getInMemoryRoomUserStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        cut = UserServiceImpl(
            roomUserStore,
            roomStore,
            roomStateStore,
            globalAccountDataStore,
            api,
            PresenceEventHandler(api),
            CurrentSyncState(currentSyncState),
            userInfo = UserInfo(
                me, "IAmADeviceId", signingPublicKey = Key.Ed25519Key(value = ""),
                Key.Curve25519Key(value = "")
            ),
            scope = scope
        )
    }

    afterTest {
        scope.cancel()
    }

    context(UserServiceImpl::loadMembers.name) {
        should("do nothing when members already loaded") {
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = true)
            roomStore.update(roomId) { storedRoom }
            cut.loadMembers(roomId)
            continually(500.milliseconds) {
                roomStore.get(roomId).first() shouldBe storedRoom
            }
        }
        should("load members") {
            val aliceEvent = StateEvent(
                MemberEventContent(membership = JOIN),
                EventId("\$event1"),
                alice,
                roomId,
                1234,
                stateKey = alice.full
            )
            val bobEvent = StateEvent(
                MemberEventContent(membership = JOIN),
                EventId("\$event2"),
                bob,
                roomId,
                1234,
                stateKey = bob.full
            )
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, GetMembers(roomId.e(), notMembership = LEAVE)) {
                    GetMembers.Response(
                        setOf(aliceEvent, bobEvent)
                    )
                }
            }
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = false)
            roomStore.update(roomId) { storedRoom }
            val newMemberEvents = mutableListOf<Event<MemberEventContent>>()
            api.sync.subscribe {
                newMemberEvents += it
            }
            cut.loadMembers(roomId)
            roomStore.get(roomId).first { it?.membersLoaded == true }?.membersLoaded shouldBe true
            newMemberEvents shouldContainExactly listOf(aliceEvent, bobEvent)
        }
    }

    context("getPowerLevel") {
        context("the room contains no power_levels event") {
            context("I am the creator of the room") {
                should("return 100") {
                    val createEvent = getCreateEvent(me)
                    roomStateStore.update(createEvent)

                    cut.getPowerLevel(me, roomId).first { it != 0 } shouldBe 100
                }
            }
            context("I am not the creator of the room") {
                should("return 0") {
                    val createEvent = getCreateEvent(alice)
                    roomStateStore.update(createEvent)

                    cut.getPowerLevel(me, roomId).first() shouldBe 0

                }
            }
        }
        context("the room contains a power_level event") {
            beforeTest {
                val createEvent = getCreateEvent(me)
                roomStateStore.update(createEvent)
            }
            should("return the value in the user_id list when I am in the user_id list") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 60,
                            alice to 50
                        )
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.getPowerLevel(me, roomId).first { it != 0 } shouldBe 60
            }
            should("return the usersDefault value when I am not in the user_id list") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(alice to 50),
                        usersDefault = 40
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.getPowerLevel(me, roomId).first { it != 0 } shouldBe 40
            }
        }
    }

    context(UserServiceImpl::canKickUser.name) {
        context("my power level > kick level") {
            should("return true when my level > other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 56,
                            alice to 54
                        ), kick = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canKickUser(alice, roomId).first() shouldBe true
            }
            should("return false when my level == other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 56,
                            alice to 56
                        ), kick = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canKickUser(alice, roomId).first() shouldBe false
            }
            should("return false when my level < other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 56,
                            alice to 57
                        ), kick = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canKickUser(alice, roomId).first() shouldBe false
            }
        }
        context("my power level == kick level") {
            should("return true when my level > other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 54
                        ), kick = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canKickUser(alice, roomId).first() shouldBe true
            }
            should("return false when my level == other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 55
                        ), kick = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canKickUser(alice, roomId).first() shouldBe false
            }
            should("return false when my level < other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 56
                        ), kick = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canKickUser(alice, roomId).first() shouldBe false
            }
        }
        context("my power level < kick level") {
            should("return false when my level > other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 54,
                            alice to 53
                        ), kick = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canKickUser(alice, roomId).first() shouldBe false
            }
            should("return false when my level == other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 54,
                            alice to 54
                        ), kick = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canKickUser(alice, roomId).first() shouldBe false
            }
            should("return false when my level < other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 54,
                            alice to 55
                        ), kick = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canKickUser(alice, roomId).first() shouldBe false
            }
        }
    }

    context(UserServiceImpl::canBanUser.name) {
        context("my power level > ban level") {
            should("return true when my level > other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 56,
                            alice to 54
                        ), ban = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canBanUser(alice, roomId).first() shouldBe true
            }
            should("return false when my level == other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 56,
                            alice to 56
                        ), ban = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canBanUser(alice, roomId).first() shouldBe false
            }
            should("return false when my level < other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 56,
                            alice to 57
                        ), ban = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canBanUser(alice, roomId).first() shouldBe false
            }
        }
        context("my power level == ban level") {
            should("return true when my level > other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 54
                        ), ban = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canBanUser(alice, roomId).first() shouldBe true
            }
            should("return false when my level == other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 55
                        ), ban = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canBanUser(alice, roomId).first() shouldBe false
            }
            should("return false when my level < other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 56
                        ), ban = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canBanUser(alice, roomId).first() shouldBe false
            }
        }
        context("my power level < ban level") {
            should("return false when my level > other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 54,
                            alice to 53
                        ), ban = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canBanUser(alice, roomId).first() shouldBe false
            }
            should("return false when my level == other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 54,
                            alice to 54
                        ), ban = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canBanUser(alice, roomId).first() shouldBe false
            }
            should("return false when my level < other user level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 54,
                            alice to 55
                        ), ban = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canBanUser(alice, roomId).first() shouldBe false
            }
        }
    }

    context(UserServiceImpl::canUnbanUser.name) {
        context("my level > kick level") {
            val kickLevel = 60
            val myLevel = 61
            context("my power level > ban level") {
                val banLevel = 60
                should("return true when my level > other user level") {
                    val otherUserLevel = 60
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe true
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
            }
            context("my power level == ban level") {
                val banLevel = 61
                should("return true when my level > other user level") {
                    val otherUserLevel = 60
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe true
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
            }
            context("my power level < ban level") {
                val banLevel = 62
                should("return false when my level > other user level") {
                    val otherUserLevel = 60
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
            }
        }
        context("my level == kick level") {
            val kickLevel = 61
            val myLevel = 61
            context("my power level > ban level") {
                val banLevel = 60
                should("return true when my level > other user level") {
                    val otherUserLevel = 60
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe true
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
            }
            context("my power level == ban level") {
                val banLevel = 61
                should("return true when my level > other user level") {
                    val otherUserLevel = 60
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe true
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
            }
            context("my power level < ban level") {
                val banLevel = 62
                should("return false when my level > other user level") {
                    val otherUserLevel = 60
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }

            }
        }
        context("my level < kick level") {
            val kickLevel = 62
            val myLevel = 61
            context("my power level > ban level") {
                val banLevel = 60
                should("return false when my level > other user level") {
                    val otherUserLevel = 60
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
            }
            context("my power level == ban level") {
                val banLevel = 61
                should("return false when my level > other user level") {
                    val otherUserLevel = 60
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
            }
            context("my power level < ban level") {
                val banLevel = 62
                should("return false when my level > other user level") {
                    val otherUserLevel = 60
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.update(powerLevelsEvent)
                    cut.canUnbanUser(alice, roomId).first() shouldBe false
                }
            }
        }
    }

    context(UserServiceImpl::canInvite.name) {
        should("return true when my power level > invite level") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 56,
                    ), invite = 55
                )
            )
            roomStateStore.update(powerLevelsEvent)
            cut.canInvite(roomId).first() shouldBe true
        }
        should("return true when my power level == invite level") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                    ), invite = 55
                )
            )
            roomStateStore.update(powerLevelsEvent)
            cut.canInvite(roomId).first() shouldBe true
        }
        should("return false when my power level < invite level") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 54,
                        alice to 53
                    ), invite = 55
                )
            )
            roomStateStore.update(powerLevelsEvent)
            cut.canInvite(roomId).first() shouldBe false
        }
    }

    context(UserServiceImpl::canInviteUser.name) {
        context("User I want to invite is banned") {
            beforeTest {
                val memberEvent = StateEvent(
                    MemberEventContent(membership = Membership.BAN),
                    EventId(""),
                    alice,
                    roomId,
                    0L,
                    stateKey = ""
                )
                val aliceRoomUser = RoomUser(roomId, alice, "Alice", memberEvent)
                roomUserStore.update(alice, roomId) { aliceRoomUser }
            }
            should("return false when my power level > invite level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 56,
                        ), invite = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canInviteUser(alice, roomId).first() shouldBe false
            }
            should("return false when my power level == invite level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                        ), invite = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canInviteUser(alice, roomId).first() shouldBe false
            }
            should("return false when my power level < invite level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 54,
                            alice to 53
                        ), invite = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canInviteUser(alice, roomId).first() shouldBe false
            }
        }

        context("User I want to invite is not banned") {
            beforeTest {
                val memberEvent = StateEvent(
                    MemberEventContent(membership = LEAVE),
                    EventId(""),
                    alice,
                    roomId,
                    0L,
                    stateKey = ""
                )
                val aliceRoomUser = RoomUser(roomId, alice, "Alice", memberEvent)
                roomUserStore.update(alice, roomId) { aliceRoomUser }
            }
            should("return true when my power level > invite level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 56,
                        ), invite = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canInviteUser(alice, roomId).first() shouldBe true
            }
            should("return true when my power level == invite level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                        ), invite = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canInviteUser(alice, roomId).first() shouldBe true
            }
            should("return false when my power level < invite level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 54,
                            alice to 53
                        ), invite = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canInviteUser(alice, roomId).first() shouldBe false
            }
        }
    }

    context("canSetPowerLevelToMax") {
        context("events-map is not null") {
            should("not allow to change the power level when (events power_levels value > own power level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 50
                        ),
                        events = mapOf("m.room.power_levels" to 56),
                        stateDefault = 60
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canSetPowerLevelToMax(alice, roomId).first() shouldBe null
            }

            should("return own power level as max power level value when (events power_levels value == own power level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 50
                        ),
                        events = mapOf("m.room.power_levels" to 55),
                        stateDefault = 60
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canSetPowerLevelToMax(alice, roomId).first() shouldBe 55
            }
        }

        context("events-map is null") {
            should("not allow to change the power level when (stateDefault value > own power level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 50
                        ),
                        stateDefault = 56
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canSetPowerLevelToMax(alice, roomId).first() shouldBe null
            }

            should("return own power level as max power level value when (stateDefault value == own power level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 50
                        ),
                        stateDefault = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canSetPowerLevelToMax(alice, roomId).first() shouldBe 55
            }
        }

        context("oldUserPowerLevel > ownPowerLevel") {
            should("not allow to change the power level when (otherUserId != me)") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 56
                        ),
                        events = mapOf("m.room.power_levels" to 55),
                        stateDefault = 55
                    )
                )
                roomStateStore.update(powerLevelsEvent)
                cut.canSetPowerLevelToMax(alice, roomId).first() shouldBe null
            }
        }

        context("oldUserPowerLevel == ownPowerLevel") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 55
                    ),
                    events = mapOf("m.room.power_levels" to 54),
                    stateDefault = 54
                )
            )
            should("not allow to change the power level when (otherUserId != me)") {
                roomStateStore.update(powerLevelsEvent)
                cut.canSetPowerLevelToMax(alice, roomId).first() shouldBe null
            }
            should("return own power level as max power level value when (otherUserId == me)") {
                roomStateStore.update(powerLevelsEvent)
                cut.canSetPowerLevelToMax(me, roomId).first() shouldBe 55
            }
        }
        should("return own power level as max power level value when all criteria are met") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 54
                    ),
                    events = mapOf("m.room.power_levels" to 52),
                    stateDefault = 52
                )
            )
            roomStateStore.update(powerLevelsEvent)
            cut.canSetPowerLevelToMax(alice, roomId).first() shouldBe 55
        }
    }
})
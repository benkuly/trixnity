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
import kotlinx.coroutines.flow.firstOrNull
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetMembers
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.EventType
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.subscribeContent
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
    lateinit var roomTimelineStore: RoomTimelineStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    lateinit var scope: CoroutineScope
    lateinit var api: MatrixClientServerApiClient
    lateinit var apiConfig: PortableMockEngineConfig
    val json = createMatrixEventJson()
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
        roomTimelineStore = getInMemoryRoomTimelineStore(scope)
        cut = UserServiceImpl(
            roomUserStore,
            roomStore,
            roomStateStore,
            roomTimelineStore,
            globalAccountDataStore,
            api,
            PresenceEventHandler(api),
            listOf(),
            CurrentSyncState(currentSyncState),
            userInfo = UserInfo(
                me, "IAmADeviceId", signingPublicKey = Key.Ed25519Key(value = ""),
                Key.Curve25519Key(value = "")
            ),
            DefaultEventContentSerializerMappings,
            RepositoryTransactionManagerMock(),
            scope = scope
        )

        beforeTest { // some defaults
            roomStateStore.save(getPowerLevelsEvent(PowerLevelsEventContent()))
            roomStateStore.save(getCreateEvent(UserId("creator", "server")))
        }
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
                matrixJsonEndpoint(GetMembers(roomId, notMembership = LEAVE)) {
                    GetMembers.Response(
                        setOf(aliceEvent, bobEvent)
                    )
                }
            }
            val storedRoom = simpleRoom.copy(roomId = roomId, membersLoaded = false)
            roomStore.update(roomId) { storedRoom }
            val newMemberEvents = mutableListOf<Event<MemberEventContent>>()
            api.sync.subscribeContent<MemberEventContent> {
                newMemberEvents += it
            }
            cut.loadMembers(roomId)
            roomStore.get(roomId).first { it?.membersLoaded == true }?.membersLoaded shouldBe true
            newMemberEvents shouldContainExactly listOf(aliceEvent, bobEvent)
        }
    }

    context("getPowerLevel") {
        context("the room contains a power_level event") {
            beforeTest {
                val createEvent = getCreateEvent(me)
                roomStateStore.save(createEvent)
            }
            should("return the value in the user_id list when I am in the user_id list") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 60L,
                            alice to 50
                        )
                    )
                )
                roomStateStore.save(powerLevelsEvent)
                cut.getPowerLevel(roomId, me).first { it != 0L } shouldBe 60L
            }
            should("return the usersDefault value when I am not in the user_id list") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(alice to 50),
                        usersDefault = 40
                    )
                )
                roomStateStore.save(powerLevelsEvent)
                cut.getPowerLevel(roomId, me).first { it != 0L } shouldBe 40
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
                roomStateStore.save(powerLevelsEvent)
                cut.canKickUser(roomId, alice).first() shouldBe true
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
                roomStateStore.save(powerLevelsEvent)
                cut.canKickUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canKickUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canKickUser(roomId, alice).first() shouldBe true
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
                roomStateStore.save(powerLevelsEvent)
                cut.canKickUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canKickUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canKickUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canKickUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canKickUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canBanUser(roomId, alice).first() shouldBe true
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
                roomStateStore.save(powerLevelsEvent)
                cut.canBanUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canBanUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canBanUser(roomId, alice).first() shouldBe true
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
                roomStateStore.save(powerLevelsEvent)
                cut.canBanUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canBanUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canBanUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canBanUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canBanUser(roomId, alice).first() shouldBe false
            }
        }
    }

    context(UserServiceImpl::canUnbanUser.name) {
        context("my level > kick level") {
            val kickLevel = 60L
            val myLevel = 61L
            context("my power level > ban level") {
                val banLevel = 60L
                should("return true when my level > other user level") {
                    val otherUserLevel = 60L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe true
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
            }
            context("my power level == ban level") {
                val banLevel = 61L
                should("return true when my level > other user level") {
                    val otherUserLevel = 60L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe true
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
            }
            context("my power level < ban level") {
                val banLevel = 62L
                should("return false when my level > other user level") {
                    val otherUserLevel = 60L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
            }
        }
        context("my level == kick level") {
            val kickLevel = 61L
            val myLevel = 61L
            context("my power level > ban level") {
                val banLevel = 60L
                should("return true when my level > other user level") {
                    val otherUserLevel = 60L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe true
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
            }
            context("my power level == ban level") {
                val banLevel = 61L
                should("return true when my level > other user level") {
                    val otherUserLevel = 60L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe true
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
            }
            context("my power level < ban level") {
                val banLevel = 62L
                should("return false when my level > other user level") {
                    val otherUserLevel = 60L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }

            }
        }
        context("my level < kick level") {
            val kickLevel = 62L
            val myLevel = 61L
            context("my power level > ban level") {
                val banLevel = 60L
                should("return false when my level > other user level") {
                    val otherUserLevel = 60L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
            }
            context("my power level == ban level") {
                val banLevel = 61L
                should("return false when my level > other user level") {
                    val otherUserLevel = 60L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
            }
            context("my power level < ban level") {
                val banLevel = 62L
                should("return false when my level > other user level") {
                    val otherUserLevel = 60L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level == other user level") {
                    val otherUserLevel = 61L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
                }
                should("return false when my level < other user level") {
                    val otherUserLevel = 62L
                    val powerLevelsEvent = getPowerLevelsEvent(
                        PowerLevelsEventContent(
                            users = mapOf(
                                me to myLevel,
                                alice to otherUserLevel
                            ), kick = kickLevel,
                            ban = banLevel
                        )
                    )
                    roomStateStore.save(powerLevelsEvent)
                    cut.canUnbanUser(roomId, alice).first() shouldBe false
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
            roomStateStore.save(powerLevelsEvent)
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
            roomStateStore.save(powerLevelsEvent)
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
            roomStateStore.save(powerLevelsEvent)
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
                roomStateStore.save(powerLevelsEvent)
                cut.canInviteUser(roomId, alice).first() shouldBe false
            }
            should("return false when my power level == invite level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                        ), invite = 55
                    )
                )
                roomStateStore.save(powerLevelsEvent)
                cut.canInviteUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canInviteUser(roomId, alice).first() shouldBe false
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
                roomStateStore.save(powerLevelsEvent)
                cut.canInviteUser(roomId, alice).first() shouldBe true
            }
            should("return true when my power level == invite level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                        ), invite = 55
                    )
                )
                roomStateStore.save(powerLevelsEvent)
                cut.canInviteUser(roomId, alice).first() shouldBe true
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
                roomStateStore.save(powerLevelsEvent)
                cut.canInviteUser(roomId, alice).first() shouldBe false
            }
        }
    }
    context("canSendEvent") {
        should("be true when allowed to send") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 50
                    ),
                    events = mapOf(EventType(RoomMessageEventContent::class, "m.room.message") to 55),
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSendEvent<RoomMessageEventContent>(roomId).first() shouldBe true
        }
        should("be false when not allowed to send") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 50
                    ),
                    events = mapOf(EventType(RoomMessageEventContent::class, "m.room.message") to 56),
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSendEvent<RoomMessageEventContent>(roomId).first() shouldBe false
        }
        should("use stateDefault") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(me to 50),
                    events = mapOf(),
                    stateDefault = 55
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSendEvent<NameEventContent>(roomId).first() shouldBe false
        }
        should("use eventDefaults") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(me to 50),
                    events = mapOf(),
                    eventsDefault = 55
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSendEvent<RoomMessageEventContent>(roomId).first() shouldBe false
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
                        events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 56),
                        stateDefault = 60L
                    )
                )
                roomStateStore.save(powerLevelsEvent)
                cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
            }

            should("return own power level as max power level value when (events power_levels value == own power level") {
                val powerLevelsEvent = getPowerLevelsEvent(
                    PowerLevelsEventContent(
                        users = mapOf(
                            me to 55,
                            alice to 50
                        ),
                        events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 55),
                        stateDefault = 60L
                    )
                )
                roomStateStore.save(powerLevelsEvent)
                cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe 55
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
                roomStateStore.save(powerLevelsEvent)
                cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
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
                roomStateStore.save(powerLevelsEvent)
                cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe 55
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
                        events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 55),
                        stateDefault = 55
                    )
                )
                roomStateStore.save(powerLevelsEvent)
                cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
            }
        }

        context("oldUserPowerLevel == ownPowerLevel") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 55
                    ),
                    events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 54),
                    stateDefault = 54
                )
            )
            should("not allow to change the power level when (otherUserId != me)") {
                roomStateStore.save(powerLevelsEvent)
                cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
            }
            should("return own power level as max power level value when (otherUserId == me)") {
                roomStateStore.save(powerLevelsEvent)
                cut.canSetPowerLevelToMax(roomId, me).first() shouldBe 55
            }
        }
        should("return own power level as max power level value when all criteria are met") {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 54
                    ),
                    events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 52),
                    stateDefault = 52
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe 55
        }
    }

    context(UserServiceImpl::canRedactEvent.name) {
        val eventByUser = TimelineEvent(
            event = Event.MessageEvent(
                content = RoomMessageEventContent.TextMessageEventContent(body = "Hi"),
                id = EventId("4711"),
                sender = me,
                roomId = roomId,
                originTimestamp = 0L,
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null,
        )
        val eventByOtherUser = TimelineEvent(
            event = Event.MessageEvent(
                content = RoomMessageEventContent.TextMessageEventContent(body = "Hi"),
                id = EventId("4711"),
                sender = UserId("otherUser"),
                roomId = roomId,
                originTimestamp = 0L,
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null,
        )

        should("return true if it is the event of the user and the user's power level is at least as high as the needed event redaction level") {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 40,
                        ),
                        events = mapOf(
                            EventType(RedactionEventContent::class, "m.room.redaction") to 30,
                        )
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            roomTimelineStore.addAll(listOf(eventByUser))
            cut.canRedactEvent(eventByUser.roomId, eventByUser.eventId).firstOrNull() shouldBe true
        }

        should("return true if it is the event of another user but the user's power level is at least as high as the needed redaction power level") {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 40,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            roomTimelineStore.addAll(listOf(eventByOtherUser))
            cut.canRedactEvent(eventByOtherUser.roomId, eventByOtherUser.eventId).firstOrNull() shouldBe true
        }
        should("return false if the user has no high enough power level for event redactions") {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 20,
                        ),
                        events = mapOf(
                            EventType(RedactionEventContent::class, "m.room.redaction") to 30,
                        )
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            roomTimelineStore.addAll(listOf(eventByUser))
            cut.canRedactEvent(eventByUser.roomId, eventByUser.eventId).firstOrNull() shouldBe false
        }
        should("return false if the user has no high enough power level for redactions of events of other users") {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 20,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            roomTimelineStore.addAll(listOf(eventByOtherUser))
            cut.canRedactEvent(eventByOtherUser.roomId, eventByOtherUser.eventId).firstOrNull() shouldBe false
        }

        should("not allow to redact an already redacted event") {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 40,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            val event = TimelineEvent(
                event = Event.MessageEvent(
                    content = RedactedMessageEventContent(eventType = "redacted"),
                    id = EventId("event"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L
                ),
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            roomTimelineStore.addAll(listOf(event))
            cut.canRedactEvent(event.roomId, event.eventId).firstOrNull() shouldBe false
        }

        context("react to changes in the power levels") {
            should("react to changes in the user's power levels") {
                roomStateStore.save(
                    StateEvent(
                        content = PowerLevelsEventContent(
                            users = mapOf(
                                me to 40,
                            ),
                            redact = 30,
                        ),
                        id = EventId("eventId"),
                        sender = me,
                        roomId = roomId,
                        originTimestamp = 0L,
                        stateKey = "",
                    )
                )
                roomTimelineStore.addAll(listOf(eventByOtherUser))
                val resultFlow = cut.canRedactEvent(eventByOtherUser.roomId, eventByOtherUser.eventId)
                resultFlow.first() shouldBe true
                roomStateStore.save(
                    StateEvent(
                        content = PowerLevelsEventContent(
                            users = mapOf(
                                me to 20,
                            ),
                            redact = 30,
                        ),
                        id = EventId("eventId"),
                        sender = me,
                        roomId = roomId,
                        originTimestamp = 0L,
                        stateKey = "",
                    )
                )
                resultFlow.first() shouldBe false
            }
        }
    }
})
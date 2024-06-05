package net.folivo.trixnity.client.notification

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.notification.NotificationService.Notification
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.startOnce
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.idOrNull
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.push.PushAction.Notify
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleSet
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NotificationServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 10_000

    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var roomUserStore: RoomUserStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()
    lateinit var api: MatrixClientServerApiClientImpl
    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var room: RoomServiceMock

    val roomId = RoomId("room", "localhost")
    val user1 = UserId("user1", "localhost")
    val otherUser = UserId("otherUser", "localhost")
    val user1DisplayName = "User1 🦊"
    val currentSyncState = MutableStateFlow(SyncState.RUNNING)

    lateinit var cut: NotificationServiceImpl

    beforeTest {
        currentSyncState.value = SyncState.RUNNING
        room = RoomServiceMock()
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        api = newApi
        apiConfig = newApiConfig
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        roomUserStore = getInMemoryRoomUserStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        roomStore.update(roomId) { Room(roomId) }
        cut = NotificationServiceImpl(
            UserInfo(user1, "", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            api,
            room,
            roomStore,
            roomStateStore,
            roomUserStore,
            globalAccountDataStore,
            json,
            CurrentSyncState(currentSyncState)
        )
    }

    afterTest {
        scope.cancel()
    }

    fun pushRules(overridePushRules: List<PushRule.Override>) = PushRulesEventContent(
        global = PushRuleSet(
            override = overridePushRules,
            content = listOf(),
            room = listOf(),
            sender = listOf(),
            underride = listOf(),
        )
    )

    fun pushRuleDisplayName() = PushRule.Override(
        ruleId = ".m.rule.contains_display_name",
        enabled = true,
        default = true,
        conditions = setOf(PushCondition.ContainsDisplayName),
        actions = setOf(Notify),
    )

    fun pushRuleInvitation() = PushRule.Override(
        ruleId = ".m.rule.invite_for_me",
        enabled = true,
        default = true,
        conditions = setOf(
            PushCondition.EventMatch(key = "type", pattern = "m.room.member"),
            PushCondition.EventMatch(key = "content.membership", "invite"),
            PushCondition.EventMatch(key = "state_key", pattern = user1.full),
        ),
        actions = setOf(Notify),
    )

    fun pushRuleEventMatchTriggeredDontNotify() = PushRule.Override(
        ruleId = "customRule1",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.EventMatch(key = "content.body", "*User*")),
        actions = setOf(),
    )

    fun pushRuleEventMatchTriggeredNotEnabled() = PushRule.Override(
        ruleId = "customRule1",
        enabled = false,
        default = false,
        conditions = setOf(PushCondition.EventMatch(key = "content.body", "*User*")),
        actions = setOf(Notify),
    )

    fun pushRuleMemberCountGreaterEqual2() = PushRule.Override(
        ruleId = "customRule2",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.RoomMemberCount(">=2")),
        actions = setOf(Notify)
    )

    fun pushRulePowerLevelRoom() = PushRule.Override(
        ruleId = "customRule3",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.SenderNotificationPermission("room")),
        actions = setOf(Notify)
    )

    fun pushRuleNoCondition() = PushRule.Override(
        ruleId = "customRule4",
        enabled = true,
        default = false,
        conditions = setOf(),
        actions = setOf(Notify)
    )

    fun pushRuleWithMultipleConditions() = PushRule.Override(
        ruleId = "customRule5",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.EventMatch("content.body", "*User*")),
        actions = setOf(Notify)
    )

    fun messageEventWithContent(
        roomId: RoomId,
        content: MessageEventContent,
        decryptedContent: MessageEventContent = content,
        sender: UserId = otherUser
    ) = TimelineEvent(
        event = MessageEvent(
            content = content,
            id = EventId("\$event-${content.hashCode()}"),
            sender = sender,
            roomId = roomId,
            originTimestamp = 0L,
        ),
        content = Result.success(decryptedContent),
        previousEventId = null,
        nextEventId = null,
        gap = null
    )

    suspend fun setUser1DisplayName(roomId: RoomId) {
        roomUserStore.update(
            user1,
            roomId
        ) {
            RoomUser(
                roomId,
                user1,
                user1DisplayName,
                StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("JOIN"),
                    user1,
                    roomId,
                    0,
                    stateKey = ""
                )
            )
        }
    }

    suspend fun checkNoNotification() = coroutineScope {
        val notifications = async { cut.getNotifications(0.seconds).first() }
        api.sync.startOnce(
            getBatchToken = { null },
            setBatchToken = {},
        ).getOrThrow()

        continually(50.milliseconds) {
            notifications.isCompleted shouldBe false
        }
        notifications.cancel()
    }

    context(NotificationServiceImpl::getNotifications.name) {
        should("wait for sync to be started or running") {
            currentSyncState.value = SyncState.INITIAL_SYNC
            globalAccountDataStore.save(
                GlobalAccountDataEvent(
                    pushRules(
                        listOf(
                            pushRuleInvitation(),
                        )
                    )
                )
            )
            val invitation = StrippedStateEvent(
                content = MemberEventContent(
                    membership = Membership.INVITE,
                    displayName = user1DisplayName,
                ),
                sender = otherUser,
                roomId = roomId,
                stateKey = user1.full,
            )
            apiConfig.endpoints {
                matrixJsonEndpoint(Sync(timeout = 0)) {
                    Sync.Response(
                        nextBatch = "next",
                        room = Sync.Response.Rooms(
                            invite = mapOf(
                                roomId to Sync.Response.Rooms.InvitedRoom(
                                    inviteState = Sync.Response.Rooms.InvitedRoom.InviteState(
                                        events = listOf(invitation)
                                    )
                                )
                            )
                        )
                    )
                }
            }
            val notification = async { cut.getNotifications(0.seconds).first() }
            delay(50)
            api.sync.startOnce(
                getBatchToken = { null },
                setBatchToken = {},
            ).getOrThrow()
            continually(50.milliseconds) {
                notification.isCompleted shouldBe false
            }
            notification.cancel()
        }
        should("do nothing when no events") {
            apiConfig.endpoints {
                matrixJsonEndpoint(Sync(timeout = 0)) {
                    Sync.Response("next")
                }
            }
            globalAccountDataStore.save(GlobalAccountDataEvent(pushRules(listOf(pushRuleDisplayName()))))

            checkNoNotification()
        }
        should("notify on invite") {
            globalAccountDataStore.save(
                GlobalAccountDataEvent(
                    pushRules(
                        listOf(
                            pushRuleInvitation(),
                        )
                    )
                )
            )
            val invitation = StrippedStateEvent(
                content = MemberEventContent(
                    membership = Membership.INVITE,
                    displayName = user1DisplayName,
                ),
                sender = otherUser,
                roomId = roomId,
                stateKey = user1.full,
            )
            apiConfig.endpoints {
                matrixJsonEndpoint(Sync(timeout = 0)) {
                    Sync.Response(
                        nextBatch = "next",
                        room = Sync.Response.Rooms(
                            invite = mapOf(
                                roomId to Sync.Response.Rooms.InvitedRoom(
                                    inviteState = Sync.Response.Rooms.InvitedRoom.InviteState(
                                        events = listOf(invitation)
                                    )
                                )
                            )
                        )
                    )
                }
            }
            val notification = async { cut.getNotifications(0.seconds).first() }
            delay(50)
            api.sync.startOnce(
                getBatchToken = { null },
                setBatchToken = {},
            ).getOrThrow()
            notification.await() shouldBe Notification(invitation, setOf(Notify))
        }
        context("new timeline events") {
            val timelineEvent = messageEventWithContent(
                roomId, RoomMessageEventContent.TextBased.Text(
                    body = "Hello User1 🦊!"
                )
            )
            beforeTest {
                setUser1DisplayName(roomId)
                globalAccountDataStore.save(GlobalAccountDataEvent(pushRules(listOf(pushRuleDisplayName()))))
                apiConfig.endpoints {
                    matrixJsonEndpoint(Sync(timeout = 0)) {
                        Sync.Response("next")
                    }
                }
                room.returnGetTimelineEventsFromNowOn = flowOf(timelineEvent)
            }
            should("check push rules and notify") {
                cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event, setOf(Notify))
            }
            should("have correct order") {
                val timelineEvents = (0..99).map {
                    messageEventWithContent(
                        roomId, RoomMessageEventContent.TextBased.Text(
                            body = "Hello User1 🦊! ($it)"
                        )
                    )
                }
                room.returnGetTimelineEventsFromNowOn = timelineEvents.asFlow()
                cut.getNotifications(0.seconds).take(100).toList() shouldBe timelineEvents.map {
                    Notification(it.event, setOf(Notify))
                }
            }
            should("not notify on own messages") {
                val timelineEvents = (0..9).map {
                    messageEventWithContent(
                        roomId, RoomMessageEventContent.TextBased.Text(
                            body = "Hello User1 🦊! ($it)"
                        ),
                        sender = if (it == 0 || it == 9) user1 else otherUser
                    )
                }
                room.returnGetTimelineEventsFromNowOn = timelineEvents.asFlow()
                cut.getNotifications(0.seconds).take(8).toList() shouldBe
                        timelineEvents.drop(1).dropLast(1).map {
                            Notification(it.event, setOf(Notify))
                        }
            }
        }
        context("new decrypted timeline events") {
            val timelineEvent = messageEventWithContent(
                roomId, MegolmEncryptedMessageEventContent(
                    "", Key.Curve25519Key(null, ""), "", ""
                ), RoomMessageEventContent.TextBased.Text(
                    body = "Hello User1 🦊!"
                )
            )
            beforeTest {
                apiConfig.endpoints {
                    matrixJsonEndpoint(Sync(timeout = 0)) {
                        Sync.Response("next")
                    }
                }
                room.returnGetTimelineEventsFromNowOn = flowOf(timelineEvent)
            }
            should("check push rules and notify") {
                setUser1DisplayName(roomId)
                globalAccountDataStore.save(GlobalAccountDataEvent(pushRules(listOf(pushRuleDisplayName()))))

                assertSoftly(cut.getNotifications(0.seconds).first()) {
                    event.idOrNull shouldBe timelineEvent.eventId
                    event.content shouldBe timelineEvent.content?.getOrThrow()
                }
            }
        }
        context("push rules") {
            val timelineEvent = messageEventWithContent(
                roomId, RoomMessageEventContent.TextBased.Text(
                    body = "Hello User1 🦊!"
                )
            )
            beforeTest {
                apiConfig.endpoints {
                    matrixJsonEndpoint(Sync(timeout = 0)) {
                        Sync.Response("next")
                    }
                }
                room.returnGetTimelineEventsFromNowOn = flowOf(timelineEvent)
            }
            context("room member count") {
                beforeTest {
                    globalAccountDataStore.save(
                        GlobalAccountDataEvent(pushRules(listOf(pushRuleMemberCountGreaterEqual2())))
                    )
                }
                should("notify when met") {
                    roomStore.update(roomId) {
                        Room(roomId, name = RoomDisplayName(summary = RoomSummary(joinedMemberCount = 2)))
                    }
                    cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event, setOf(Notify))
                }
                should("not notify when not met") {
                    roomStore.update(roomId) {
                        Room(roomId, name = RoomDisplayName(summary = RoomSummary(joinedMemberCount = 1)))
                    }
                    checkNoNotification()
                }
            }
            context("permission level") {
                beforeTest {
                    globalAccountDataStore.save(
                        GlobalAccountDataEvent(pushRules(listOf(pushRulePowerLevelRoom())))
                    )
                }
                should("notify when met") {
                    roomStateStore.save(
                        StateEvent(
                            PowerLevelsEventContent(
                                notifications = PowerLevelsEventContent.Notifications(50),
                                users = mapOf(otherUser to 50, user1 to 30)
                            ),
                            id = EventId("\$powerLevel"),
                            sender = user1,
                            roomId = roomId,
                            originTimestamp = 0L,
                            stateKey = "",
                        )
                    )
                    cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event, setOf(Notify))
                }
                should("not notify when not met") {
                    roomStateStore.save(
                        StateEvent(
                            PowerLevelsEventContent(
                                notifications = PowerLevelsEventContent.Notifications(100),
                                users = mapOf(otherUser to 50, user1 to 30)
                            ),
                            id = EventId("\$powerLevel"),
                            sender = user1,
                            roomId = roomId,
                            originTimestamp = 0L,
                            stateKey = "",
                        )
                    )
                    checkNoNotification()
                }
            }
            should("not notify when push rule disabled") {
                globalAccountDataStore.save(
                    GlobalAccountDataEvent(
                        pushRules(listOf(pushRuleEventMatchTriggeredNotEnabled()))
                    )
                )
                checkNoNotification()
            }
            context("multiple conditions") {
                should("should notify when all conditions match") {
                    globalAccountDataStore.save(
                        GlobalAccountDataEvent(
                            pushRules(listOf(pushRuleWithMultipleConditions()))
                        )
                    )
                    setUser1DisplayName(roomId)
                    cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event, setOf(Notify))
                }
                should("not notify when one condition matches") {
                    globalAccountDataStore.save(
                        GlobalAccountDataEvent(
                            pushRules(listOf(pushRuleWithMultipleConditions()))
                        )
                    )
                    checkNoNotification()
                }
            }
            should("always notify when no conditions") {
                globalAccountDataStore.save(
                    GlobalAccountDataEvent(
                        pushRules(listOf(pushRuleNoCondition()))
                    )
                )
                cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event, setOf(Notify))
            }
            should("override") {
                globalAccountDataStore.save(
                    GlobalAccountDataEvent(
                        PushRulesEventContent(
                            global = PushRuleSet(
                                override = listOf(
                                    PushRule.Override(
                                        ruleId = "customRule10",
                                        enabled = true,
                                        default = false,
                                        conditions = setOf(PushCondition.EventMatch("content.body", "*User*")),
                                        actions = setOf()
                                    ),
                                    pushRuleDisplayName()
                                )
                            )
                        )
                    )
                )
                setUser1DisplayName(roomId)

                checkNoNotification()
            }

            context("room push rules") {
                should("ignore other room's rules") {
                    globalAccountDataStore.save(
                        GlobalAccountDataEvent(
                            PushRulesEventContent(
                                global = PushRuleSet(
                                    override = listOf(
                                        pushRuleDisplayName()
                                    ),
                                    room = listOf(
                                        PushRule.Room(
                                            roomId = RoomId("!andNowForSomethingCompletelyDifferent:localhost"),
                                            enabled = true,
                                            default = false,
                                            actions = setOf(Notify)
                                        )
                                    )
                                )
                            )
                        )
                    )

                    checkNoNotification()
                }
                should("consider this room's rule") {
                    globalAccountDataStore.save(
                        GlobalAccountDataEvent(
                            PushRulesEventContent(
                                global = PushRuleSet(
                                    override = listOf(
                                        pushRuleDisplayName()
                                    ),
                                    room = listOf(
                                        PushRule.Room(
                                            roomId = roomId,
                                            enabled = true,
                                            default = false,
                                            actions = setOf(Notify)
                                        )
                                    )
                                )
                            )
                        )
                    )

                    cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event, setOf(Notify))
                }
            }
        }
        context("push actions") {
            val timelineEvent = messageEventWithContent(
                roomId, RoomMessageEventContent.TextBased.Text(
                    body = "Hello User1 🦊!"
                )
            )
            beforeTest {
                apiConfig.endpoints {
                    matrixJsonEndpoint(Sync(timeout = 0)) {
                        Sync.Response("next")
                    }
                }
                room.returnGetTimelineEventsFromNowOn = flowOf(timelineEvent)
            }
            should("not notify when action says it") {
                globalAccountDataStore.save(
                    GlobalAccountDataEvent(pushRules(listOf(pushRuleEventMatchTriggeredDontNotify())))
                )
                checkNoNotification()
            }
        }
    }
    context(NotificationServiceImpl::getEventProperty.name) {
        should("get property from path") {
            val input = lazy { JsonObject(mapOf("a" to JsonObject(mapOf("b" to JsonPrimitive("value"))))) }
            cut.getEventProperty(input, "a.b") shouldBe JsonPrimitive("value")
        }
        should("return null when property not found") {
            val input = lazy { JsonObject(mapOf("a" to JsonObject(mapOf("b.c" to JsonPrimitive("value"))))) }
            cut.getEventProperty(input, "a.b.c") shouldBe null
        }
        should("escape path") {
            val input = lazy { JsonObject(mapOf("a" to JsonObject(mapOf("b.c" to JsonPrimitive("value"))))) }
            cut.getEventProperty(input, "a.b\\.c") shouldBe JsonPrimitive("value")
        }
    }
}

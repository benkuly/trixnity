package net.folivo.trixnity.client.push

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.getEventId
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.push.IPushService.Notification
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.push.PushAction.DontNotify
import net.folivo.trixnity.core.model.push.PushAction.Notify
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PushServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 10_000

    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val json = createMatrixJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var api: MatrixClientServerApiClient
    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var room: RoomServiceMock

    val roomId = RoomId("room", "localhost")
    val user1 = UserId("user1", "localhost")
    val otherUser = UserId("otherUser", "localhost")
    val user1DisplayName = "User1 🦊"

    lateinit var cut: PushService

    beforeTest {
        room = RoomServiceMock()
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        api = newApi
        apiConfig = newApiConfig
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        store.account.userId.value = user1
        store.room.update(roomId) { Room(roomId) }
        cut = PushService(api, room, store, json)
    }

    afterTest {
        storeScope.cancel()
    }

    fun pushRules(contentPushRules: List<PushRule>) = PushRulesEventContent(
        global = mapOf(
            PushRuleKind.CONTENT to contentPushRules,
            PushRuleKind.OVERRIDE to listOf(),
            PushRuleKind.ROOM to listOf(),
            PushRuleKind.SENDER to listOf(),
            PushRuleKind.UNDERRIDE to listOf(),
        )
    )

    fun pushRuleDisplayName() = PushRule(
        ruleId = ".m.rule.contains_display_name",
        enabled = true,
        default = true,
        conditions = setOf(PushCondition.ContainsDisplayName),
        actions = setOf(Notify),
    )

    fun pushRuleInvitation() = PushRule(
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

    fun pushRuleEventMatchTriggeredDontNotify() = PushRule(
        ruleId = "customRule1",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.EventMatch(key = "content.body", "*User*")),
        actions = setOf(DontNotify),
    )

    fun pushRuleEventMatchTriggeredNotEnabled() = PushRule(
        ruleId = "customRule1",
        enabled = false,
        default = false,
        conditions = setOf(PushCondition.EventMatch(key = "content.body", "*User*")),
        actions = setOf(Notify),
    )

    fun pushRuleMemberCountGreaterEqual2() = PushRule(
        ruleId = "customRule2",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.RoomMemberCount(">=2")),
        actions = setOf(Notify)
    )

    fun pushRulePowerLevelRoom() = PushRule(
        ruleId = "customRule3",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.SenderNotificationPermission("room")),
        actions = setOf(Notify)
    )

    fun pushRuleNoCondition() = PushRule(
        ruleId = "customRule4",
        enabled = true,
        default = false,
        conditions = setOf(),
        actions = setOf(Notify)
    )

    fun pushRuleWithMultipleConditions() = PushRule(
        ruleId = "customRule5",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.EventMatch("content.body", "*User*")),
        actions = setOf(Notify)
    )

    fun messageEventWithContent(
        roomId: RoomId, content: MessageEventContent, decryptedContent: MessageEventContent = content
    ) = TimelineEvent(
        event = MessageEvent(
            content = content,
            id = EventId("\$event-${content.hashCode()}"),
            sender = otherUser,
            roomId = roomId,
            originTimestamp = 0L,
        ),
        content = Result.success(decryptedContent),
        previousEventId = null,
        nextEventId = null,
        gap = null
    )

    suspend fun setUser1DisplayName(roomId: RoomId) {
        store.roomUser.update(
            user1,
            roomId
        ) {
            RoomUser(
                roomId,
                user1,
                user1DisplayName,
                Event.StateEvent(
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
        api.sync.startOnce().getOrThrow()

        continually(50.milliseconds) {
            notifications.isCompleted shouldBe false
        }
        notifications.cancel()
    }

    context(PushService::getNotifications.name) {
        should("do nothing when no events") {
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, Sync(timeout = 0)) {
                    Sync.Response("next")
                }
            }
            store.globalAccountData.update(GlobalAccountDataEvent(pushRules(listOf(pushRuleDisplayName()))))

            checkNoNotification()
        }
        should("notify on invite") {
            store.globalAccountData.update(
                GlobalAccountDataEvent(
                    pushRules(
                        listOf(
                            pushRuleInvitation(),
                        )
                    )
                )
            )
            val invitation = Event.StrippedStateEvent(
                content = MemberEventContent(
                    membership = Membership.INVITE,
                    displayName = user1DisplayName,
                ),
                sender = otherUser,
                roomId = roomId,
                stateKey = user1.full,
            )
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, Sync(timeout = 0)) {
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
            api.sync.startOnce().getOrThrow()
            notification.await() shouldBe Notification(invitation)
        }
        context("new timeline events") {
            val timelineEvent = messageEventWithContent(
                roomId, RoomMessageEventContent.TextMessageEventContent(
                    body = "Hello User1 🦊!"
                )
            )
            beforeTest {
                setUser1DisplayName(roomId)
                store.globalAccountData.update(GlobalAccountDataEvent(pushRules(listOf(pushRuleDisplayName()))))
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(timeout = 0)) {
                        Sync.Response("next")
                    }
                }
                room.returnGetTimelineEventsFromNowOn = flowOf(timelineEvent)
            }
            should("check push rules and notify") {
                cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event)
            }
            should("have correct order") {
                val timelineEvents = (0..99).map {
                    messageEventWithContent(
                        roomId, RoomMessageEventContent.TextMessageEventContent(
                            body = "Hello User1 🦊! ($it)"
                        )
                    )
                }
                room.returnGetTimelineEventsFromNowOn = timelineEvents.asFlow()
                cut.getNotifications(0.seconds).take(100).toList() shouldBe timelineEvents.map {
                    Notification(it.event)
                }
            }
        }
        context("new decrypted timeline events") {
            val timelineEvent = messageEventWithContent(
                roomId, EncryptedEventContent.MegolmEncryptedEventContent(
                    "", Key.Curve25519Key(null, ""), "", ""
                ), RoomMessageEventContent.TextMessageEventContent(
                    body = "Hello User1 🦊!"
                )
            )
            beforeTest {
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(timeout = 0)) {
                        Sync.Response("next")
                    }
                }
                room.returnGetTimelineEventsFromNowOn = flowOf(timelineEvent)
            }
            should("check push rules and notify") {
                store.account.userId.value = user1
                setUser1DisplayName(roomId)
                store.globalAccountData.update(GlobalAccountDataEvent(pushRules(listOf(pushRuleDisplayName()))))

                assertSoftly(cut.getNotifications(0.seconds).first()) {
                    event.getEventId() shouldBe timelineEvent.eventId
                    event.content shouldBe timelineEvent.content?.getOrThrow()
                }
            }
        }
        context("push rules") {
            val timelineEvent = messageEventWithContent(
                roomId, RoomMessageEventContent.TextMessageEventContent(
                    body = "Hello User1 🦊!"
                )
            )
            beforeTest {
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(timeout = 0)) {
                        Sync.Response("next")
                    }
                }
                room.returnGetTimelineEventsFromNowOn = flowOf(timelineEvent)
            }
            context("room member count") {
                beforeTest {
                    store.globalAccountData.update(
                        GlobalAccountDataEvent(pushRules(listOf(pushRuleMemberCountGreaterEqual2())))
                    )
                }
                should("notify when met") {
                    store.room.update(roomId) {
                        Room(roomId, name = RoomDisplayName(summary = RoomSummary(joinedMemberCount = 2)))
                    }
                    cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event)
                }
                should("not notify when not met") {
                    store.room.update(roomId) {
                        Room(roomId, name = RoomDisplayName(summary = RoomSummary(joinedMemberCount = 1)))
                    }
                    checkNoNotification()
                }
            }
            context("permission level") {
                beforeTest {
                    store.globalAccountData.update(
                        GlobalAccountDataEvent(pushRules(listOf(pushRulePowerLevelRoom())))
                    )
                }
                should("notify when met") {
                    store.roomState.update(
                        Event.StateEvent(
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
                    cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event)
                }
                should("not notify when not met") {
                    store.roomState.update(
                        Event.StateEvent(
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
                store.globalAccountData.update(
                    GlobalAccountDataEvent(
                        pushRules(listOf(pushRuleEventMatchTriggeredNotEnabled()))
                    )
                )
                checkNoNotification()
            }
            context("multiple conditions") {
                should("should notify when all conditions match") {
                    store.globalAccountData.update(
                        GlobalAccountDataEvent(
                            pushRules(listOf(pushRuleWithMultipleConditions()))
                        )
                    )
                    setUser1DisplayName(roomId)
                    cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event)
                }
                should("not notify when one condition matches") {
                    store.globalAccountData.update(
                        GlobalAccountDataEvent(
                            pushRules(listOf(pushRuleWithMultipleConditions()))
                        )
                    )
                    checkNoNotification()
                }
            }
            should("always notify when no conditions") {
                store.globalAccountData.update(
                    GlobalAccountDataEvent(
                        pushRules(listOf(pushRuleNoCondition()))
                    )
                )
                cut.getNotifications(0.seconds).first() shouldBe Notification(timelineEvent.event)
            }
            should("override") {
                store.globalAccountData.update(
                    GlobalAccountDataEvent(
                        PushRulesEventContent(
                            global = mapOf(
                                PushRuleKind.OVERRIDE to listOf(
                                    PushRule(
                                        ruleId = "customRule10",
                                        enabled = true,
                                        default = false,
                                        conditions = setOf(PushCondition.EventMatch("content.body", "*User*")),
                                        actions = setOf(DontNotify)
                                    )
                                ),
                                PushRuleKind.CONTENT to listOf(
                                    pushRuleDisplayName()
                                )
                            )
                        )
                    )
                )
                setUser1DisplayName(roomId)

                checkNoNotification()
            }
        }
        context("push actions") {
            val timelineEvent = messageEventWithContent(
                roomId, RoomMessageEventContent.TextMessageEventContent(
                    body = "Hello User1 🦊!"
                )
            )
            beforeTest {
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(timeout = 0)) {
                        Sync.Response("next")
                    }
                }
                room.returnGetTimelineEventsFromNowOn = flowOf(timelineEvent)
            }
            should("not notify when action says it") {
                store.globalAccountData.update(
                    GlobalAccountDataEvent(pushRules(listOf(pushRuleEventMatchTriggeredDontNotify())))
                )
                checkNoNotification()
            }
        }
    }
}

package de.connect2x.trixnity.client.notification

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import de.connect2x.trixnity.core.model.push.PushAction
import de.connect2x.trixnity.core.model.push.PushRule
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import kotlin.test.Test

class EvaluatePushRulesTest : TrixnityBaseTest() {

    private val roomId = RoomId("!room:localhost")
    private val userId = UserId("user1", "localhost")

    private class TestPushRuleMatcher : PushRuleMatcher {
        var doesMatch = false
        override suspend fun match(
            rule: PushRule,
            event: ClientEvent<*>,
            eventJson: Lazy<JsonObject?>
        ): Boolean = doesMatch
    }

    private val pushRuleMatcher = TestPushRuleMatcher().apply {
        scheduleSetup { doesMatch = false }
    }

    private val cut = EvaluatePushRulesImpl(
        pushRuleMatcher,
        createMatrixEventJson(),
    )

    private fun messageEvent(content: MessageEventContent): ClientEvent<*> =
        ClientEvent.RoomEvent.MessageEvent(
            content = content,
            id = EventId("bla"),
            roomId = roomId,
            sender = userId,
            originTimestamp = 1234,
            unsigned = null,
        )

    private fun stateEvent(content: StateEventContent, stateKey: String = ""): ClientEvent<*> =
        ClientEvent.RoomEvent.StateEvent(
            content = content,
            id = EventId("bla"),
            roomId = roomId,
            sender = userId,
            originTimestamp = 1234,
            stateKey = stateKey,
            unsigned = null,
        )

    @Test
    fun `no rule match`() = runTest {
        pushRuleMatcher.doesMatch = false
        cut.invoke(
            messageEvent(Text("Hello!")),
            listOf(
                PushRule.Content(
                    ruleId = "dino",
                    default = true,
                    enabled = false,
                    actions = setOf(PushAction.Notify),
                    "*!"
                )
            )
        ) shouldBe null
    }

    @Test
    fun `match message`() = runTest {
        pushRuleMatcher.doesMatch = true
        cut.invoke(
            messageEvent(Text("Hello!")),
            listOf(
                PushRule.Content(
                    ruleId = "dino",
                    default = true,
                    enabled = true,
                    actions = setOf(PushAction.Notify),
                    "*!"
                )
            )
        ) shouldBe setOf(PushAction.Notify)
    }

    @Test
    fun `match state`() = runTest {
        pushRuleMatcher.doesMatch = true
        cut.invoke(
            stateEvent(MemberEventContent(membership = Membership.JOIN), stateKey = userId.full),
            listOf(
                PushRule.Content(
                    ruleId = "dino",
                    default = true,
                    enabled = true,
                    actions = setOf(PushAction.Notify),
                    "*!"
                )
            )
        ) shouldBe setOf(PushAction.Notify)
    }

    @Test
    fun `ignore no actions`() = runTest {
        pushRuleMatcher.doesMatch = true
        cut.invoke(
            messageEvent(Text("Hello!")),
            listOf(
                PushRule.Content(
                    ruleId = "dino",
                    default = true,
                    enabled = true,
                    actions = setOf(),
                    "*!"
                )
            )
        ) shouldBe null
    }
}

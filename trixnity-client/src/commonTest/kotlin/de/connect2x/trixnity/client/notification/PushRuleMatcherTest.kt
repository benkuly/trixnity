package de.connect2x.trixnity.client.notification

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import de.connect2x.trixnity.core.model.push.PushCondition
import de.connect2x.trixnity.core.model.push.PushRule
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import kotlin.test.Test

class PushRuleMatcherTest : TrixnityBaseTest() {

    private val roomId = RoomId("!room:localhost")
    private val userId = UserId("user1", "localhost")

    private val someEvent = ClientEvent.RoomEvent.MessageEvent(
        content = Text("hi!"),
        id = EventId("event"),
        roomId = roomId,
        sender = userId,
        originTimestamp = 1234,
        unsigned = null,
    )
    private val someEventJson = lazy { JsonObject(emptyMap()) }

    private val someStateEvent = ClientEvent.RoomEvent.StateEvent(
        content = MemberEventContent(membership = Membership.JOIN),
        id = EventId("bla"),
        roomId = roomId,
        sender = userId,
        originTimestamp = 1234,
        stateKey = userId.full,
        unsigned = null,
    )

    private class TestPushRuleConditionMatcher : PushRuleConditionMatcher {
        var doesMatch = mutableListOf<Boolean>()
        override suspend fun match(
            condition: PushCondition,
            event: ClientEvent<*>,
            eventJson: Lazy<JsonObject?>
        ): Boolean = doesMatch.removeFirst()
    }

    private val conditionMatcher = TestPushRuleConditionMatcher().apply {
        scheduleSetup { doesMatch = mutableListOf() }
    }

    @Test
    fun `Override - disabled - no match`() = runTest {
        conditionMatcher.doesMatch.add(true)
        PushRuleMatcherImpl.match(
            PushRule.Override(
                ruleId = "rule",
                default = false,
                enabled = false,
                actions = setOf(),
                conditions = setOf(PushCondition.ContainsDisplayName)
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe false
    }

    @Test
    fun `Override - null conditions - match`() = runTest {
        conditionMatcher.doesMatch.add(true)
        PushRuleMatcherImpl.match(
            PushRule.Override(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = null
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe true
    }

    @Test
    fun `Override - empty conditions - match`() = runTest {
        conditionMatcher.doesMatch.add(true)
        PushRuleMatcherImpl.match(
            PushRule.Override(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = setOf()
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe true
    }

    @Test
    fun `Override - all false conditions - no match`() = runTest {
        conditionMatcher.doesMatch.addAll(listOf(false, false))
        PushRuleMatcherImpl.match(
            PushRule.Override(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.RoomMemberCount("1"))
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe false
    }

    @Test
    fun `Override - one false condition - no match`() = runTest {
        conditionMatcher.doesMatch.addAll(listOf(true, false))
        PushRuleMatcherImpl.match(
            PushRule.Override(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.RoomMemberCount("1"))
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe false
    }

    @Test
    fun `Override - all true condition - match`() = runTest {
        conditionMatcher.doesMatch.addAll(listOf(true, true))
        PushRuleMatcherImpl.match(
            PushRule.Override(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.RoomMemberCount("1"))
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe true
    }

    @Test
    fun `Content - disabled - no match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Content(
                ruleId = "rule",
                default = false,
                enabled = false,
                actions = setOf(),
                pattern = "*!",
            ),
            someEvent,
        ) shouldBe false
    }

    @Test
    fun `Content - no RoomMessageEventContent - no match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Content(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                pattern = "*!",
            ),
            someStateEvent,
        ) shouldBe false
    }

    @Test
    fun `Content - message - no match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Content(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                pattern = "dino",
            ),
            someEvent,
        ) shouldBe false
    }

    @Test
    fun `Content - message - match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Content(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                pattern = "*!",
            ),
            someEvent,
        ) shouldBe true
    }

    @Test
    fun `Room - disabled - no match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Room(
                roomId = roomId,
                default = false,
                enabled = false,
                actions = setOf(),
            ),
            someEvent,
        ) shouldBe false
    }

    @Test
    fun `Room - no match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Room(
                roomId = RoomId("!other"),
                default = false,
                enabled = true,
                actions = setOf(),
            ),
            someEvent,
        ) shouldBe false
    }

    @Test
    fun `Room - match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Room(
                roomId = roomId,
                default = false,
                enabled = true,
                actions = setOf(),
            ),
            someEvent,
        ) shouldBe true
    }

    @Test
    fun `Sender - disabled - no match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Sender(
                userId = userId,
                default = false,
                enabled = false,
                actions = setOf(),
            ),
            someEvent,
        ) shouldBe false
    }

    @Test
    fun `Sender - no match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Sender(
                userId = UserId("@other:localhost"),
                default = false,
                enabled = true,
                actions = setOf(),
            ),
            someEvent,
        ) shouldBe false
    }

    @Test
    fun `Sender - match`() = runTest {
        PushRuleMatcherImpl.match(
            PushRule.Sender(
                userId = userId,
                default = false,
                enabled = true,
                actions = setOf(),
            ),
            someEvent,
        ) shouldBe true
    }

    @Test
    fun `Underride - disabled - no match`() = runTest {
        conditionMatcher.doesMatch.add(true)
        PushRuleMatcherImpl.match(
            PushRule.Underride(
                ruleId = "rule",
                default = false,
                enabled = false,
                actions = setOf(),
                conditions = setOf(PushCondition.ContainsDisplayName)
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe false
    }

    @Test
    fun `Underride - null conditions - match`() = runTest {
        conditionMatcher.doesMatch.add(true)
        PushRuleMatcherImpl.match(
            PushRule.Underride(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = null
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe true
    }

    @Test
    fun `Underride - empty conditions - match`() = runTest {
        conditionMatcher.doesMatch.add(true)
        PushRuleMatcherImpl.match(
            PushRule.Underride(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = setOf()
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe true
    }

    @Test
    fun `Underride - all false conditions - no match`() = runTest {
        conditionMatcher.doesMatch.addAll(listOf(false, false))
        PushRuleMatcherImpl.match(
            PushRule.Underride(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.RoomMemberCount("1"))
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe false
    }

    @Test
    fun `Underride - one false condition - no match`() = runTest {
        conditionMatcher.doesMatch.addAll(listOf(true, false))
        PushRuleMatcherImpl.match(
            PushRule.Underride(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.RoomMemberCount("1"))
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe false
    }

    @Test
    fun `Underride - all true condition - match`() = runTest {
        conditionMatcher.doesMatch.addAll(listOf(true, true))
        PushRuleMatcherImpl.match(
            PushRule.Underride(
                ruleId = "rule",
                default = false,
                enabled = true,
                actions = setOf(),
                conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.RoomMemberCount("1"))
            ),
            someEvent,
            someEventJson,
            conditionMatcher,
        ) shouldBe true
    }
}
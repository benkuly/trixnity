package net.folivo.trixnity.client.notification

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.scheduleSetup
import kotlin.test.Test

class PushRuleConditionMatcherTest : TrixnityBaseTest() {

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
    fun `match - Override - disabled - no match`() = runTest {
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
    fun `match - Override - null conditions - match`() = runTest {
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
    fun `match - Override - empty conditions - match`() = runTest {
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
    fun `match - Override - all false conditions - no match`() = runTest {
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
    fun `match - Override - one false condition - no match`() = runTest {
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
    fun `match - Override - all true condition - match`() = runTest {
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
    fun `match - Content - disabled - no match`() = runTest {
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
    fun `match - Content - no RoomMessageEventContent - no match`() = runTest {
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
    fun `match - Content - message - no match`() = runTest {
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
    fun `match - Content - message - match`() = runTest {
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
    fun `match - Room - disabled - no match`() = runTest {
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
    fun `match - Room - no match`() = runTest {
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
    fun `match - Room - match`() = runTest {
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
    fun `match - Sender - disabled - no match`() = runTest {
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
    fun `match - Sender - no match`() = runTest {
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
    fun `match - Sender - match`() = runTest {
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
    fun `match - Underride - disabled - no match`() = runTest {
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
    fun `match - Underride - null conditions - match`() = runTest {
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
    fun `match - Underride - empty conditions - match`() = runTest {
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
    fun `match - Underride - all false conditions - no match`() = runTest {
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
    fun `match - Underride - one false condition - no match`() = runTest {
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
    fun `match - Underride - all true condition - match`() = runTest {
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
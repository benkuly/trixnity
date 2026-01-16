package de.connect2x.trixnity.client.notification

import io.kotest.matchers.shouldBe
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.push.PushAction
import de.connect2x.trixnity.core.model.push.PushCondition
import de.connect2x.trixnity.core.model.push.PushRule
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

class GetRoomsWithDisabledPushRulesTest : TrixnityBaseTest() {
    @Test
    fun `push rule not Override - ignore`() {
        getRoomsWithDisabledPushRules(
            listOf(
                PushRule.Underride(
                    ruleId = "rule",
                    default = false,
                    enabled = true,
                    actions = setOf(),
                    conditions = setOf(
                        PushCondition.EventMatch("room_id", "!room")
                    )
                )
            )
        ) shouldBe emptySet()
    }

    @Test
    fun `actions not empty - ignore`() {
        getRoomsWithDisabledPushRules(
            listOf(
                PushRule.Override(
                    ruleId = "rule",
                    default = false,
                    enabled = true,
                    actions = setOf(PushAction.Notify),
                    conditions = setOf(
                        PushCondition.EventMatch("room_id", "!room1")
                    )
                )
            )
        ) shouldBe emptySet()
    }

    @Test
    fun `conditions more than one - ignore`() {
        getRoomsWithDisabledPushRules(
            listOf(
                PushRule.Override(
                    ruleId = "rule",
                    default = false,
                    enabled = true,
                    actions = setOf(),
                    conditions = setOf(
                        PushCondition.EventMatch("room_id", "!room1"),
                        PushCondition.RoomMemberCount("1")
                    )
                )
            )
        ) shouldBe emptySet()
    }

    @Test
    fun `conditions less than one - ignore`() {
        getRoomsWithDisabledPushRules(
            listOf(
                PushRule.Override(
                    ruleId = "rule",
                    default = false,
                    enabled = true,
                    actions = setOf(),
                    conditions = setOf()
                )
            )
        ) shouldBe emptySet()
    }

    @Test
    fun `condition not EventMatch - ignore`() {
        getRoomsWithDisabledPushRules(
            listOf(
                PushRule.Override(
                    ruleId = "rule",
                    default = false,
                    enabled = true,
                    actions = setOf(),
                    conditions = setOf(
                        PushCondition.RoomMemberCount("1")
                    )
                )
            )
        ) shouldBe emptySet()
    }

    @Test
    fun `condition key not room_id - ignore`() {
        getRoomsWithDisabledPushRules(
            listOf(
                PushRule.Override(
                    ruleId = "rule",
                    default = false,
                    enabled = true,
                    actions = setOf(),
                    conditions = setOf(
                        PushCondition.EventMatch("dino", "!room1"),
                    )
                )
            )
        ) shouldBe emptySet()
    }

    @Test
    fun `use pattern as RoomId`() {
        getRoomsWithDisabledPushRules(
            listOf(
                PushRule.Override(
                    ruleId = "rule",
                    default = false,
                    enabled = true,
                    actions = setOf(),
                    conditions = setOf(
                        PushCondition.EventMatch("room_id", "!room1")
                    )
                ),
                PushRule.Override(
                    ruleId = "rule",
                    default = false,
                    enabled = true,
                    actions = setOf(),
                    conditions = setOf(
                        PushCondition.EventMatch("room_id", "!room2")
                    )
                ),
                PushRule.Override(
                    ruleId = "rule",
                    default = false,
                    enabled = true,
                    actions = setOf(PushAction.Notify),
                    conditions = setOf(
                        PushCondition.EventMatch("room_id", "!room3")
                    )
                )
            )
        ) shouldBe setOf(RoomId("!room1"), RoomId("!room2"))
    }
}
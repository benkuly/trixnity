package net.folivo.trixnity.client.notification

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.ServerDefaultPushRules
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

class IsPushRulesDisabledTest : TrixnityBaseTest() {
    @Test
    fun `is not Override - false`() {
        isPushRulesDisabled(
            listOf(
                PushRule.Underride(
                    ruleId = ServerDefaultPushRules.Master.id,
                    default = true,
                    enabled = true,
                    actions = setOf(),
                    conditions = null,
                ),
                PushRule.Content(
                    ruleId = ServerDefaultPushRules.Master.id,
                    default = true,
                    enabled = true,
                    actions = setOf(),
                    pattern = "dino",
                ),
            )
        ) shouldBe false
    }

    @Test
    fun `id is not master - false`() {
        isPushRulesDisabled(
            listOf(
                ServerDefaultPushRules.Master.rule.copy(ruleId = "other", enabled = true),
            )
        ) shouldBe false
    }

    @Test
    fun `is not enabled - false`() {
        isPushRulesDisabled(
            listOf(
                ServerDefaultPushRules.Master.rule.copy(enabled = false),
            )
        ) shouldBe false
    }

    @Test
    fun `actions not empty - false`() {
        isPushRulesDisabled(
            listOf(
                ServerDefaultPushRules.Master.rule.copy(enabled = true, actions = setOf(PushAction.Notify)),
            )
        ) shouldBe false
    }

    @Test
    fun `conditions not empty - false`() {
        isPushRulesDisabled(
            listOf(
                ServerDefaultPushRules.Master.rule.copy(
                    enabled = true,
                    conditions = setOf(PushCondition.RoomMemberCount("1"))
                ),
            )
        ) shouldBe false
    }

    @Test
    fun `true`() {
        isPushRulesDisabled(
            listOf(
                ServerDefaultPushRules.Master.rule.copy(enabled = true),
                ServerDefaultPushRules.RoomOneToOne.rule
            )
        ) shouldBe true
    }

    @Test
    fun `true when empty`() {
        isPushRulesDisabled(
            listOf()
        ) shouldBe true
    }
}
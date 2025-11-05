package net.folivo.trixnity.client.notification

import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.ServerDefaultPushRules

internal fun isPushRulesDisabled(pushRules: List<PushRule>): Boolean =
    pushRules.isEmpty() || pushRules.any {
        it is PushRule.Override
                && it.ruleId == ServerDefaultPushRules.Master.id
                && it.enabled
                && it.actions.isEmpty()
                && it.conditions.isNullOrEmpty()
    }
package de.connect2x.trixnity.client.notification

import de.connect2x.trixnity.core.model.push.PushRule
import de.connect2x.trixnity.core.model.push.ServerDefaultPushRules

internal fun isPushRulesDisabled(pushRules: List<PushRule>): Boolean =
    pushRules.isEmpty() || pushRules.any {
        it is PushRule.Override
                && it.ruleId == ServerDefaultPushRules.Master.id
                && it.enabled
                && it.actions.isEmpty()
                && it.conditions.isNullOrEmpty()
    }
package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.model.push.PushRule

private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.PushRuleMatcher")

interface PushRuleMatcher {
    /**
     * Calculate if a [PushRule] matches for the given event.
     */
    suspend fun match(rule: PushRule, event: ClientEvent<*>, eventJson: Lazy<JsonObject?>): Boolean
}

class PushRuleMatcherImpl(
    private val conditionMatcher: PushRuleConditionMatcher,
) : PushRuleMatcher {
    companion object {
        suspend fun match(
            rule: PushRule.Override,
            event: ClientEvent<*>,
            eventJson: Lazy<JsonObject?>,
            conditionMatcher: PushRuleConditionMatcher,
        ): Boolean = rule.enabled && rule.conditions.orEmpty().all { conditionMatcher.match(it, event, eventJson) }

        fun match(
            rule: PushRule.Content,
            event: ClientEvent<*>,
        ): Boolean {
            val content = event.content
            return rule.enabled && if (content is RoomMessageEventContent) {
                hasGlobMatch(content.body, rule.pattern)
            } else false
        }

        fun match(
            rule: PushRule.Room,
            event: ClientEvent<*>,
        ): Boolean = rule.enabled && rule.roomId == event.roomIdOrNull

        fun match(
            rule: PushRule.Sender,
            event: ClientEvent<*>,
        ): Boolean = rule.enabled && rule.userId == event.senderOrNull

        suspend fun match(
            rule: PushRule.Underride,
            event: ClientEvent<*>,
            eventJson: Lazy<JsonObject?>,
            conditionMatcher: PushRuleConditionMatcher,
        ): Boolean = rule.enabled && rule.conditions.orEmpty().all { conditionMatcher.match(it, event, eventJson) }
    }


    override suspend fun match(
        rule: PushRule,
        event: ClientEvent<*>,
        eventJson: Lazy<JsonObject?>
    ): Boolean = when (rule) {
        is PushRule.Override -> Companion.match(rule, event, eventJson, conditionMatcher)
        is PushRule.Content -> Companion.match(rule, event)
        is PushRule.Room -> Companion.match(rule, event)
        is PushRule.Sender -> Companion.match(rule, event)
        is PushRule.Underride -> Companion.match(rule, event, eventJson, conditionMatcher)
    }
}

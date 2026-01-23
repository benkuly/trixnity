package de.connect2x.trixnity.client.notification

import de.connect2x.lognity.api.logger.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.idOrNull
import de.connect2x.trixnity.core.model.push.PushAction
import de.connect2x.trixnity.core.model.push.PushRule

private val log = Logger("de.connect2x.trixnity.client.notification.EvaluatePushRules")

fun interface EvaluatePushRules {
    /**
     * Calculate [PushAction]s for the given [ClientEvent] and [PushRule]s.
     */
    suspend operator fun invoke(
        event: ClientEvent<*>,
        allRules: List<PushRule>,
    ): Set<PushAction>?
}

class EvaluatePushRulesImpl(
    private val matcher: PushRuleMatcher,
    private val json: Json,
) : EvaluatePushRules {
    @OptIn(ExperimentalSerializationApi::class)
    override suspend operator fun invoke(
        event: ClientEvent<*>,
        allRules: List<PushRule>
    ): Set<PushAction>? {
        log.trace { "evaluate push rules for event: ${event.idOrNull}" }
        val eventJson = lazy { notificationEventToJson(event, json) }
        val rule = allRules.find { matcher.match(it, event, eventJson) }
        if (rule != null) {
            log.trace { "found matching rule ${rule.ruleId} for event ${event.idOrNull} with actions: ${rule.actions}" }
        } else {
            log.trace { "no rule found for event ${event.idOrNull}" }
        }
        if (rule?.actions?.contains(PushAction.Notify) != true) return null
        log.debug { "notify for event ${event.idOrNull} (content=${event.content::class.simpleName}, rule=$rule)" }
        return rule.actions
    }
}

package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.idOrNull
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushRule

private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.evaluatePushRules")

fun interface EvaluatePushRules {
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
        log.trace { "found matching rule ${rule?.ruleId} for event ${event.idOrNull} with actions: ${rule?.actions}" }
        if (rule?.actions?.contains(PushAction.Notify) != true) return null
        log.debug { "notify for event ${event.idOrNull} (content=${event.content::class}, rule=$rule)" }
        return rule.actions
    }
}

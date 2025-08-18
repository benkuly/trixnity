package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.idOrNull
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.evaluatePushRules")

sealed interface EvaluatePushRulesResult {
    val roomId: RoomId
    val eventId: EventId?
    val actions: Set<PushAction>

    data class Message(
        override val roomId: RoomId,
        override val eventId: EventId,
        override val actions: Set<PushAction>,
    ) : EvaluatePushRulesResult

    data class State(
        override val roomId: RoomId,
        override val eventId: EventId?,
        val type: String,
        val stateKey: String,
        override val actions: Set<PushAction>,
    ) : EvaluatePushRulesResult
}

fun interface EvaluatePushRules {
    suspend operator fun invoke(
        event: ClientEvent<*>,
        allRules: List<PushRule>,
    ): EvaluatePushRulesResult?
}

class EvaluatePushRulesImpl(
    private val matcher: PushRuleMatcher,
    private val json: Json,
    private val eventContentSerializerMappings: EventContentSerializerMappings,
) : EvaluatePushRules {
    @OptIn(ExperimentalSerializationApi::class)
    override suspend operator fun invoke(
        event: ClientEvent<*>,
        allRules: List<PushRule>
    ): EvaluatePushRulesResult? {
        log.trace { "evaluate push rules for event: ${event.idOrNull}" }
        val eventJson = lazy { notificationEventToJson(event, json) }
        val rule = allRules.find { matcher.match(it, event, eventJson) }
        log.trace { "event ${event.idOrNull}, found matching rule: ${rule?.ruleId}, actions: ${rule?.actions}" }
        if (rule?.actions?.contains(PushAction.Notify) != true) return null
        log.debug { "notify for event ${event.idOrNull} (type: ${event::class}, content type: ${event.content::class}) (PushRule is $rule)" }
        return when (event) {
            is RoomEvent.MessageEvent<*> -> EvaluatePushRulesResult.Message(
                roomId = event.roomId,
                eventId = event.id,
                actions = rule.actions,
            )

            is ClientEvent.StateBaseEvent<*> -> {
                val roomId = event.roomId ?: return null
                val type =
                    eventContentSerializerMappings.state.find { it.kClass.isInstance(event.content) }?.type
                        ?: return null
                EvaluatePushRulesResult.State(
                    roomId = roomId,
                    eventId = event.id,
                    type = type,
                    stateKey = event.stateKey,
                    actions = rule.actions,
                )
            }

            else -> null
        }
    }
}

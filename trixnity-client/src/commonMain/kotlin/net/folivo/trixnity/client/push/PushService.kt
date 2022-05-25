package net.folivo.trixnity.client.push

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import mu.KotlinLogging
import net.folivo.trixnity.client.getEventId
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getSender
import net.folivo.trixnity.client.push.IPushService.Notification
import net.folivo.trixnity.client.room.IRoomService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.get
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.clientserverapi.client.AfterSyncResponseSubscriber
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


private val log = KotlinLogging.logger { }

interface IPushService {
    data class Notification(
        val event: Event<*>
    )

    suspend fun getNotifications(
        decryptionTimeout: Duration = 5.seconds,
        syncResponseBufferSize: Int = 0
    ): Flow<Notification>
}

class PushService(
    private val api: MatrixClientServerApiClient,
    private val room: IRoomService,
    private val store: Store,
    private val json: Json,
) : IPushService {

    private val roomSizePattern = Regex("\\s*(==|<|>|<=|>=)\\s*([0-9]+)")

    @OptIn(FlowPreview::class)
    override suspend fun getNotifications(
        decryptionTimeout: Duration,
        syncResponseBufferSize: Int,
    ): Flow<Notification> = channelFlow {
        val syncResponseFlow = callbackFlow {
            val subscriber: AfterSyncResponseSubscriber = { send(it) }
            api.sync.subscribeAfterSyncResponse(subscriber)
            awaitClose { api.sync.unsubscribeAfterSyncResponse(subscriber) }
        }

        val pushRules =
            store.globalAccountData.get<PushRulesEventContent>(scope = this).map { event ->
                event?.content?.global?.let { globalRuleSet ->
                    log.trace { "global rule set: $globalRuleSet" }
                    setOf(OVERRIDE, CONTENT, ROOM, SENDER, UNDERRIDE)
                        .mapNotNull { globalRuleSet[it] }
                        .fold(listOf<PushRule>()) { old, new -> old + new }
                } ?: listOf()
            }.stateIn(this)
        val inviteEvents = syncResponseFlow
            .map { syncResponse ->
                syncResponse.room?.invite?.values?.flatMap { inviteRoom ->
                    inviteRoom.inviteState?.events.orEmpty()
                }?.asFlow()
            }.filterNotNull()
            .flattenConcat()

        val timelineEvents =
            room.getTimelineEventsFromNowOn(decryptionTimeout, syncResponseBufferSize)
                .map { extractDecryptedEvent(it) }
                .filterNotNull()
        merge(inviteEvents, timelineEvents)
            .map {
                evaluatePushRules(
                    event = it,
                    allRules = pushRules.value
                )
            }.filterNotNull()
            .collect { send(it) }
    }

    private fun extractDecryptedEvent(timelineEvent: TimelineEvent): Event<*>? {
        val originalEvent = timelineEvent.event
        val content = timelineEvent.content?.getOrNull()
        return when {
            timelineEvent.isEncrypted.not() -> originalEvent
            content == null -> null
            originalEvent is Event.MessageEvent && content is MessageEventContent ->
                Event.MessageEvent(
                    content = content,
                    id = originalEvent.id,
                    sender = originalEvent.sender,
                    roomId = originalEvent.roomId,
                    originTimestamp = originalEvent.originTimestamp,
                    unsigned = originalEvent.unsigned
                )
            originalEvent is Event.StateEvent && content is StateEventContent -> originalEvent
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun evaluatePushRules(
        event: Event<*>,
        allRules: List<PushRule>,
    ): Notification? {
        val eventJson = lazy {
            try {
                json.serializersModule.getContextual(Event::class)?.let {
                    json.encodeToJsonElement(it, event) // TODO could be optimized
                }?.jsonObject
            } catch (exception: Exception) {
                log.warn { "could not serialize event" }
                null
            }
        }
        val rule = allRules.find { pushRule ->
            pushRule.enabled
                    && pushRule.conditions.orEmpty().all { matchPushCondition(event, eventJson, it) }
                    && if (pushRule.pattern != null) bodyContainsPattern(event, pushRule.pattern!!) else true
        }
        return rule?.actions?.asFlow()
            ?.transform { pushAction ->
                if (pushAction is PushAction.Notify) {
                    log.debug { "notify for event ${event.getEventId()} (type: ${event::class}, content type: ${event.content::class}) (PushRule is $rule)" }
                    emit(Notification(event))
                }
            }?.firstOrNull()
    }

    private suspend fun matchPushCondition(
        event: Event<*>,
        eventJson: Lazy<JsonObject?>,
        pushCondition: PushCondition
    ): Boolean {
        return when (pushCondition) {
            is PushCondition.ContainsDisplayName -> {
                val content = event.content
                if (content is RoomMessageEventContent) {
                    event.getRoomId()?.let { roomId ->
                        store.account.userId.value?.let { userId ->
                            store.roomUser.get(userId, roomId)?.name?.let { username ->
                                content.body.contains(username)
                            } ?: false
                        } ?: false
                    } ?: false
                } else false
            }
            is PushCondition.RoomMemberCount -> {
                event.getRoomId()?.let { roomId ->
                    pushCondition.isCount.checkIsCount(
                        store.room.get(roomId).value?.name?.summary?.joinedMemberCount ?: 0
                    )
                } ?: false
            }
            is PushCondition.SenderNotificationPermission -> {
                event.getRoomId()?.let { roomId ->
                    // at the moment, key can only be "room"
                    val powerLevels =
                        store.roomState.getByStateKey<PowerLevelsEventContent>(roomId, "")?.content
                    val requiredNotificationPowerLevel = powerLevels?.notifications?.room ?: 100
                    val senderPowerLevel = powerLevels?.users?.get(event.getSender()) ?: 0
                    senderPowerLevel >= requiredNotificationPowerLevel
                } ?: false
            }
            is PushCondition.EventMatch -> {
                val eventValue = getEventValue(event, eventJson, pushCondition)
                if (eventValue == null) {
                    log.debug { "cannot get the event's value for key '${pushCondition.key}' or value is 'null'" }
                    false
                } else {
                    pushCondition.pattern.globToRegExp().containsMatchIn(eventValue)
                }
            }
            is PushCondition.Unknown -> false
        }
    }

    private fun bodyContainsPattern(event: Event<*>, pattern: String): Boolean {
        val content = event.content
        return if (content is TextMessageEventContent) {
            pattern.globToRegExp().containsMatchIn(content.body)
        } else false
    }

    private fun getEventValue(
        event: Event<*>,
        initialEventJson: Lazy<JsonObject?>,
        pushCondition: PushCondition.EventMatch
    ): String? {
        return try {
            var eventJson: JsonElement? = initialEventJson.value
            pushCondition.key.split('.').forEach { segment ->
                eventJson = eventJson?.jsonObject?.get(segment)
            }
            eventJson?.jsonPrimitive?.contentOrNull
        } catch (exc: Exception) {
            log.warn(exc) { "could not evaluate event match push condition $pushCondition of event $event" }
            null
        }
    }

    private fun String.checkIsCount(size: Long): Boolean {
        this.toLongOrNull()?.let { count ->
            return size == count
        }
        val result = roomSizePattern.find(this)
        val bound = result?.groupValues?.getOrNull(2)?.toLongOrNull() ?: 0
        val operator = result?.groupValues?.getOrNull(1)
        log.debug { "room size ($size) $operator bound ($bound)" }
        return when (operator) {
            "==" -> size == bound
            "<" -> size < bound
            ">" -> size > bound
            "<=" -> size <= bound
            ">=" -> size >= bound
            else -> false
        }
    }

    private fun String.globToRegExp(): Regex {
        return buildString {
            this@globToRegExp.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.' -> append("\\.")
                    '\\' -> append("\\\\")
                    else -> append(char)
                }
            }
        }.toRegex()
    }

}
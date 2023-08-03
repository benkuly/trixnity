package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.getEventId
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getSender
import net.folivo.trixnity.client.notification.NotificationService.Notification
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncResponseSubscriber
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.UserInfo
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


private val log = KotlinLogging.logger { }

interface NotificationService {
    data class Notification(
        val event: Event<*>
    )

    fun getNotifications(
        decryptionTimeout: Duration = 5.seconds,
        syncResponseBufferSize: Int = 4
    ): Flow<Notification>
}

class NotificationServiceImpl(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val room: RoomService,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val roomUserStore: RoomUserStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val json: Json,
    private val currentSyncState: CurrentSyncState,
) : NotificationService {

    private val roomSizePattern = Regex("\\s*(==|<|>|<=|>=)\\s*([0-9]+)")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getNotifications(
        decryptionTimeout: Duration,
        syncResponseBufferSize: Int,
    ): Flow<Notification> = channelFlow {
        currentSyncState.first { it == SyncState.STARTED || it == SyncState.RUNNING }
        val syncResponseFlow = callbackFlow {
            val subscriber: SyncResponseSubscriber = { send(it) }
            api.sync.subscribeLastInSyncProcessing(subscriber)
            awaitClose { api.sync.unsubscribeLastInSyncProcessing(subscriber) }
        }

        val pushRules =
            globalAccountDataStore.get<PushRulesEventContent>().map { event ->
                event?.content?.global?.let { globalRuleSet ->
                    log.trace { "global rule set: $globalRuleSet" }
                    globalRuleSet.override.orEmpty() +
                            globalRuleSet.content.orEmpty() +
                            globalRuleSet.room.orEmpty() +
                            globalRuleSet.sender.orEmpty() +
                            globalRuleSet.content.orEmpty()
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
                .filter {
                    when (it) {
                        is Event.RoomEvent -> it.sender != userInfo.userId
                        else -> true
                    }
                }
        merge(inviteEvents, timelineEvents)
            .map {
                evaluatePushRules(
                    event = it,
                    allRules = pushRules.value
                )
            }.filterNotNull()
            .collect { send(it) }
    }.buffer(0)

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
        log.trace { "evaluate push rules for event: ${event.getEventId()}" }
        val eventJson = lazy {
            try {
                json.serializersModule.getContextual(Event::class)?.let {
                    json.encodeToJsonElement(it, event)
                }?.jsonObject
            } catch (exception: Exception) {
                log.warn { "could not serialize event" }
                null
            }
        }
        val rule = allRules
            .filter { it.enabled }
            .find { pushRule ->
                when (pushRule) {
                    is PushRule.Override -> pushRule.conditions.orEmpty()
                        .all { matchPushCondition(event, eventJson, it) }

                    is PushRule.Content -> bodyContainsPattern(event, pushRule.pattern)
                    is PushRule.Room -> pushRule.roomId == event.getRoomId()
                    is PushRule.Sender -> pushRule.userId == event.getSender()
                    is PushRule.Underride -> pushRule.conditions.orEmpty()
                        .all { matchPushCondition(event, eventJson, it) }
                }
            }
        log.trace { "event ${event.getEventId()}, found matching rule: ${rule?.ruleId}, actions: ${rule?.actions}" }
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
                        roomUserStore.get(userInfo.userId, roomId).first()?.name?.let { username ->
                            content.body.contains(username)
                        } ?: false
                    } ?: false
                } else false
            }

            is PushCondition.RoomMemberCount -> {
                event.getRoomId()?.let { roomId ->
                    pushCondition.isCount.checkIsCount(
                        roomStore.get(roomId).first()?.name?.summary?.joinedMemberCount ?: 0
                    )
                } ?: false
            }

            is PushCondition.SenderNotificationPermission -> {
                event.getRoomId()?.let { roomId ->
                    // at the moment, key can only be "room"
                    val powerLevels =
                        roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId, "").first()?.content
                    val requiredNotificationPowerLevel = powerLevels?.notifications?.room ?: 100
                    val senderPowerLevel = powerLevels?.users?.get(event.getSender()) ?: powerLevels?.usersDefault ?: 0
                    senderPowerLevel >= requiredNotificationPowerLevel
                } ?: false
            }

            is PushCondition.EventMatch -> {
                val propertyValue =
                    getEventProperty(event, eventJson, pushCondition.key)?.jsonPrimitiveOrNull?.contentOrNull
                if (propertyValue == null) {
                    log.debug { "cannot get the event's value for key '${pushCondition.key}' or value is 'null'" }
                    false
                } else {
                    pushCondition.pattern.matrixGlobToRegExp().containsMatchIn(propertyValue)
                }
            }

            is PushCondition.EventPropertyIs -> {
                val propertyValue = getEventProperty(event, eventJson, pushCondition.key)?.jsonPrimitiveOrNull
                if (propertyValue == null) {
                    log.debug { "cannot get the event's value for key '${pushCondition.key}' or value is 'null'" }
                    false
                } else {
                    propertyValue == pushCondition.value
                }
            }

            is PushCondition.EventPropertyContains -> {
                val propertyValue = getEventProperty(event, eventJson, pushCondition.key)?.jsonArrayOrNull
                if (propertyValue == null) {
                    log.debug { "cannot get the event's value for key '${pushCondition.key}' or value is 'null'" }
                    false
                } else {
                    propertyValue.contains(pushCondition.value)
                }
            }

            is PushCondition.Unknown -> false
        }
    }

    private fun bodyContainsPattern(event: Event<*>, pattern: String): Boolean {
        val content = event.content
        return if (content is TextMessageEventContent) {
            pattern.matrixGlobToRegExp().containsMatchIn(content.body)
        } else false
    }

    private fun getEventProperty(
        event: Event<*>,
        initialEventJson: Lazy<JsonObject?>,
        key: String
    ): JsonElement? {
        return try {
            var eventJson: JsonElement? = initialEventJson.value
            key.split('.').forEach { segment ->
                eventJson = eventJson?.jsonObject?.get(segment)
            }
            eventJson
        } catch (exc: Exception) {
            log.warn(exc) { "could not find event property for key $key in event $event" }
            null
        }
    }

    private val JsonElement?.jsonPrimitiveOrNull: JsonPrimitive?
        get() = try {
            this?.jsonPrimitive
        } catch (_: Exception) {
            null
        }

    private val JsonElement?.jsonArrayOrNull: JsonArray?
        get() = try {
            this?.jsonArray
        } catch (_: Exception) {
            null
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

    private fun String.matrixGlobToRegExp(): Regex {
        return buildString {
            this@matrixGlobToRegExp.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.' -> append("""\.""")
                    '\\' -> append("""\\""")
                    else -> append(char)
                }
            }
        }.toRegex()
    }

}
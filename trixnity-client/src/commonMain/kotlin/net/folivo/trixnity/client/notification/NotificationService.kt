package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.notification.NotificationService.Notification
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.ClientEventEmitter
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.subscribeAsFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


private val log = KotlinLogging.logger { }

interface NotificationService {
    data class Notification(
        val event: ClientEvent<*>
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
        val syncResponseFlow =
            api.sync.subscribeAsFlow(ClientEventEmitter.Priority.AFTER_DEFAULT).map { it.syncResponse }

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
        val inviteEventsFlow = syncResponseFlow
            .map { syncResponse ->
                syncResponse.room?.invite?.values?.flatMap { inviteRoom ->
                    inviteRoom.inviteState?.events.orEmpty()
                }?.asFlow()
            }.filterNotNull()
            .flattenConcat()

        val timelineEventsFlow =
            room.getTimelineEventsFromNowOn(decryptionTimeout, syncResponseBufferSize)
                .map { extractDecryptedEvent(it) }
                .filterNotNull()
                .filter {
                    it.sender != userInfo.userId
                }
        merge(inviteEventsFlow, timelineEventsFlow)
            .map {
                evaluatePushRules(
                    event = it,
                    allRules = pushRules.value
                )
            }.filterNotNull()
            .collect { send(it) }
    }.buffer(0)

    private fun extractDecryptedEvent(timelineEvent: TimelineEvent): RoomEvent<*>? {
        val originalEvent = timelineEvent.event
        val content = timelineEvent.content?.getOrNull()
        return when {
            timelineEvent.isEncrypted.not() -> originalEvent
            content == null -> null
            originalEvent is RoomEvent.MessageEvent<*> && content is MessageEventContent ->
                RoomEvent.MessageEvent(
                    content = content,
                    id = originalEvent.id,
                    sender = originalEvent.sender,
                    roomId = originalEvent.roomId,
                    originTimestamp = originalEvent.originTimestamp,
                    unsigned = originalEvent.unsigned
                )

            originalEvent is RoomEvent.StateEvent<*> && content is StateEventContent -> originalEvent
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun evaluatePushRules(
        event: ClientEvent<*>,
        allRules: List<PushRule>,
    ): Notification? {
        log.trace { "evaluate push rules for event: ${event.idOrNull}" }
        val eventJson = lazy {
            try {
                when (event) {
                    is RoomEvent -> json.serializersModule.getContextual(RoomEvent::class)?.let {
                        json.encodeToJsonElement(it, event)
                    }?.jsonObject

                    is StrippedStateEvent -> json.serializersModule.getContextual(StrippedStateEvent::class)?.let {
                        json.encodeToJsonElement(it, event)
                    }?.jsonObject

                    else -> throw IllegalStateException("event did have unexpected type ${event::class}")
                }
            } catch (exception: Exception) {
                log.warn(exception) { "could not serialize event" }
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
                    is PushRule.Room -> pushRule.roomId == event.roomIdOrNull
                    is PushRule.Sender -> pushRule.userId == event.senderOrNull
                    is PushRule.Underride -> pushRule.conditions.orEmpty()
                        .all { matchPushCondition(event, eventJson, it) }
                }
            }
        log.trace { "event ${event.idOrNull}, found matching rule: ${rule?.ruleId}, actions: ${rule?.actions}" }
        return rule?.actions?.asFlow()
            ?.transform { pushAction ->
                if (pushAction is PushAction.Notify) {
                    log.debug { "notify for event ${event.idOrNull} (type: ${event::class}, content type: ${event.content::class}) (PushRule is $rule)" }
                    emit(Notification(event))
                }
            }?.firstOrNull()
    }

    private suspend fun matchPushCondition(
        event: ClientEvent<*>,
        eventJson: Lazy<JsonObject?>,
        pushCondition: PushCondition
    ): Boolean {
        return when (pushCondition) {
            is PushCondition.ContainsDisplayName -> {
                val content = event.content
                if (content is RoomMessageEventContent) {
                    event.roomIdOrNull?.let { roomId ->
                        roomUserStore.get(userInfo.userId, roomId).first()?.name?.let { username ->
                            content.body.contains(username)
                        } ?: false
                    } ?: false
                } else false
            }

            is PushCondition.RoomMemberCount -> {
                event.roomIdOrNull?.let { roomId ->
                    pushCondition.isCount.checkIsCount(
                        roomStore.get(roomId).first()?.name?.summary?.joinedMemberCount ?: 0
                    )
                } ?: false
            }

            is PushCondition.SenderNotificationPermission -> {
                event.roomIdOrNull?.let { roomId ->
                    // at the moment, key can only be "room"
                    val powerLevels =
                        roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId, "").first()?.content
                    val requiredNotificationPowerLevel = powerLevels?.notifications?.room ?: 100
                    val senderPowerLevel = powerLevels?.users?.get(event.senderOrNull) ?: powerLevels?.usersDefault ?: 0
                    senderPowerLevel >= requiredNotificationPowerLevel
                } ?: false
            }

            is PushCondition.EventMatch -> {
                val propertyValue =
                    (getEventProperty(eventJson, pushCondition.key) as? JsonPrimitive)?.contentOrNull
                if (propertyValue == null) {
                    log.debug { "cannot get the event's value for key '${pushCondition.key}' or value is 'null'" }
                    false
                } else {
                    pushCondition.pattern.matrixGlobToRegExp().containsMatchIn(propertyValue)
                }
            }

            is PushCondition.EventPropertyIs -> {
                val propertyValue = getEventProperty(eventJson, pushCondition.key) as? JsonPrimitive
                if (propertyValue == null) {
                    log.debug { "cannot get the event's value for key '${pushCondition.key}' or value is 'null'" }
                    false
                } else {
                    propertyValue == pushCondition.value
                }
            }

            is PushCondition.EventPropertyContains -> {
                val propertyValue = getEventProperty(eventJson, pushCondition.key) as? JsonArray
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

    private fun bodyContainsPattern(event: ClientEvent<*>, pattern: String): Boolean {
        val content = event.content
        return if (content is RoomMessageEventContent.TextBased.Text) {
            pattern.matrixGlobToRegExp().containsMatchIn(content.body)
        } else false
    }

    private val dotRegex = "(?<!\\\\)(?:\\\\\\\\)*[.]".toRegex()
    private val removeEscapes = "\\\\(.)".toRegex()
    internal fun getEventProperty(
        initialEventJson: Lazy<JsonObject?>,
        key: String
    ): JsonElement? {
        return try {
            var targetProperty: JsonElement? = initialEventJson.value
            key.split(dotRegex)
                .map { it.replace(removeEscapes, "$1") }
                .forEach { segment ->
                    targetProperty = (targetProperty as? JsonObject)?.get(segment)
                }
            targetProperty
        } catch (exc: Exception) {
            log.warn(exc) { "could not find event property for key $key in event ${initialEventJson.value}" }
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
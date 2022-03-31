package net.folivo.trixnity.client.push

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import mu.KotlinLogging
import net.folivo.trixnity.client.getEventId
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getSender
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.clientserverapi.client.AfterSyncResponseSubscriber
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.SyncResponse
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import kotlin.time.Duration.Companion.seconds


private val log = KotlinLogging.logger { }

class PushService(
    private val api: MatrixClientServerApiClient,
    private val room: RoomService,
    private val store: Store,
    private val json: Json,
) {

    data class Notification(
        val event: Event<*>,
        val content: EventContent, // possibly decrypted
    )

    private val roomSizePattern = Regex("\\s*(==|<|>|<=|>=)\\s*([0-9]+)")

    private val _notifications = MutableSharedFlow<Notification>(0)

    /**
     * In order to get notifications, manually call [enableNotifications].
     */
    val notifications = _notifications.asSharedFlow()

    private val allRules = MutableStateFlow(listOf<PushRule>())
    private val subscriber: AfterSyncResponseSubscriber = { // _after_ sync since we need access to timeline events
        evaluatePushRules(it, allRules)
    }

    internal suspend fun start(scope: CoroutineScope) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.globalAccountData.get(PushRulesEventContent::class, "", scope).map { event ->
                event?.content?.global?.let { globalRuleSet ->
                    log.trace { "global rule set: $globalRuleSet" }
                    globalRuleSet.override.orEmpty() +
                            globalRuleSet.content.orEmpty() +
                            globalRuleSet.room.orEmpty() +
                            globalRuleSet.sender.orEmpty() +
                            globalRuleSet.underride.orEmpty()
                } ?: listOf()
            }
                .collect {
                    allRules.value = it
                } // do _not_ provide a default here as we need to suspend here; otherwise we could miss messages
        }
    }

    fun enableNotifications() {
        api.sync.subscribeAfterSyncResponse(subscriber)
    }

    fun disableNotifications() {
        api.sync.unsubscribeAfterSyncResponse(subscriber)
    }

    private suspend fun evaluatePushRules(
        syncResponse: SyncResponse,
        allRules: StateFlow<List<PushRule>>,
    ) = coroutineScope {
        findInterestingEvents(syncResponse).map { event ->
            async {
                try {
                    withTimeout(2.seconds) {
                        findMatchingPushRule(event, allRules.value)
                    }
                } catch (exc: CancellationException) {
                    log.warn { "could not decrypt event ${event.getEventId()}" }
                    null
                }
            }
        }.awaitAll().filterNotNull().forEach { _notifications.emit(it) }
    }

    private fun findInterestingEvents(syncResponse: SyncResponse): List<Event<*>> {
        val roomEvents = syncResponse.room?.join?.values?.flatMap { joinedRoom ->
            joinedRoom.timeline?.events.orEmpty()
                .filter { roomEvent ->
                    roomEvent.sender != store.account.userId.value
                }
        }.orEmpty()
        val inviteEvents = syncResponse.room?.invite?.values?.flatMap { inviteRoom ->
            inviteRoom.inviteState?.events.orEmpty()
        }.orEmpty()
        return roomEvents + inviteEvents
    }

    private suspend fun findMatchingPushRule(
        event: Event<*>,
        allRules: List<PushRule>,
    ): Notification? {
        log.trace { "find matching push rule for event ${event.getEventId()}" }
        possiblyDecryptEvent(event)?.let { decryptedEvent ->
            val rule = allRules.find { pushRule ->
                pushRule.enabled && pushRule.conditions.all { pushCondition ->
                    matchPushCondition(decryptedEvent, pushCondition)
                } && if (pushRule.pattern != null) bodyContainsPattern(decryptedEvent, pushRule.pattern!!) else true
            }
            rule?.actions?.forEach { pushAction ->
                if (pushAction is PushAction.Notify) {
                    log.debug { "notify for event ${event.getEventId()} (type: ${event::class}, content type: ${event.content::class}) (PushRule is $rule)" }
                    return Notification(event, decryptedEvent.content)
                }
            }
        }
        return null
    }

    private suspend fun matchPushCondition(
        event: Event<*>,
        pushCondition: PushCondition
    ): Boolean {
        return when (pushCondition) {
            is PushCondition.ContainsDisplayName -> {
                val content = event.content
                if (content is RoomMessageEventContent) {
                    store.account.userId.value?.let { userId ->
                        content.body.contains(userId.sigilCharacter + userId.localpart)
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
                val eventValue = getEventValue(event, pushCondition)
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

    private suspend fun possiblyDecryptEvent(
        event: Event<*>
    ): Event<*>? = coroutineScope {
        if (event is Event.MessageEvent) { // TODO has to be changed to RoomEvent with merge of 2.0.0
            val theRoom = store.room.get(event.roomId).value
            val isNotEncrypted = theRoom?.encryptionAlgorithm == null
            val timelineEvent =
                room.getTimelineEvent(event.id, event.roomId, this)
                    .filterNotNull()
                    .first {
                        it.decryptedEvent?.isSuccess == true || isNotEncrypted
                    }
            log.trace { "decrypted timelineEvent: ${timelineEvent.eventId}" }
            if (timelineEvent.decryptedEvent == null) { // not encrypted
                timelineEvent.event
            } else { // encrypted
                timelineEvent.decryptedEvent.getOrNull()
            }
        } else {
            event
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun getEventValue(
        event: Event<*>,
        pushCondition: PushCondition.EventMatch
    ): String? {
        var eventJson: JsonElement? = json.serializersModule.getContextual(Event::class)?.let {
            json.encodeToJsonElement(it, event) // TODO could be optimized
        }
        try {
            pushCondition.key.split('.').forEach { segment ->
                eventJson = eventJson?.jsonObject?.get(segment)
            }
        } catch (exc: IllegalArgumentException) {
            eventJson = null
        }
        if (eventJson != null && eventJson is JsonPrimitive) {
            return (eventJson as JsonPrimitive).contentOrNull
        }
        return null
    }

    private fun String.checkIsCount(size: Int): Boolean {
        this.toIntOrNull()?.let { count ->
            return size == count
        }
        val result = roomSizePattern.find(this)
        val bound = result?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
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
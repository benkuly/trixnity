package net.folivo.trixnity.client.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import mu.KotlinLogging
import net.folivo.trixnity.client.getEventId
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getSender
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.SyncResponse
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule


private val log = KotlinLogging.logger { }

class PushService(
    private val api: MatrixClientServerApiClient,
    private val room: RoomService,
    private val store: Store,
    private val json: Json,
) {

    data class Notification(
        val roomId: RoomId,
        val userId: UserId,
        val content: EventContent,
    )

    private val roomSizePattern = Regex("\\s*(==|<|>|<=|>=)\\s*([0-9]+)")

    private val _notifications = MutableSharedFlow<Notification>(0)
    val notifications = _notifications.asSharedFlow()

    suspend fun start(scope: CoroutineScope) {
        val allRules =
            store.globalAccountData.get(PushRulesEventContent::class, "", scope).map { event ->
                event?.content?.global?.let { globalRuleSet ->
                    globalRuleSet.override.orEmpty() +
                            globalRuleSet.content.orEmpty() +
                            globalRuleSet.room.orEmpty() +
                            globalRuleSet.sender.orEmpty() +
                            globalRuleSet.underride.orEmpty()
                } ?: listOf()
            }
                .stateIn(scope) // do _not_ provide a default here as we need to suspend here; otherwise we could miss messages
        api.sync.subscribeSyncResponse { evaluatePushRules(it, allRules, scope) }
    }

    private fun evaluatePushRules(
        syncResponse: SyncResponse,
        allRules: StateFlow<List<PushRule>>,
        scope: CoroutineScope,
    ) {
        findInterestingEvents(syncResponse).forEach { event ->
            findMatchingPushRule(event, allRules.value, scope)
        }
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

    private fun findMatchingPushRule(
        event: Event<*>,
        allRules: List<PushRule>,
        scope: CoroutineScope,
    ) {
        scope.launch {
            possiblyDecryptEvent(event, scope)?.let {
                val (sender, decryptedEvent) = it
                val rule = allRules.find { pushRule ->
                    pushRule.enabled && pushRule.conditions.all { pushCondition ->
                        matchPushCondition(decryptedEvent, pushCondition)
                    }
                }
                rule?.actions?.forEach { pushAction ->
                    decryptedEvent.getRoomId()?.let { roomId ->
                        if (pushAction is PushAction.Notify) {
                            _notifications.emit(Notification(roomId, sender, decryptedEvent.content))
                        }
                    }
                }
            }
        }
    }

    private suspend fun matchPushCondition(
        event: Event<*>,
        pushCondition: PushCondition
    ): Boolean {
        log.debug { "checking push condition $pushCondition on event ${event.getEventId()}" }
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

    private suspend fun possiblyDecryptEvent(
        event: Event<*>,
        scope: CoroutineScope
    ): Pair<UserId, Event<*>>? {
        return if (event is Event.RoomEvent) {
            val theRoom = store.room.get(event.roomId).value
            val isNotEncrypted = theRoom?.encryptionAlgorithm == null
            val timelineEvent =
                room.getTimelineEvent(event.id, event.roomId, scope)
                    .filterNotNull()
                    .first {
                        it.decryptedEvent?.isSuccess == true || isNotEncrypted
                    }
            log.debug { "decrypted timelineEvent: ${timelineEvent.eventId}" }
            if (timelineEvent.decryptedEvent == null) { // not encrypted
                event.sender to timelineEvent.event
            } else { // encrypted
                timelineEvent.decryptedEvent.getOrNull()?.let { event.sender to it }
            }
        } else {
            event.getSender()?.let { it to event }
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
        }.toRegex(RegexOption.DOT_MATCHES_ALL)
    }

}
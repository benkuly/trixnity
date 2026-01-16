package de.connect2x.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import de.connect2x.trixnity.client.store.RoomStateStore
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.client.store.RoomUserStore
import de.connect2x.trixnity.client.store.getByStateKey
import de.connect2x.trixnity.client.user.CanDoAction
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.roomIdOrNull
import de.connect2x.trixnity.core.model.events.senderOrNull
import de.connect2x.trixnity.core.model.push.PushCondition

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.notification.PushRuleConditionMatcher")

interface PushRuleConditionMatcher {
    /**
     * Calculate if a [PushCondition] matches for the given event.
     */
    suspend fun match(condition: PushCondition, event: ClientEvent<*>, eventJson: Lazy<JsonObject?>): Boolean
}

class PushRuleConditionMatcherImpl(
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val roomUserStore: RoomUserStore,
    private val canDoAction: CanDoAction,
    private val userInfo: UserInfo,
) : PushRuleConditionMatcher {
    companion object {
        // TODO should use display name at position of the event
        @Suppress("DEPRECATION")
        suspend fun match(
            condition: PushCondition.ContainsDisplayName,
            event: ClientEvent<*>,
            userId: UserId,
            roomUserStore: RoomUserStore,
        ): Boolean {
            val content = event.content
            return if (content is RoomMessageEventContent) {
                event.roomIdOrNull?.let { roomId ->
                    roomUserStore.get(userId, roomId).first()?.name?.let { username ->
                        content.body.contains(username)
                    } ?: false
                } ?: false
            } else false
        }

        // TODO should use member count at position of the event
        suspend fun match(
            condition: PushCondition.RoomMemberCount,
            event: ClientEvent<*>,
            roomStore: RoomStore,
        ): Boolean {
            return event.roomIdOrNull?.let { roomId ->
                hasSizeMatch(
                    value = condition.isCount,
                    size = roomStore.get(roomId).first()?.name?.summary?.joinedMemberCount ?: 0
                )
            } ?: false
        }

        // TODO should use power level at position of the event
        suspend fun match(
            condition: PushCondition.SenderNotificationPermission,
            event: ClientEvent<*>,
            roomStateStore: RoomStateStore,
            canDoAction: CanDoAction,
        ): Boolean {
            val roomId = event.roomIdOrNull ?: return false
            val sender = event.senderOrNull ?: return false
            val createEvent = roomStateStore.getByStateKey<CreateEventContent>(roomId).first() ?: return false
            val powerLevelsEventContent =
                roomStateStore.getByStateKey<PowerLevelsEventContent>(roomId).first()?.content ?: return false
            return canDoAction.asUser(
                userId = sender,
                createEvent = createEvent,
                powerLevelsEventContent = powerLevelsEventContent,
            ) { powerLevel ->
                powerLevel >= (powerLevelsEventContent.notifications?.get(condition.key) ?: 50)
            }
        }

        fun match(
            condition: PushCondition.EventMatch,
            eventJson: Lazy<JsonObject?>
        ): Boolean {
            val propertyValue =
                (eventJson.value?.let { jsonPath(it, condition.key) } as? JsonPrimitive)?.contentOrNull
            return if (propertyValue == null) {
                log.trace { "cannot get the event's value for key '${condition.key}' or value is 'null'" }
                false
            } else {
                hasGlobMatch(propertyValue, condition.pattern)
            }
        }

        fun match(
            condition: PushCondition.EventPropertyIs,
            eventJson: Lazy<JsonObject?>
        ): Boolean {
            val propertyValue = eventJson.value?.let { jsonPath(it, condition.key) } as? JsonPrimitive
            return if (propertyValue == null) {
                log.trace { "cannot get the event's value for key '${condition.key}' or value is 'null'" }
                false
            } else {
                propertyValue == condition.value
            }
        }

        fun match(
            condition: PushCondition.EventPropertyContains,
            eventJson: Lazy<JsonObject?>
        ): Boolean {
            val propertyValue = eventJson.value?.let { jsonPath(it, condition.key) } as? JsonArray
            return if (propertyValue == null) {
                log.trace { "cannot get the event's value for key '${condition.key}' or value is 'null'" }
                false
            } else {
                propertyValue.contains(condition.value)
            }
        }
    }


    @Suppress("DEPRECATION")
    override suspend fun match(
        condition: PushCondition,
        event: ClientEvent<*>,
        eventJson: Lazy<JsonObject?>
    ): Boolean = when (condition) {
        is PushCondition.ContainsDisplayName -> Companion.match(condition, event, userInfo.userId, roomUserStore)
        is PushCondition.RoomMemberCount -> Companion.match(condition, event, roomStore)
        is PushCondition.SenderNotificationPermission -> Companion.match(condition, event, roomStateStore, canDoAction)
        is PushCondition.EventMatch -> Companion.match(condition, eventJson)
        is PushCondition.EventPropertyContains -> Companion.match(condition, eventJson)
        is PushCondition.EventPropertyIs -> Companion.match(condition, eventJson)
        is PushCondition.Unknown -> false
    }
}

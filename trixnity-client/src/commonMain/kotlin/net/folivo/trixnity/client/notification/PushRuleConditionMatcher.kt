package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.client.user.CanDoAction
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.model.push.PushCondition

private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.PushRuleConditionMatcher")

interface PushRuleConditionMatcher {
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
                log.debug { "cannot get the event's value for key '${condition.key}' or value is 'null'" }
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
                log.debug { "cannot get the event's value for key '${condition.key}' or value is 'null'" }
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
                log.debug { "cannot get the event's value for key '${condition.key}' or value is 'null'" }
                false
            } else {
                propertyValue.contains(condition.value)
            }
        }
    }


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

package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withTimeoutOrNull
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.firstWithContent
import net.folivo.trixnity.client.store.StoredNotification
import net.folivo.trixnity.client.store.StoredNotificationUpdate
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.EventsToNotificationUpdates")

interface EventsToNotificationUpdates {
    /**
     * It is expected to call this in reversed (new to old) timeline order.
     */
    suspend operator fun invoke(
        roomId: RoomId,
        eventFlow: Flow<ClientEvent<*>>,
        pushRules: List<PushRule>,
        existingNotifications: Map<String, String>,
        removeStale: Boolean,
    ): List<StoredNotificationUpdate>
}

class EventsToNotificationUpdatesImpl(
    private val roomService: RoomService,
    private val evaluatePushRules: EvaluatePushRules,
    private val eventContentSerializerMappings: EventContentSerializerMappings,
    private val userInfo: UserInfo,
    private val clock: Clock,
) : EventsToNotificationUpdates {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend operator fun invoke(
        roomId: RoomId,
        eventFlow: Flow<ClientEvent<*>>,
        pushRules: List<PushRule>,
        existingNotifications: Map<String, String>,
        removeStale: Boolean,
    ): List<StoredNotificationUpdate> {
        val sortKeyPrefix by lazy { "$roomId-${clock.now()}" }
        var index = UInt.MAX_VALUE
        fun sortKey() = "$sortKeyPrefix-${(index--).toHexString()}"
        val processedNotifications = mutableSetOf<String>()

        val newUpdates = eventFlow.transform { event ->
            val roomId = event.roomIdOrNull ?: return@transform

            val updates = when (event) {
                is ClientEvent.StateBaseEvent -> handleStateEvent(
                    event = event,
                    roomId = roomId,
                    processedNotifications = processedNotifications,
                    pushRules = pushRules,
                    existingNotifications = existingNotifications,
                    sortKeyFactory = ::sortKey,
                )

                is RoomEvent.MessageEvent -> handleMessageEvent(
                    roomId = roomId,
                    event = event,
                    processedNotifications = processedNotifications,
                    pushRules = pushRules,
                    existingNotifications = existingNotifications,
                    sortKeyFactory = ::sortKey,
                )

                else -> emptyList()
            }
            updates.forEach { update ->
                processedNotifications.add(update.id)
                emit(update)
            }
        }.toList()

        val removeStaleUpdates =
            if (removeStale)
                (existingNotifications.keys - newUpdates.map { it.id }
                    .toSet())
                    .also { log.trace { "stale notifications: $it" } }
                    .map { StoredNotificationUpdate.Remove(it, roomId) }
            else emptyList()
        return newUpdates + removeStaleUpdates
    }

    private suspend fun handleStateEvent(
        event: ClientEvent.StateBaseEvent<*>,
        roomId: RoomId,
        processedNotifications: MutableSet<String>,
        pushRules: List<PushRule>,
        existingNotifications: Map<String, String>,
        sortKeyFactory: () -> String,
    ): List<StoredNotificationUpdate> {
        val type =
            eventContentSerializerMappings.state.find { it.kClass.isInstance(event.content) }?.type
                ?: return emptyList()
        val id = StoredNotification.State.id(roomId, type, event.stateKey)
        if (processedNotifications.contains(id)) {
            log.trace { "skip state event $id" }
            return emptyList()
        } else {
            log.trace { "handle state event $id" }
        }

        return notificationUpdate(
            id = id,
            roomId = roomId,
            event = event,
            pushRules = pushRules,
            existingNotifications = existingNotifications,
            sortKeyFactory = sortKeyFactory
        ) {
            StoredNotificationUpdate.Content.State(
                roomId = roomId,
                eventId = event.id,
                type = type,
                stateKey = event.stateKey,
            )
        }?.let { listOf(it) } ?: emptyList()
    }

    private suspend fun handleMessageEvent(
        roomId: RoomId,
        event: RoomEvent.MessageEvent<*>,
        processedNotifications: MutableSet<String>,
        pushRules: List<PushRule>,
        existingNotifications: Map<String, String>,
        sortKeyFactory: () -> String,
    ): List<StoredNotificationUpdate> {
        val id = StoredNotification.Message.id(roomId, event.id)
        if (processedNotifications.contains(id)) {
            log.trace { "skip message event $id" }
            return emptyList()
        } else {
            log.trace { "handle message event $id" }
        }

        val update = notificationUpdate(
            id = id,
            roomId = roomId,
            event = event,
            pushRules = pushRules,
            existingNotifications = existingNotifications,
            sortKeyFactory = sortKeyFactory,
        ) {
            StoredNotificationUpdate.Content.Message(
                roomId = roomId,
                eventId = event.id,
            )
        }

        val content = event.content
        val relatesTo = content.relatesTo
        val redactionOrReplaceUpdate = when {
            content is RedactionEventContent -> run {
                val redactedTimelineEvent = roomService.getTimelineEvent(roomId, content.redacts).first()
                    ?.mergedEvent?.getOrNull() ?: return@run null
                when (redactedTimelineEvent) {
                    is ClientEvent.StateBaseEvent<*> -> {
                        val mapping =
                            eventContentSerializerMappings.state.find { it.kClass.isInstance(redactedTimelineEvent.content) }
                                ?: return@run null
                        val redactedId =
                            StoredNotification.State.id(roomId, mapping.type, redactedTimelineEvent.stateKey)
                        if (processedNotifications.contains(redactedId)) {
                            log.trace { "skip state event redaction $redactedId" }
                            return@run null
                        } else {
                            log.trace { "handle state event redaction $redactedId" }
                        }
                        val currentState =
                            roomService.getState(roomId, mapping.kClass, redactedTimelineEvent.stateKey)
                                .first()
                                ?: return@run null
                        notificationUpdate(
                            id = redactedId,
                            roomId = roomId,
                            event = currentState,
                            pushRules = pushRules,
                            existingNotifications = existingNotifications,
                            sortKeyFactory = sortKeyFactory,
                        ) {
                            StoredNotificationUpdate.Content.State(
                                roomId = roomId,
                                eventId = currentState.id,
                                type = mapping.type,
                                stateKey = currentState.stateKey,
                            )
                        }
                    }

                    is RoomEvent.MessageEvent -> {
                        val redactedId = StoredNotification.Message.id(roomId, content.redacts)
                        log.trace { "handle message event redaction $redactedId" }
                        StoredNotificationUpdate.Remove(
                            id = StoredNotification.Message.id(roomId, content.redacts),
                            roomId = roomId,
                        )
                    }
                }
            }

            relatesTo is RelatesTo.Replace -> run {
                val replacedId =
                    StoredNotification.Message.id(roomId, relatesTo.eventId)
                if (processedNotifications.contains(replacedId)) {
                    log.trace { "skip message event replace $replacedId" }
                    return@run null
                } else {
                    log.trace { "handle message event replace $replacedId" }
                }
                val replacedTimelineEvent =
                    withTimeoutOrNull(5.seconds) {
                        roomService.getTimelineEvent(roomId, relatesTo.eventId) {
                            decryptionTimeout = 5.seconds
                        }
                    }?.firstWithContent()?.mergedEvent?.getOrNull() ?: return@run null
                notificationUpdate(
                    id = replacedId,
                    roomId = roomId,
                    event = replacedTimelineEvent,
                    pushRules = pushRules,
                    existingNotifications = existingNotifications,
                    sortKeyFactory = sortKeyFactory,
                ) {
                    StoredNotificationUpdate.Content.Message(
                        roomId = roomId,
                        eventId = relatesTo.eventId,
                    )
                }
            }

            else -> null
        }
        return listOfNotNull(update, redactionOrReplaceUpdate)
    }

    private suspend fun notificationUpdate(
        id: String,
        roomId: RoomId,
        event: ClientEvent<*>,
        pushRules: List<PushRule>,
        existingNotifications: Map<String, String>,
        sortKeyFactory: () -> String,
        contentFactory: () -> StoredNotificationUpdate.Content,
    ): StoredNotificationUpdate? {
        val actions = newNotificationChanges(event, pushRules)
        val existingSortKey = existingNotifications[id]
        log.trace { "notification update (id=$id, existingSortKey=$existingSortKey, actions=$actions)" }
        return when {
            actions == null ->
                if (existingSortKey != null) StoredNotificationUpdate.Remove(
                    id = id,
                    roomId = roomId
                )
                else null

            existingSortKey != null -> StoredNotificationUpdate.Update(
                id = id,
                sortKey = existingSortKey,
                content = contentFactory(),
                actions = actions,
            )

            else -> StoredNotificationUpdate.New(
                id = id,
                sortKey = sortKeyFactory(),
                content = contentFactory(),
                actions = actions,
            )
        }
    }

    private suspend fun newNotificationChanges(event: ClientEvent<*>, pushRules: List<PushRule>) =
        if (event.senderOrNull != userInfo.userId)
            evaluatePushRules(
                event = event,
                allRules = pushRules
            )
        else null
}

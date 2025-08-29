package net.folivo.trixnity.client.notification

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.firstWithContent
import net.folivo.trixnity.client.store.StoredNotification
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.time.Duration.Companion.seconds

sealed interface NotificationUpdate {
    val id: String
    val roomId: RoomId
    val eventId: EventId?
    val change: Change

    data class Message(
        override val roomId: RoomId,
        override val eventId: EventId,
        override val change: Change,
    ) : NotificationUpdate {
        override val id = StoredNotification.Message.id(roomId, eventId)
    }

    data class State(
        override val roomId: RoomId,
        override val eventId: EventId?,
        val type: String,
        val stateKey: String,
        override val change: Change,
    ) : NotificationUpdate {
        override val id = StoredNotification.State.id(roomId, type, stateKey)
    }

    sealed interface Change {
        data class New(val actions: Set<PushAction>) : Change
        data class Update(val actions: Set<PushAction>) : Change
        data object Remove : Change
    }
}

interface EventsToNotificationUpdates {
    /**
     * It is expected to call this in reversed timeline order.
     */
    suspend operator fun invoke(
        eventFlow: Flow<ClientEvent<*>>,
        pushRules: List<PushRule>
    ): Flow<NotificationUpdate>
}

class EventsToNotificationUpdatesImpl(
    private val roomService: RoomService,
    private val evaluatePushRules: EvaluatePushRules,
    private val eventContentSerializerMappings: EventContentSerializerMappings,
    private val userInfo: UserInfo,
) : EventsToNotificationUpdates {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend operator fun invoke(
        eventFlow: Flow<ClientEvent<*>>,
        pushRules: List<PushRule>,
    ): Flow<NotificationUpdate> = flow {
        val processedNotifications = mutableSetOf<String>()
        eventFlow.collect { event ->
            val roomId = event.roomIdOrNull ?: return@collect

            val updates = when (event) {
                is ClientEvent.StateBaseEvent -> handleStateEvents(
                    event = event,
                    roomId = roomId,
                    processedNotifications = processedNotifications,
                    pushRules = pushRules,
                )

                is RoomEvent.MessageEvent -> handleMessageEvents(
                    roomId = roomId,
                    event = event,
                    processedNotifications = processedNotifications,
                    pushRules = pushRules,
                )

                else -> emptyList()
            }
            updates.forEach { processedNotifications.add(it.id) }
            emitAll(updates.asFlow())
        }
    }

    private suspend fun handleStateEvents(
        event: ClientEvent.StateBaseEvent<*>,
        roomId: RoomId,
        processedNotifications: MutableSet<String>,
        pushRules: List<PushRule>,
    ): List<NotificationUpdate> {
        val type =
            eventContentSerializerMappings.state.find { it.kClass.isInstance(event.content) }?.type
                ?: return emptyList()
        val id = StoredNotification.State.id(roomId, type, event.stateKey)
        if (processedNotifications.contains(id)) return emptyList()
        return listOf(
            NotificationUpdate.State(
                roomId = roomId,
                eventId = event.id,
                type = type,
                stateKey = event.stateKey,
                change = newNotificationChanges(event, pushRules)
                    ?.let { NotificationUpdate.Change.New(it) }
                    ?: NotificationUpdate.Change.Remove,
            )
        )
    }

    private suspend fun handleMessageEvents(
        roomId: RoomId,
        event: RoomEvent.MessageEvent<*>,
        processedNotifications: MutableSet<String>,
        pushRules: List<PushRule>,
    ): List<NotificationUpdate> {
        val id = StoredNotification.Message.id(roomId, event.id)
        if (processedNotifications.contains(id)) return emptyList()

        val update = newNotificationChanges(event, pushRules)?.let {
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = event.id,
                change = NotificationUpdate.Change.New(it)
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
                        if (processedNotifications.contains(redactedId)) return@run null
                        val currentState =
                            roomService.getState(roomId, mapping.kClass, redactedTimelineEvent.stateKey)
                                .first()
                                ?: return@run null
                        NotificationUpdate.State(
                            roomId = roomId,
                            eventId = redactedTimelineEvent.id,
                            type = mapping.type,
                            stateKey = currentState.stateKey,
                            change = newNotificationChanges(currentState, pushRules)
                                ?.let { NotificationUpdate.Change.New(it) }
                                ?: NotificationUpdate.Change.Remove,
                        )
                    }

                    is RoomEvent.MessageEvent -> {
                        NotificationUpdate.Message(
                            roomId = event.roomId,
                            eventId = content.redacts,
                            change = NotificationUpdate.Change.Remove
                        )
                    }
                }
            }

            relatesTo is RelatesTo.Replace -> run {
                val replacedId =
                    StoredNotification.Message.id(roomId, relatesTo.eventId)
                if (processedNotifications.contains(replacedId)) return@run null
                val replacedTimelineEvent =
                    withTimeoutOrNull(5.seconds) {
                        roomService.getTimelineEvent(roomId, relatesTo.eventId) {
                            decryptionTimeout = 5.seconds
                        }
                    }?.firstWithContent()?.mergedEvent?.getOrNull() ?: return@run null
                NotificationUpdate.Message(
                    roomId = roomId,
                    eventId = relatesTo.eventId,
                    change = newNotificationChanges(replacedTimelineEvent, pushRules)
                        ?.let { NotificationUpdate.Change.Update(it) }
                        ?: NotificationUpdate.Change.Remove,
                )
            }

            else -> null
        }
        return listOfNotNull(update, redactionOrReplaceUpdate)
    }

    private suspend fun newNotificationChanges(event: ClientEvent<*>, pushRules: List<PushRule>) =
        if (event.senderOrNull != userInfo.userId)
            evaluatePushRules(
                event = event,
                allRules = pushRules
            )
        else null
}

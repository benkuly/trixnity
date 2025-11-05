package net.folivo.trixnity.client.notification

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.push.PushAction

/**
 * A notification can be of type [Message] or [State].
 */
sealed interface Notification {
    /**
     * Unique identifier, that can be used for various operations in [NotificationService].
     */
    val id: String

    /**
     * Can be used to sort the notification.
     */
    val sortKey: String

    /**
     * The [PushAction] that should be performed, when the notification is created on the device.
     */
    val actions: Set<PushAction>

    /**
     * Indicates, that a user has dismissed a notification on the device.
     */
    val dismissed: Boolean

    data class Message(
        override val id: String,
        override val sortKey: String,
        override val actions: Set<PushAction>,
        override val dismissed: Boolean,
        val timelineEvent: TimelineEvent,
    ) : Notification

    data class State(
        override val id: String,
        override val sortKey: String,
        override val actions: Set<PushAction>,
        override val dismissed: Boolean,
        val stateEvent: ClientEvent.StateBaseEvent<*>,
    ) : Notification
}
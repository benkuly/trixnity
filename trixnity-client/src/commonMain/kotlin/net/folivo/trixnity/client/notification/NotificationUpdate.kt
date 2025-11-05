package net.folivo.trixnity.client.notification

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.push.PushAction

/**
 * A notification update can be of type [New], [Update] or [Remove].
 */
sealed interface NotificationUpdate {
    /**
     * Unique identifier.
     */
    val id: String

    /**
     * Can be used to sort the notification.
     */
    val sortKey: String

    data class New(
        override val id: String,
        override val sortKey: String,
        /**
         * The [PushAction]s that should be performed, when the notification is created on the device.
         */
        val actions: Set<PushAction>,
        val content: Content,
    ) : NotificationUpdate

    data class Update(
        override val id: String,
        override val sortKey: String,
        /**
         * The [PushAction]s that should be performed, when the notification is created on the device.
         */
        val actions: Set<PushAction>,
        val content: Content,
    ) : NotificationUpdate

    data class Remove(
        override val id: String,
        override val sortKey: String,
    ) : NotificationUpdate

    sealed interface Content {
        data class Message(
            val timelineEvent: TimelineEvent,
        ) : Content

        data class State(
            val stateEvent: ClientEvent.StateBaseEvent<*>,
        ) : Content
    }
}
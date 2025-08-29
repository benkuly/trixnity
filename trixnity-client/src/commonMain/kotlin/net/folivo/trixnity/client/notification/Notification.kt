package net.folivo.trixnity.client.notification

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.push.PushAction

sealed interface Notification {
    val id: String
    val actions: Set<PushAction>
    val dismissed: Boolean

    data class Message(
        override val id: String,
        override val actions: Set<PushAction>,
        override val dismissed: Boolean,
        val timelineEvent: TimelineEvent,
    ) : Notification

    data class State(
        override val id: String,
        override val actions: Set<PushAction>,
        override val dismissed: Boolean,
        val stateEvent: ClientEvent.StateBaseEvent<*>,
    ) : Notification

    data class Unknown(
        override val id: String,
        override val dismissed: Boolean,
        val roomId: RoomId,
    ) : Notification {
        override val actions: Set<PushAction> = emptySet()
    }
}
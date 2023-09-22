package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.subscribeContent
import net.folivo.trixnity.core.unsubscribeOnCompletion

class PresenceEventHandler(
    private val api: MatrixClientServerApiClient,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContent(subscriber = ::setPresence).unsubscribeOnCompletion(scope)
    }

    private val _userPresence = MutableStateFlow(mapOf<UserId, PresenceEventContent>())
    internal val userPresence = _userPresence.asStateFlow()

    internal fun setPresence(presenceEvent: Event<PresenceEventContent>) {
        presenceEvent.senderOrNull?.let { sender ->
            _userPresence.update { oldValue -> oldValue + (sender to presenceEvent.content) }
        }
    }
}
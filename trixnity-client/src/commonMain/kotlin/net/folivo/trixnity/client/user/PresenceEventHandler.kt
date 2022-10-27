package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import net.folivo.trixnity.client.getSender
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

class PresenceEventHandler(
    private val api: MatrixClientServerApiClient,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setPresence)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setPresence)
        }
    }

    private val _userPresence = MutableStateFlow(mapOf<UserId, PresenceEventContent>())
    internal val userPresence = _userPresence.asStateFlow()

    internal fun setPresence(presenceEvent: Event<PresenceEventContent>) {
        presenceEvent.getSender()?.let { sender ->
            _userPresence.update { oldValue -> oldValue + (sender to presenceEvent.content) }
        }
    }
}
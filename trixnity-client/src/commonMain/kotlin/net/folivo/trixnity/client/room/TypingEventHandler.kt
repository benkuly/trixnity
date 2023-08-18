package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

class TypingEventHandler(
    private val api: MatrixClientServerApiClient,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setTyping)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setTyping)
        }
    }

    private val _usersTyping = MutableStateFlow(mapOf<RoomId, TypingEventContent>())
    internal val usersTyping = _usersTyping.asStateFlow()

    internal fun setTyping(typingEvent: Event<TypingEventContent>) {
        typingEvent.roomIdOrNull?.let { roomId ->
            _usersTyping.update { oldValue -> oldValue + (roomId to typingEvent.content) }
        }
    }
}
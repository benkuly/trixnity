package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.subscribeContent
import net.folivo.trixnity.core.unsubscribeOnCompletion

interface TypingEventHandler : EventHandler {
    val usersTyping: StateFlow<Map<RoomId, TypingEventContent>>
}

class TypingEventHandlerImpl(
    private val api: MatrixClientServerApiClient,
) : TypingEventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContent(subscriber = ::setTyping).unsubscribeOnCompletion(scope)
    }

    private val _usersTyping = MutableStateFlow(mapOf<RoomId, TypingEventContent>())
    override val usersTyping = _usersTyping.asStateFlow()

    internal fun setTyping(typingEvent: ClientEvent<TypingEventContent>) {
        typingEvent.roomIdOrNull?.let { roomId ->
            _usersTyping.update { oldValue -> oldValue + (roomId to typingEvent.content) }
        }
    }
}
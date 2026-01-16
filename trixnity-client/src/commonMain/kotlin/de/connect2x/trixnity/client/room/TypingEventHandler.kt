package de.connect2x.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.TypingEventContent
import de.connect2x.trixnity.core.model.events.roomIdOrNull
import de.connect2x.trixnity.core.subscribeContent
import de.connect2x.trixnity.core.unsubscribeOnCompletion

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
package net.folivo.trixnity.client

import io.ktor.client.*
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

class MatrixClientConfiguration {
    var setOwnMessagesAsFullyRead: Boolean = false
    var customMappings: EventContentSerializerMappings? = null
    var customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf()
    var httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) }
    var lastRelevantEventFilter: (ClientEvent.RoomEvent<*>) -> Boolean = { it is ClientEvent.MessageEvent<*> }
}
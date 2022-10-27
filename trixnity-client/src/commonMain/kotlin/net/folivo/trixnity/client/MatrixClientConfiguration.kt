package net.folivo.trixnity.client

import io.ktor.client.*
import net.folivo.trixnity.core.model.events.Event
import org.koin.core.module.Module

class MatrixClientConfiguration {
    var setOwnMessagesAsFullyRead: Boolean = false
    var lastRelevantEventFilter: (Event.RoomEvent<*>) -> Boolean = { it is Event.MessageEvent<*> }
    var httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) }
    var modules: List<Module> = createDefaultModules()
}
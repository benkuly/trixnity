package net.folivo.trixnity.client

import io.ktor.client.*
import org.koin.core.module.Module

class MatrixClientConfiguration {
    var setOwnMessagesAsFullyRead: Boolean = false
    var httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) }
    var modules: List<Module> = createDefaultModules()
}
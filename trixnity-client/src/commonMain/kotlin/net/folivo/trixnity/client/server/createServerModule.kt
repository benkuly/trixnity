package net.folivo.trixnity.client.server

import net.folivo.trixnity.core.EventHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createServerModule() = module {
    singleOf(::ServerDataService) {
        bind<EventHandler>()
        named<ServerDataService>()
    }
}
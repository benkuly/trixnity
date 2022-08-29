package net.folivo.trixnity.client.push

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createPushModule() = module {
    singleOf(::PushService) { bind<IPushService>() }
}
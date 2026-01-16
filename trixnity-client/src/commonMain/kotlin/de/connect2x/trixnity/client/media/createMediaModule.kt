package de.connect2x.trixnity.client.media

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createMediaModule() = module {
    singleOf(::MediaServiceImpl) { bind<MediaService>() }
}
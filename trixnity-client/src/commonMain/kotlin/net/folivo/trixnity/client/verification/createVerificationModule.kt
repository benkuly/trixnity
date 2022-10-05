package net.folivo.trixnity.client.verification

import net.folivo.trixnity.core.EventHandler
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun createVerificationModule() = module {
    single {
        VerificationServiceImpl(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }.apply {
        bind<VerificationService>()
        bind<EventHandler>()
        named<VerificationServiceImpl>()
    }
}
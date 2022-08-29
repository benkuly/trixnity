package net.folivo.trixnity.client.verification

import net.folivo.trixnity.core.EventHandler
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun createVerificationModule() = module {
    single {
        VerificationService(
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
        bind<IVerificationService>()
        bind<EventHandler>()
        named<VerificationService>()
    }
}
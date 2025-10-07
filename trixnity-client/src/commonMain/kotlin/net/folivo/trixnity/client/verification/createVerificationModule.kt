package net.folivo.trixnity.client.verification

import net.folivo.trixnity.core.EventHandler
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun createVerificationModule() = module {
    single {
        VerificationServiceImpl(
            userInfo = get(),
            api = get(),
            keyStore = get(),
            globalAccountDataStore = get(),
            olmDecrypter = get(),
            olmEncryptionService = get(),
            roomService = get(),
            userService = get(),
            keyService = get(),
            keyTrustService = get(),
            keySecretService = get(),
            currentSyncState = get(),
            clock = get(),
            driver = get(),
        )
    }.apply {
        bind<VerificationService>()
        bind<EventHandler>()
        named<VerificationServiceImpl>()
    }
}
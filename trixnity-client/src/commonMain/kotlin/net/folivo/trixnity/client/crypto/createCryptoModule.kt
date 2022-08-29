package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.crypto.olm.*
import net.folivo.trixnity.crypto.sign.ISignService
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.SignServiceStore
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun createCryptoModule() = module {
    singleOf(::ClientOneTimeKeysCountEmitter) { bind<OneTimeKeysCountEmitter>() }
    singleOf(::ClientSignServiceStore) { bind<SignServiceStore>() }
    singleOf(::SignService) { bind<ISignService>() }
    singleOf(::ClientOlmEventHandlerRequestHandler) { bind<OlmEventHandlerRequestHandler>() }
    singleOf(::ClientOlmEncryptionServiceRequestHandler) { bind<OlmEncryptionServiceRequestHandler>() }
    singleOf(::ClientOlmStore) { bind<OlmStore>() }
    singleOf(::OlmEncryptionService) { bind<IOlmEncryptionService>() }
    singleOf(::OlmDecrypter) { bind<IOlmDecrypter>() }
    singleOf(::PossiblyEncryptEvent) { bind<IPossiblyEncryptEvent>() }
    single<EventHandler>(named<OlmEventHandler>()) {
        OlmEventHandler(get<IMatrixClientServerApiClient>().sync, get(), get(), get(), get(), get())
    }
}
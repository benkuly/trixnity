package de.connect2x.trixnity.client.cryptodriver

import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.crypto.olm.*
import de.connect2x.trixnity.crypto.sign.SignService
import de.connect2x.trixnity.crypto.sign.SignServiceImpl
import de.connect2x.trixnity.crypto.sign.SignServiceStore
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun createCryptoModule() = module {
    singleOf(::ClientOlmKeysChangeEmitter) { bind<OlmKeysChangeEmitter>() }
    singleOf(::ClientSignServiceStore) { bind<SignServiceStore>() }
    singleOf(::SignServiceImpl) { bind<SignService>() }
    singleOf(::ClientOlmEventHandlerRequestHandler) { bind<OlmEventHandlerRequestHandler>() }
    singleOf(::ClientOlmEncryptionServiceRequestHandler) { bind<OlmEncryptionServiceRequestHandler>() }
    singleOf(::ClientOlmStore) { bind<OlmStore>() }
    singleOf(::OlmEncryptionServiceImpl) { bind<OlmEncryptionService>() }
    singleOf(::OlmDecrypterImpl) { bind<OlmDecrypter>() }
    single<EventHandler>(named<OlmEventHandler>()) {
        OlmEventHandler(get(), get<MatrixClientServerApiClient>().sync, get(), get(), get(), get(), get(), get(), get())
    }
}
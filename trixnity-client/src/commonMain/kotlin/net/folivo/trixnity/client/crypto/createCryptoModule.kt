package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.crypto.olm.*
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.SignServiceImpl
import net.folivo.trixnity.crypto.sign.SignServiceStore
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun createCryptoModule() = module {
    singleOf(::ClientOneTimeKeysCountEmitter) { bind<OneTimeKeysCountEmitter>() }
    singleOf(::ClientSignServiceStore) { bind<SignServiceStore>() }
    singleOf(::SignServiceImpl) { bind<SignService>() }
    singleOf(::ClientOlmEventHandlerRequestHandler) { bind<OlmEventHandlerRequestHandler>() }
    singleOf(::ClientOlmEncryptionServiceRequestHandler) { bind<OlmEncryptionServiceRequestHandler>() }
    singleOf(::ClientOlmStore) { bind<OlmStore>() }
    singleOf(::OlmEncryptionServiceImpl) { bind<OlmEncryptionService>() }
    singleOf(::OlmDecrypterImpl) { bind<OlmDecrypter>() }
    singleOf(::PossiblyEncryptEventImpl) { bind<PossiblyEncryptEvent>() }
    single<EventHandler>(named<OlmEventHandler>()) {
        OlmEventHandler(get<MatrixClientServerApiClient>().sync, get(), get(), get(), get(), get())
    }
}
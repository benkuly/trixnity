package net.folivo.trixnity.client.key

import net.folivo.trixnity.client.user.LazyMemberEventHandler
import net.folivo.trixnity.core.EventHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun createKeyModule() = module {
    singleOf(::OutdatedKeysHandler) {
        bind<EventHandler>()
        bind<LazyMemberEventHandler>()
        named<OutdatedKeysHandler>()
    }
    singleOf(::IncomingRoomKeyRequestEventHandler) {
        bind<EventHandler>()
        named<IncomingRoomKeyRequestEventHandler>()
    }
    singleOf(::OutgoingRoomKeyRequestEventHandlerImpl) {
        bind<OutgoingRoomKeyRequestEventHandler>()
        bind<EventHandler>()
        named<OutgoingRoomKeyRequestEventHandler>()
    }
    singleOf(::IncomingSecretKeyRequestEventHandler) {
        bind<EventHandler>()
        named<IncomingSecretKeyRequestEventHandler>()
    }
    single<EventHandler>(named<OutgoingSecretKeyRequestEventHandler>()) {
        OutgoingSecretKeyRequestEventHandler(
            userInfo = get(),
            api = get(),
            olmDecrypter = get(),
            keyBackupService = get(named<KeyBackupService>()),
            keyStore = get(),
            globalAccountDataStore = get(),
            currentSyncState = get()
        )
    }
    singleOf(::KeySecretServiceImpl) { bind<KeySecretService>() }
    singleOf(::KeyTrustServiceImpl) { bind<KeyTrustService>() }
    singleOf(::KeyBackupServiceImpl) {
        bind<KeyBackupService>()
        bind<EventHandler>()
        named<KeyBackupService>()
    }
    single<KeyService> {
        KeyServiceImpl(
            userInfo = get(),
            keyStore = get(),
            olmCryptoStore = get(),
            globalAccountDataStore = get(),
            roomService = get(),
            signService = get(),
            keyBackupService = get(named<KeyBackupService>()),
            keyTrustService = get(),
            api = get()
        )
    }
}
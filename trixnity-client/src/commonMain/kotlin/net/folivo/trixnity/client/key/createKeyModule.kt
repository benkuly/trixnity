package net.folivo.trixnity.client.key

import net.folivo.trixnity.core.EventHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun createKeyModule() = module {
    singleOf(::KeyMemberEventHandler) {
        bind<EventHandler>()
        named<KeyMemberEventHandler>()
    }
    singleOf(::KeyEncryptionEventHandler) {
        bind<EventHandler>()
        named<KeyEncryptionEventHandler>()
    }
    singleOf(::DeviceListsHandler) {
        bind<EventHandler>()
        named<DeviceListsHandler>()
    }
    singleOf(::OutdatedKeysHandler) {
        bind<EventHandler>()
        named<OutdatedKeysHandler>()
    }
    singleOf(::IncomingKeyRequestEventHandler) {
        bind<EventHandler>()
        named<IncomingKeyRequestEventHandler>()
    }
    single<EventHandler>(named<OutgoingKeyRequestEventHandler>()) {
        OutgoingKeyRequestEventHandler(get(), get(), get(), get(named<KeyBackupService>()), get(), get(), get())
    }
    singleOf(::KeySecretService) { bind<IKeySecretService>() }
    singleOf(::KeyTrustService) { bind<IKeyTrustService>() }
    singleOf(::KeyBackupService) {
        bind<IKeyBackupService>()
        bind<EventHandler>()
        named<KeyBackupService>()
    }
    single<IKeyService> {
        KeyService(get(), get(), get(), get(), get(), get(named<KeyBackupService>()), get(), get())
    }
}
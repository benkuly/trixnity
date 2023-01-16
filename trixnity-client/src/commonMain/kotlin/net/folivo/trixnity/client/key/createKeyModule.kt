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
    singleOf(::IncomingSecretKeyRequestEventHandler) {
        bind<EventHandler>()
        named<IncomingSecretKeyRequestEventHandler>()
    }
    single<EventHandler>(named<OutgoingSecretKeyRequestEventHandler>()) {
        OutgoingSecretKeyRequestEventHandler(get(), get(), get(), get(named<KeyBackupServiceImpl>()), get(), get(), get())
    }
    singleOf(::KeySecretServiceImpl) { bind<KeySecretService>() }
    singleOf(::KeyTrustServiceImpl) { bind<KeyTrustService>() }
    singleOf(::KeyBackupServiceImpl) {
        bind<KeyBackupService>()
        bind<EventHandler>()
        named<KeyBackupServiceImpl>()
    }
    single<KeyService> {
        KeyServiceImpl(get(), get(), get(), get(), get(), get(named<KeyBackupServiceImpl>()), get(), get())
    }
}
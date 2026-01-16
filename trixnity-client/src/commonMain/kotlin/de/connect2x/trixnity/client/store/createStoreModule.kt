package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.media.MediaStore
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.core.EventHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

fun createStoreModule() = module {
    singleOf(::TransactionManagerImpl).bind<TransactionManager>()
    singleOf(::ObservableCacheStatisticCollector) {
        bind<EventHandler>()
        bind<ObservableCacheStatisticCollector>()
    }
    singleOf(::AccountStore)
    singleOf(::AuthenticationStore)
    singleOf(::ServerDataStore)
    singleOf(::GlobalAccountDataStore)
    single {
        KeyStore(
            outdatedKeysRepository = get(),
            deviceKeysRepository = get(),
            crossSigningKeysRepository = get(),
            keyVerificationStateRepository = get(),
            keyChainLinkRepository = get(),
            secretsRepository = get(),
            secretKeyRequestRepository = get(),
            roomKeyRequestRepository = get(),
            tm = get(),
            config = get(),
            statisticCollector = get(),
            storeScope = get(),
            clock = get(),
        )
    }
    singleOf(::MediaCacheMappingStore)
    singleOf(::OlmCryptoStore)
    singleOf(::RoomAccountDataStore)
    singleOf(::RoomOutboxMessageStore)
    singleOf(::RoomStateStore)
    singleOf(::RoomStore)
    singleOf(::RoomTimelineStore)
    singleOf(::RoomUserStore)
    singleOf(::UserPresenceStore)
    singleOf(::NotificationStore)

    single {
        RootStore(
            listOfNotNull(
                getOrNull<AccountStore>(),
                getOrNull<AuthenticationStore>(),
                getOrNull<ServerDataStore>(),
                getOrNull<GlobalAccountDataStore>(),
                getOrNull<KeyStore>(),
                getOrNull<MediaCacheMappingStore>(),
                getOrNull<MediaStore>(),
                getOrNull<OlmCryptoStore>(),
                getOrNull<RoomAccountDataStore>(),
                getOrNull<RoomOutboxMessageStore>(),
                getOrNull<RoomStateStore>(),
                getOrNull<RoomStore>(),
                getOrNull<RoomTimelineStore>(),
                getOrNull<RoomUserStore>(),
                getOrNull<UserPresenceStore>(),
                getOrNull<NotificationStore>(),
            )
        )
    }
}
package net.folivo.trixnity.client.store

import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.core.EventHandler
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
            storeScope = get()
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

    single {
        RootStore(
            listOfNotNull(
                getOrNull<AccountStore>(),
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
            )
        )
    }
}
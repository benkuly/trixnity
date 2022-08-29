package net.folivo.trixnity.client.store

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createStoreModule() = module {
    singleOf(::AccountStore)
    singleOf(::GlobalAccountDataStore)
    singleOf(::KeyStore)
    singleOf(::MediaStore)
    singleOf(::OlmStore)
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
                getOrNull<GlobalAccountDataStore>(),
                getOrNull<KeyStore>(),
                getOrNull<MediaStore>(),
                getOrNull<OlmStore>(),
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
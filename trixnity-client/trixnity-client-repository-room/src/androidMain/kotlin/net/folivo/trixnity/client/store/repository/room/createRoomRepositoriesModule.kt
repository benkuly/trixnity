package net.folivo.trixnity.client.store.repository.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import net.folivo.trixnity.client.store.repository.*
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createRoomRepositoriesModule(
    appContext: Context,
    databaseName: String = "trixnity.db",
    extraDbConfig: RoomDatabase.Builder<*>.() -> RoomDatabase.Builder<*> = { this }
): Module = module {
    /* Provide the actual database as a singleton */
    single {
        Room.databaseBuilder(appContext, TrixnityRoomDatabase::class.java, databaseName)
            .apply { extraDbConfig() }
            .build()
    }

    /* Bind the Trixnity required interfaces */
    singleOf(::RoomAccountRepository) { bind<AccountRepository>() }
    singleOf(::RoomCrossSigningKeysRepository) { bind<CrossSigningKeysRepository>() }
    singleOf(::RoomDeviceKeysRepository) { bind<DeviceKeysRepository>() }
    singleOf(::RoomGlobalAccountDataRepository) { bind<GlobalAccountDataRepository>() }
    singleOf(::RoomInboundMegolmMessageIndexRepository) { bind<InboundMegolmMessageIndexRepository>() }
    singleOf(::RoomInboundMegolmSessionRepository) { bind<InboundMegolmSessionRepository>() }
    singleOf(::RoomKeyChainLinkRepository) { bind<KeyChainLinkRepository>() }
    singleOf(::RoomKeyVerificationStateRepository) { bind<KeyVerificationStateRepository>() }
    singleOf(::RoomMediaCacheMappingRepository) { bind<MediaCacheMappingRepository>() }
    singleOf(::RoomOlmAccountRepository) { bind<OlmAccountRepository>() }
    singleOf(::RoomOlmForgetFallbackKeyAfterRepository) { bind<OlmForgetFallbackKeyAfterRepository>() }
    singleOf(::RoomOlmSessionRepository) { bind<OlmSessionRepository>() }
    singleOf(::RoomOutboundMegolmSessionRepository) { bind<OutboundMegolmSessionRepository>() }
    singleOf(::RoomOutdatedKeysRepository) { bind<OutdatedKeysRepository>() }
    singleOf(::RoomRepositoryTransactionManager) { bind<RepositoryTransactionManager>() }
    singleOf(::RoomRoomAccountDataRepository) { bind<RoomAccountDataRepository>() }
    singleOf(::RoomRoomKeyRequestRepository) { bind<RoomKeyRequestRepository>() }
    singleOf(::RoomRoomOutboxMessageRepository) { bind<RoomOutboxMessageRepository>() }
    singleOf(::RoomRoomRepository) { bind<RoomRepository>() }
    singleOf(::RoomRoomStateRepository) { bind<RoomStateRepository>() }
    singleOf(::RoomRoomUserRepository) { bind<RoomUserRepository>() }
    singleOf(::RoomSecretKeyRequestRepository) { bind<SecretKeyRequestRepository>() }
    singleOf(::RoomSecretsRepository) { bind<SecretsRepository>() }
    singleOf(::RoomTimelineEventRelationRepository) { bind<TimelineEventRelationRepository>() }
    singleOf(::RoomTimelineEventRepository) { bind<TimelineEventRepository>() }
}

package net.folivo.trixnity.client.store.repository.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.repository.CrossSigningKeysRepository
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.KeyChainLinkRepository
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository
import net.folivo.trixnity.client.store.repository.OlmAccountRepository
import net.folivo.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import net.folivo.trixnity.client.store.repository.OlmSessionRepository
import net.folivo.trixnity.client.store.repository.OutboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.OutdatedKeysRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomKeyRequestRepository
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.client.store.repository.SecretKeyRequestRepository
import net.folivo.trixnity.client.store.repository.SecretsRepository
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.client.store.repository.TimelineEventRepository
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
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
            .extraDbConfig()
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

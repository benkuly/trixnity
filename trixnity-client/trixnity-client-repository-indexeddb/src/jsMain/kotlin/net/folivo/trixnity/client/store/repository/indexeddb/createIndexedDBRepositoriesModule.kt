package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.openDatabase
import mu.KotlinLogging
import net.folivo.trixnity.client.store.repository.*
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private val log = KotlinLogging.logger {}

suspend fun createIndexedDBRepositoriesModule(
    databaseName: String,
): Module {
    log.debug { "create missing tables and columns" }
    val database = openDatabase(databaseName, 1) { database, oldVersion, _ ->
        IndexedDBAccountRepository.apply { migrate(database, oldVersion) }
        IndexedDBCrossSigningKeysRepository.apply { migrate(database, oldVersion) }
        IndexedDBDeviceKeysRepository.apply { migrate(database, oldVersion) }
        IndexedDBGlobalAccountDataRepository.apply { migrate(database, oldVersion) }
        IndexedDBInboundMegolmMessageIndexRepository.apply { migrate(database, oldVersion) }
        IndexedDBInboundMegolmSessionRepository.apply { migrate(database, oldVersion) }
        IndexedDBKeyChainLinkRepository.apply { migrate(database, oldVersion) }
        IndexedDBKeyVerificationStateRepository.apply { migrate(database, oldVersion) }
        IndexedDBMediaCacheMappingRepository.apply { migrate(database, oldVersion) }
        IndexedDBOlmAccountRepository.apply { migrate(database, oldVersion) }
        IndexedDBOlmForgetFallbackKeyAfterRepository.apply { migrate(database, oldVersion) }
        IndexedDBOlmSessionRepository.apply { migrate(database, oldVersion) }
        IndexedDBOutboundMegolmSessionRepository.apply { migrate(database, oldVersion) }
        IndexedDBOutdatedKeysRepository.apply { migrate(database, oldVersion) }
        IndexedDBRoomAccountDataRepository.apply { migrate(database, oldVersion) }
        IndexedDBRoomKeyRequestRepository.apply { migrate(database, oldVersion) }
        IndexedDBRoomOutboxMessageRepository.apply { migrate(database, oldVersion) }
        IndexedDBRoomRepository.apply { migrate(database, oldVersion) }
        IndexedDBRoomStateRepository.apply { migrate(database, oldVersion) }
        IndexedDBRoomUserRepository.apply { migrate(database, oldVersion) }
        IndexedDBSecretKeyRequestRepository.apply { migrate(database, oldVersion) }
        IndexedDBSecretsRepository.apply { migrate(database, oldVersion) }
        IndexedDBTimelineEventRelationRepository.apply { migrate(database, oldVersion) }
        IndexedDBTimelineEventRepository.apply { migrate(database, oldVersion) }
    }
    log.debug { "finished database migration" }
    return module {
        single<RepositoryTransactionManager> {
            IndexedDBRepositoryTransactionManager(
                database, arrayOf(
                    IndexedDBAccountRepository.objectStoreName,
                    IndexedDBCrossSigningKeysRepository.objectStoreName,
                    IndexedDBDeviceKeysRepository.objectStoreName,
                    IndexedDBGlobalAccountDataRepository.objectStoreName,
                    IndexedDBInboundMegolmMessageIndexRepository.objectStoreName,
                    IndexedDBInboundMegolmSessionRepository.objectStoreName,
                    IndexedDBKeyChainLinkRepository.objectStoreName,
                    IndexedDBKeyVerificationStateRepository.objectStoreName,
                    IndexedDBMediaCacheMappingRepository.objectStoreName,
                    IndexedDBOlmAccountRepository.objectStoreName,
                    IndexedDBOlmForgetFallbackKeyAfterRepository.objectStoreName,
                    IndexedDBOlmSessionRepository.objectStoreName,
                    IndexedDBOutboundMegolmSessionRepository.objectStoreName,
                    IndexedDBOutdatedKeysRepository.objectStoreName,
                    IndexedDBRoomAccountDataRepository.objectStoreName,
                    IndexedDBRoomKeyRequestRepository.objectStoreName,
                    IndexedDBRoomOutboxMessageRepository.objectStoreName,
                    IndexedDBRoomRepository.objectStoreName,
                    IndexedDBRoomStateRepository.objectStoreName,
                    IndexedDBRoomUserRepository.objectStoreName,
                    IndexedDBSecretKeyRequestRepository.objectStoreName,
                    IndexedDBSecretsRepository.objectStoreName,
                    IndexedDBTimelineEventRelationRepository.objectStoreName,
                    IndexedDBTimelineEventRepository.objectStoreName,
                )
            )
        }
        singleOf(::IndexedDBAccountRepository) { bind<AccountRepository>() }
        singleOf(::IndexedDBCrossSigningKeysRepository) { bind<CrossSigningKeysRepository>() }
        singleOf(::IndexedDBDeviceKeysRepository) { bind<DeviceKeysRepository>() }
        singleOf(::IndexedDBGlobalAccountDataRepository) { bind<GlobalAccountDataRepository>() }
        singleOf(::IndexedDBInboundMegolmMessageIndexRepository) { bind<InboundMegolmMessageIndexRepository>() }
        singleOf(::IndexedDBInboundMegolmSessionRepository) { bind<InboundMegolmSessionRepository>() }
        singleOf(::IndexedDBKeyChainLinkRepository) { bind<KeyChainLinkRepository>() }
        singleOf(::IndexedDBKeyVerificationStateRepository) { bind<KeyVerificationStateRepository>() }
        singleOf(::IndexedDBMediaCacheMappingRepository) { bind<MediaCacheMappingRepository>() }
        singleOf(::IndexedDBOlmAccountRepository) { bind<OlmAccountRepository>() }
        singleOf(::IndexedDBOlmForgetFallbackKeyAfterRepository) { bind<OlmForgetFallbackKeyAfterRepository>() }
        singleOf(::IndexedDBOlmSessionRepository) { bind<OlmSessionRepository>() }
        singleOf(::IndexedDBOutboundMegolmSessionRepository) { bind<OutboundMegolmSessionRepository>() }
        singleOf(::IndexedDBOutdatedKeysRepository) { bind<OutdatedKeysRepository>() }
        singleOf(::IndexedDBRoomAccountDataRepository) { bind<RoomAccountDataRepository>() }
        singleOf(::IndexedDBRoomKeyRequestRepository) { bind<RoomKeyRequestRepository>() }
        singleOf(::IndexedDBRoomOutboxMessageRepository) { bind<RoomOutboxMessageRepository>() }
        singleOf(::IndexedDBRoomRepository) { bind<RoomRepository>() }
        singleOf(::IndexedDBRoomStateRepository) { bind<RoomStateRepository>() }
        singleOf(::IndexedDBRoomUserRepository) { bind<RoomUserRepository>() }
        singleOf(::IndexedDBSecretKeyRequestRepository) { bind<SecretKeyRequestRepository>() }
        singleOf(::IndexedDBSecretsRepository) { bind<SecretsRepository>() }
        singleOf(::IndexedDBTimelineEventRelationRepository) { bind<TimelineEventRelationRepository>() }
        singleOf(::IndexedDBTimelineEventRepository) { bind<TimelineEventRepository>() }
    }
}
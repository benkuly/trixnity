package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.openDatabase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.RepositoriesModule
import net.folivo.trixnity.client.store.repository.*
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private val log =
    KotlinLogging.logger("net.folivo.trixnity.client.store.repository.indexeddb.IndexedDBRepositoriesModule")

fun RepositoriesModule.Companion.indexedDB(databaseName: String = "trixnity"): RepositoriesModule = RepositoriesModule {
    log.debug { "create missing tables and columns" }
    val database = createDatabase(databaseName)
    log.debug { "finished database migration" }
    module {
        single { database }
        single(createdAtStart = true) {
            get<CoroutineScope>().coroutineContext.job.invokeOnCompletion {
                database.close()
            }
        }
        single<RepositoryTransactionManager> { IndexedDBRepositoryTransactionManager(get(), allStoreNames) }
        singleOf(::IndexedDBAccountRepository) { bind<AccountRepository>() }
        singleOf(::IndexedServerDataRepository) { bind<ServerDataRepository>() }
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
        singleOf(::IndexedDBRoomUserReceiptsRepository) { bind<RoomUserReceiptsRepository>() }
        singleOf(::IndexedDBSecretKeyRequestRepository) { bind<SecretKeyRequestRepository>() }
        singleOf(::IndexedDBSecretsRepository) { bind<SecretsRepository>() }
        singleOf(::IndexedDBTimelineEventRelationRepository) { bind<TimelineEventRelationRepository>() }
        singleOf(::IndexedDBTimelineEventRepository) { bind<TimelineEventRepository>() }
        singleOf(::IndexedDBUserPresenceRepository) { bind<UserPresenceRepository>() }
        singleOf(::IndexedDBNotificationRepository) { bind<NotificationRepository>() }
        singleOf(::IndexedDBNotificationStateRepository) { bind<NotificationStateRepository>() }
        singleOf(::IndexedDBNotificationUpdateRepository) { bind<NotificationUpdateRepository>() }
        singleOf(::IndexedDBMigrationRepository) { bind<MigrationRepository>() }
    }
}

internal val allStoreNames = arrayOf(
    IndexedDBAccountRepository.objectStoreName,
    IndexedServerDataRepository.objectStoreName,
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
    IndexedDBRoomUserReceiptsRepository.objectStoreName,
    IndexedDBSecretKeyRequestRepository.objectStoreName,
    IndexedDBSecretsRepository.objectStoreName,
    IndexedDBTimelineEventRelationRepository.objectStoreName,
    IndexedDBTimelineEventRepository.objectStoreName,
    IndexedDBUserPresenceRepository.objectStoreName,
    IndexedDBNotificationRepository.objectStoreName,
    IndexedDBNotificationStateRepository.objectStoreName,
    IndexedDBNotificationUpdateRepository.objectStoreName,
    IndexedDBMigrationRepository.objectStoreName,
)

internal suspend fun createDatabase(databaseName: String) =
    openDatabase(databaseName, 9) { database, oldVersion, _ ->
        IndexedDBAccountRepository.apply { migrate(database, oldVersion) }
        IndexedServerDataRepository.apply { migrate(database, oldVersion) }
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
        IndexedDBRoomUserReceiptsRepository.apply { migrate(database, oldVersion) }
        IndexedDBSecretKeyRequestRepository.apply { migrate(database, oldVersion) }
        IndexedDBSecretsRepository.apply { migrate(database, oldVersion) }
        IndexedDBTimelineEventRelationRepository.apply { migrate(database, oldVersion) }
        IndexedDBTimelineEventRepository.apply { migrate(database, oldVersion) }
        IndexedDBUserPresenceRepository.apply { migrate(database, oldVersion) }
        IndexedDBNotificationRepository.apply { migrate(database, oldVersion) }
        IndexedDBNotificationStateRepository.apply { migrate(database, oldVersion) }
        IndexedDBNotificationUpdateRepository.apply { migrate(database, oldVersion) }
        IndexedDBMigrationRepository.apply { migrate(database, oldVersion) }
    }
package net.folivo.trixnity.client.store.repository.indexeddb

import io.kotest.core.spec.style.ShouldSpec
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.client.store.repository.test.repositoryTestSuite
import net.folivo.trixnity.utils.nextString
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.random.Random

class RepositoryTestSuite : ShouldSpec({
    // remove customRepositoryTransactionManager as soon as a solution is found for async work within a transaction
    repositoryTestSuite(customRepositoryTransactionManager = {
        IndexedDBRepositoryTransactionManager(createDatabase(Random.nextString(22)), allStoreNames)
    }) {
        val database = createDatabase(Random.nextString(22))
        module {
            single { database }
            single<RepositoryTransactionManager> {
                IndexedDBRepositoryTransactionManager(get(), allStoreNames, testMode = true)
            }
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
        }
    }
})


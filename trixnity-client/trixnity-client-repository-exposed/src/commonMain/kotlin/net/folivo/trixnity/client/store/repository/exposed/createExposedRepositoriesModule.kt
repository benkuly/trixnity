package net.folivo.trixnity.client.store.repository.exposed

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.repository.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private val log = KotlinLogging.logger {}

suspend fun createExposedRepositoriesModule(
    database: Database,
): Module {
    log.debug { "create missing tables and columns" }
    newSuspendedTransaction(Dispatchers.IO, database) {
        val allTables = arrayOf(
            ExposedAccount,
            ExposedCrossSigningKeys,
            ExposedDeviceKeys,
            ExposedGlobalAccountData,
            ExposedInboundMegolmMessageIndex,
            ExposedInboundMegolmSession,
            ExposedKeyChainLink,
            ExposedKeyVerificationState,
            ExposedSecrets,
            ExposedSecretKeyRequest,
            ExposedRoomKeyRequest,
            ExposedOlmAccount,
            ExposedOlmForgetFallbackKeyAfter,
            ExposedOlmSession,
            ExposedOutboundMegolmSession,
            ExposedOutdatedKeys,
            ExposedRoomAccountData,
            ExposedRoomOutboxMessage,
            ExposedRoom,
            ExposedRoomState,
            ExposedTimelineEvent,
            ExposedTimelineEventRelation,
            ExposedRoomUser,
            ExposedRoomUserReceipts,
            ExposedMediaCacheMapping,
        )
        SchemaUtils.createMissingTablesAndColumns(*allTables)
    }
    log.debug { "finished create missing tables and columns" }
    return module {
        single { database }
        singleOf(::ExposedRepositoryTransactionManager) { bind<RepositoryTransactionManager>() }
        singleOf(::ExposedAccountRepository) { bind<AccountRepository>() }
        singleOf(::ExposedOutdatedKeysRepository) { bind<OutdatedKeysRepository>() }
        singleOf(::ExposedDeviceKeysRepository) { bind<DeviceKeysRepository>() }
        singleOf(::ExposedCrossSigningKeysRepository) { bind<CrossSigningKeysRepository>() }
        singleOf(::ExposedKeyVerificationStateRepository) { bind<KeyVerificationStateRepository>() }
        singleOf(::ExposedKeyChainLinkRepository) { bind<KeyChainLinkRepository>() }
        singleOf(::ExposedSecretsRepository) { bind<SecretsRepository>() }
        singleOf(::ExposedSecretKeyRequestRepository) { bind<SecretKeyRequestRepository>() }
        singleOf(::ExposedRoomKeyRequestRepository) { bind<RoomKeyRequestRepository>() }
        singleOf(::ExposedOlmAccountRepository) { bind<OlmAccountRepository>() }
        singleOf(::ExposedOlmForgetFallbackKeyAfterRepository) { bind<OlmForgetFallbackKeyAfterRepository>() }
        singleOf(::ExposedOlmSessionRepository) { bind<OlmSessionRepository>() }
        singleOf(::ExposedInboundMegolmSessionRepository) { bind<InboundMegolmSessionRepository>() }
        singleOf(::ExposedInboundMegolmMessageIndexRepository) { bind<InboundMegolmMessageIndexRepository>() }
        singleOf(::ExposedOutboundMegolmSessionRepository) { bind<OutboundMegolmSessionRepository>() }
        singleOf(::ExposedRoomRepository) { bind<RoomRepository>() }
        singleOf(::ExposedRoomUserRepository) { bind<RoomUserRepository>() }
        singleOf(::ExposedRoomUserReceiptsRepository) { bind<RoomUserReceiptsRepository>() }
        singleOf(::ExposedRoomStateRepository) { bind<RoomStateRepository>() }
        singleOf(::ExposedTimelineEventRepository) { bind<TimelineEventRepository>() }
        singleOf(::ExposedTimelineEventRelationRepository) { bind<TimelineEventRelationRepository>() }
        singleOf(::ExposedRoomOutboxMessageRepository) { bind<RoomOutboxMessageRepository>() }
        singleOf(::ExposedMediaCacheMappingRepository) { bind<MediaCacheMappingRepository>() }
        singleOf(::ExposedGlobalAccountDataRepository) { bind<GlobalAccountDataRepository>() }
        singleOf(::ExposedRoomAccountDataRepository) { bind<RoomAccountDataRepository>() }
    }
}
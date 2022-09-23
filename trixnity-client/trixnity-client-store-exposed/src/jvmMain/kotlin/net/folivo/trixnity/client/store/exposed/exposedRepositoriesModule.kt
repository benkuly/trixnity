package net.folivo.trixnity.client.store.exposed

import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import net.folivo.trixnity.client.store.repository.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

private val log = KotlinLogging.logger {}

suspend fun createExposedRepositoriesModule(
    database: Database,
    transactionContext: CoroutineContext = Dispatchers.IO
): Module {
    log.debug { "create missing tables and columns" }
    newSuspendedTransaction(transactionContext, database) {
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
            ExposedMedia,
            ExposedOlmAccount,
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
            ExposedUploadMedia,
        )
        SchemaUtils.createMissingTablesAndColumns(*allTables)
    }
    log.debug { "finished create missing tables and columns" }
    return module {
        single<RepositoryTransactionManager> { ExposedRepositoryTransactionManager(database, transactionContext) }
        singleOf(::ExposedAccountRepository) { bind<AccountRepository>() }
        singleOf(::ExposedOutdatedKeysRepository) { bind<OutdatedKeysRepository>() }
        singleOf(::ExposedDeviceKeysRepository) { bind<DeviceKeysRepository>() }
        singleOf(::ExposedCrossSigningKeysRepository) { bind<CrossSigningKeysRepository>() }
        singleOf(::ExposedKeyVerificationStateRepository) { bind<KeyVerificationStateRepository>() }
        singleOf(::ExposedKeyChainLinkRepository) { bind<KeyChainLinkRepository>() }
        singleOf(::ExposedSecretsRepository) { bind<SecretsRepository>() }
        singleOf(::ExposedSecretKeyRequestRepository) { bind<SecretKeyRequestRepository>() }
        singleOf(::ExposedOlmAccountRepository) { bind<OlmAccountRepository>() }
        singleOf(::ExposedOlmSessionRepository) { bind<OlmSessionRepository>() }
        singleOf(::ExposedInboundMegolmSessionRepository) { bind<InboundMegolmSessionRepository>() }
        singleOf(::ExposedInboundMegolmMessageIndexRepository) { bind<InboundMegolmMessageIndexRepository>() }
        singleOf(::ExposedOutboundMegolmSessionRepository) { bind<OutboundMegolmSessionRepository>() }
        singleOf(::ExposedRoomRepository) { bind<RoomRepository>() }
        singleOf(::ExposedRoomUserRepository) { bind<RoomUserRepository>() }
        singleOf(::ExposedRoomStateRepository) { bind<RoomStateRepository>() }
        singleOf(::ExposedTimelineEventRepository) { bind<TimelineEventRepository>() }
        singleOf(::ExposedTimelineEventRelationRepository) { bind<TimelineEventRelationRepository>() }
        singleOf(::ExposedRoomOutboxMessageRepository) { bind<RoomOutboxMessageRepository>() }
        singleOf(::ExposedMediaRepository) { bind<MediaRepository>() }
        singleOf(::ExposedUploadMediaRepository) { bind<UploadMediaRepository>() }
        singleOf(::ExposedGlobalAccountDataRepository) { bind<GlobalAccountDataRepository>() }
        singleOf(::ExposedRoomAccountDataRepository) { bind<RoomAccountDataRepository>() }
    }
}
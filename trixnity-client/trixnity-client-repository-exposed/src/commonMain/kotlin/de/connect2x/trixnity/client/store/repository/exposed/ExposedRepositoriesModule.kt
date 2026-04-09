package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.AccountRepository
import de.connect2x.trixnity.client.store.repository.AuthenticationRepository
import de.connect2x.trixnity.client.store.repository.CrossSigningKeysRepository
import de.connect2x.trixnity.client.store.repository.DeviceKeysRepository
import de.connect2x.trixnity.client.store.repository.GlobalAccountDataRepository
import de.connect2x.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.KeyChainLinkRepository
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateRepository
import de.connect2x.trixnity.client.store.repository.MediaCacheMappingRepository
import de.connect2x.trixnity.client.store.repository.MigrationRepository
import de.connect2x.trixnity.client.store.repository.NotificationRepository
import de.connect2x.trixnity.client.store.repository.NotificationStateRepository
import de.connect2x.trixnity.client.store.repository.NotificationUpdateRepository
import de.connect2x.trixnity.client.store.repository.OlmAccountRepository
import de.connect2x.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import de.connect2x.trixnity.client.store.repository.OlmSessionRepository
import de.connect2x.trixnity.client.store.repository.OutboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.OutdatedKeysRepository
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import de.connect2x.trixnity.client.store.repository.RoomAccountDataRepository
import de.connect2x.trixnity.client.store.repository.RoomKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.RoomRepository
import de.connect2x.trixnity.client.store.repository.RoomStateRepository
import de.connect2x.trixnity.client.store.repository.RoomUserReceiptsRepository
import de.connect2x.trixnity.client.store.repository.RoomUserRepository
import de.connect2x.trixnity.client.store.repository.SecretKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.SecretsRepository
import de.connect2x.trixnity.client.store.repository.ServerDataRepository
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationRepository
import de.connect2x.trixnity.client.store.repository.TimelineEventRepository
import de.connect2x.trixnity.client.store.repository.UserPresenceRepository
import de.connect2x.trixnity.core.MSC4354
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private val log =
    Logger("de.connect2x.trixnity.client.store.repository.exposed.ExposedRepositoriesModule")

fun RepositoriesModule.Companion.exposed(database: Database): RepositoriesModule = RepositoriesModule {
    log.debug { "create missing tables and columns" }
    newSuspendedTransaction(Dispatchers.IO, database) {
        val allTables = arrayOf(
            ExposedAccount,
            ExposedAuthentication,
            ExposedServerData,
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
            ExposedUserPresence,
            ExposedNotification,
            ExposedNotificationState,
            ExposedNotificationUpdate,
            ExposedMigration,
            @OptIn(MSC4354::class)
            ExposedStickyEvent,
        )
        @Suppress("DEPRECATION")
        SchemaUtils.createMissingTablesAndColumns(*allTables)
    }
    log.debug { "finished create missing tables and columns" }
    module {
        single { database }
        singleOf(::ExposedRepositoryTransactionManager) { bind<RepositoryTransactionManager>() }
        singleOf(::ExposedAccountRepository) { bind<AccountRepository>() }
        singleOf(::ExposedAuthenticationRepository) { bind<AuthenticationRepository>() }
        singleOf(::ExposedServerDataRepository) { bind<ServerDataRepository>() }
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
        singleOf(::ExposedUserPresenceRepository) { bind<UserPresenceRepository>() }
        singleOf(::ExposedNotificationRepository) { bind<NotificationRepository>() }
        singleOf(::ExposedNotificationStateRepository) { bind<NotificationStateRepository>() }
        singleOf(::ExposedNotificationUpdateRepository) { bind<NotificationUpdateRepository>() }
        singleOf(::ExposedMigrationRepository) { bind<MigrationRepository>() }
        @OptIn(MSC4354::class)
        singleOf(::ExposedStickyEventRepository) { bind<StickyEventRepository>() }
    }
}

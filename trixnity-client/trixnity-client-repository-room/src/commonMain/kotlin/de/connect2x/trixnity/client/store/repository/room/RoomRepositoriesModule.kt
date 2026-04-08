package de.connect2x.trixnity.client.store.repository.room

import androidx.room.RoomDatabase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun RepositoriesModule.Companion.room(databaseBuilder: RoomDatabase.Builder<TrixnityRoomDatabase>): RepositoriesModule =
    RepositoriesModule {
        val database = databaseBuilder.build()
        module {
            single { database }
            single(createdAtStart = true) {
                get<CoroutineScope>().coroutineContext.job.invokeOnCompletion {
                    database.close()
                }
            }

            singleOf(::RoomAccountRepository) { bind<AccountRepository>() }
            singleOf(::RoomAuthenticationRepository) { bind<AuthenticationRepository>() }
            singleOf(::RoomServerDataRepository) { bind<ServerDataRepository>() }
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
            singleOf(::RoomRoomUserReceiptsRepository) { bind<RoomUserReceiptsRepository>() }
            singleOf(::RoomSecretKeyRequestRepository) { bind<SecretKeyRequestRepository>() }
            singleOf(::RoomSecretsRepository) { bind<SecretsRepository>() }
            singleOf(::RoomTimelineEventRelationRepository) { bind<TimelineEventRelationRepository>() }
            singleOf(::RoomTimelineEventRepository) { bind<TimelineEventRepository>() }
            singleOf(::RoomUserPresenceRepository) { bind<UserPresenceRepository>() }
            singleOf(::RoomNotificationRepository) { bind<NotificationRepository>() }
            singleOf(::RoomNotificationStateRepository) { bind<NotificationStateRepository>() }
            singleOf(::RoomNotificationUpdateRepository) { bind<NotificationUpdateRepository>() }
            singleOf(::RoomMigrationRepository) { bind<MigrationRepository>() }
            @OptIn(MSC4354::class)
            singleOf(::RoomStickyEventRepository) { bind<StickyEventRepository>() }
        }
    }

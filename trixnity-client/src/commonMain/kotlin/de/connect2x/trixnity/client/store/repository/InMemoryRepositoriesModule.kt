package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.RepositoriesModule
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun RepositoriesModule.Companion.inMemory() = RepositoriesModule {
    module {
        single<RepositoryTransactionManager> { NoOpRepositoryTransactionManager }

        singleOf<AccountRepository>(::InMemoryAccountRepository)
        singleOf<AuthenticationRepository>(::InMemoryAuthenticationRepository)
        singleOf<ServerDataRepository>(::InMemoryServerDataRepository)
        singleOf<OutdatedKeysRepository>(::InMemoryOutdatedKeysRepository)
        singleOf<DeviceKeysRepository>(::InMemoryDeviceKeysRepository)
        singleOf<CrossSigningKeysRepository>(::InMemoryCrossSigningKeysRepository)
        singleOf<KeyVerificationStateRepository>(::InMemoryKeyVerificationStateRepository)
        singleOf<KeyChainLinkRepository>(::InMemoryKeyChainLinkRepository)
        singleOf<SecretsRepository>(::InMemorySecretsRepository)
        singleOf<SecretKeyRequestRepository>(::InMemorySecretKeyRequestRepository)
        singleOf<RoomKeyRequestRepository>(::InMemoryRoomKeyRequestRepository)
        singleOf<OlmAccountRepository>(::InMemoryOlmAccountRepository)
        singleOf<OlmForgetFallbackKeyAfterRepository>(::InMemoryOlmForgetFallbackKeyAfterRepository)
        singleOf<OlmSessionRepository>(::InMemoryOlmSessionRepository)
        singleOf<InboundMegolmSessionRepository>(::InMemoryInboundMegolmSessionRepository)
        singleOf<InboundMegolmMessageIndexRepository>(::InMemoryInboundMegolmMessageIndexRepository)
        singleOf<OutboundMegolmSessionRepository>(::InMemoryOutboundMegolmSessionRepository)
        singleOf<RoomRepository>(::InMemoryRoomRepository)
        singleOf<RoomUserRepository>(::InMemoryRoomUserRepository)
        singleOf<RoomUserReceiptsRepository>(::InMemoryRoomUserReceiptsRepository)
        singleOf<RoomStateRepository>(::InMemoryRoomStateRepository)
        singleOf<TimelineEventRepository>(::InMemoryTimelineEventRepository)
        singleOf<TimelineEventRelationRepository>(::InMemoryTimelineEventRelationRepository)
        singleOf<RoomOutboxMessageRepository>(::InMemoryRoomOutboxMessageRepository)
        singleOf<MediaCacheMappingRepository>(::InMemoryMediaCacheMappingRepository)
        singleOf<GlobalAccountDataRepository>(::InMemoryGlobalAccountDataRepository)
        singleOf<RoomAccountDataRepository>(::InMemoryRoomAccountDataRepository)
        singleOf<UserPresenceRepository>(::InMemoryUserPresenceRepository)
        singleOf<NotificationRepository>(::InMemoryNotificationRepository)
        singleOf<NotificationUpdateRepository>(::InMemoryNotificationUpdateRepository)
        singleOf<NotificationStateRepository>(::InMemoryNotificationStateRepository)
        singleOf<MigrationRepository>(::InMemoryMigrationRepository)
    }
}
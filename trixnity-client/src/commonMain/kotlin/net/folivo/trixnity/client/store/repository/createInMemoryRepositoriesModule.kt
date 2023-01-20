package net.folivo.trixnity.client.store.repository

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createInMemoryRepositoriesModule() = module {
    single<RepositoryTransactionManager> { NoOpRepositoryTransactionManager }

    singleOf<AccountRepository>(::InMemoryAccountRepository)
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
    singleOf<RoomStateRepository>(::InMemoryRoomStateRepository)
    singleOf<TimelineEventRepository>(::InMemoryTimelineEventRepository)
    singleOf<TimelineEventRelationRepository>(::InMemoryTimelineEventRelationRepository)
    singleOf<RoomOutboxMessageRepository>(::InMemoryRoomOutboxMessageRepository)
    singleOf<MediaCacheMappingRepository>(::InMemoryMediaCacheMappingRepository)
    singleOf<GlobalAccountDataRepository>(::InMemoryGlobalAccountDataRepository)
    singleOf<RoomAccountDataRepository>(::InMemoryRoomAccountDataRepository)
}
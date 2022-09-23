package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.NoOpRepositoryTransactionManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createInMemoryRepositoriesModule() = module {
    singleOf<RepositoryTransactionManager>(::NoOpRepositoryTransactionManager)

    singleOf<AccountRepository>(::InMemoryAccountRepository)
    singleOf<OutdatedKeysRepository>(::InMemoryOutdatedKeysRepository)
    singleOf<DeviceKeysRepository>(::InMemoryDeviceKeysRepository)
    singleOf<CrossSigningKeysRepository>(::InMemoryCrossSigningKeysRepository)
    singleOf<KeyVerificationStateRepository>(::InMemoryKeyVerificationStateRepository)
    singleOf<KeyChainLinkRepository>(::InMemoryKeyChainLinkRepository)
    singleOf<SecretsRepository>(::InMemorySecretsRepository)
    singleOf<SecretKeyRequestRepository>(::InMemorySecretKeyRequestRepository)
    singleOf<OlmAccountRepository>(::InMemoryOlmAccountRepository)
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
    singleOf<MediaRepository>(::InMemoryMediaRepository)
    singleOf<UploadMediaRepository>(::InMemoryUploadMediaRepository)
    singleOf<GlobalAccountDataRepository>(::InMemoryGlobalAccountDataRepository)
    singleOf<RoomAccountDataRepository>(::InMemoryRoomAccountDataRepository)
}
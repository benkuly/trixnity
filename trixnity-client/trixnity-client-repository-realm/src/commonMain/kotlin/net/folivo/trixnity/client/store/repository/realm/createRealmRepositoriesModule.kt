package net.folivo.trixnity.client.store.repository.realm

import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private val log = KotlinLogging.logger {}

fun createRealmRepositoriesModule(
    config: RealmConfiguration.Builder.() -> Unit = {},
): Module {
    log.info { "create realm" }

    val realmConfiguration = RealmConfiguration.Builder(
        schema = setOf(
            RealmAccount::class,
            RealmCrossSigningKeys::class,
            RealmDeviceKeys::class,
            RealmGlobalAccountData::class,
            RealmInboundMegolmMessageIndex::class,
            RealmInboundMegolmSession::class,
            RealmKeyChainLink::class,
            RealmKeyVerificationState::class,
            RealmOlmAccount::class,
            RealmOlmForgetFallbackKeyAfter::class,
            RealmOlmSession::class,
            RealmOutboundMegolmSession::class,
            RealmOutdatedKeys::class,
            RealmRoom::class,
            RealmRoomAccountData::class,
            RealmRoomKeyRequest::class,
            RealmRoomOutboxMessage::class,
            RealmRoomState::class,
            RealmRoomUser::class,
            RealmSecretKeyRequest::class,
            RealmSecrets::class,
            RealmTimelineEvent::class,
            RealmTimelineEventRelation::class,
            RealmMediaCacheMapping::class,
        )
    ).apply {
        config()
    }.build()
    val realm = Realm.open(realmConfiguration)
    log.debug { "created realm" }

    return module {
        single { realm }
        singleOf(::RealmRepositoryTransactionManager) { bind<RepositoryTransactionManager>() }
        singleOf(::RealmAccountRepository) { bind<AccountRepository>() }
        singleOf(::RealmOutdatedKeysRepository) { bind<OutdatedKeysRepository>() }
        singleOf(::RealmDeviceKeysRepository) { bind<DeviceKeysRepository>() }
        singleOf(::RealmCrossSigningKeysRepository) { bind<CrossSigningKeysRepository>() }
        singleOf(::RealmKeyVerificationStateRepository) { bind<KeyVerificationStateRepository>() }
        singleOf(::RealmKeyChainLinkRepository) { bind<KeyChainLinkRepository>() }
        singleOf(::RealmSecretsRepository) { bind<SecretsRepository>() }
        singleOf(::RealmSecretKeyRequestRepository) { bind<SecretKeyRequestRepository>() }
        singleOf(::RealmRoomKeyRequestRepository) { bind<RoomKeyRequestRepository>() }
        singleOf(::RealmOlmAccountRepository) { bind<OlmAccountRepository>() }
        singleOf(::RealmOlmForgetFallbackKeyAfterRepository) { bind<OlmForgetFallbackKeyAfterRepository>() }
        singleOf(::RealmOlmSessionRepository) { bind<OlmSessionRepository>() }
        singleOf(::RealmInboundMegolmSessionRepository) { bind<InboundMegolmSessionRepository>() }
        singleOf(::RealmInboundMegolmMessageIndexRepository) { bind<InboundMegolmMessageIndexRepository>() }
        singleOf(::RealmOutboundMegolmSessionRepository) { bind<OutboundMegolmSessionRepository>() }
        singleOf(::RealmRoomRepository) { bind<RoomRepository>() }
        singleOf(::RealmRoomUserRepository) { bind<RoomUserRepository>() }
        singleOf(::RealmRoomStateRepository) { bind<RoomStateRepository>() }
        singleOf(::RealmTimelineEventRepository) { bind<TimelineEventRepository>() }
        singleOf(::RealmTimelineEventRelationRepository) { bind<TimelineEventRelationRepository>() }
        singleOf(::RealmRoomOutboxMessageRepository) { bind<RoomOutboxMessageRepository>() }
        singleOf(::RealmMediaCacheMappingRepository) { bind<MediaCacheMappingRepository>() }
        singleOf(::RealmGlobalAccountDataRepository) { bind<GlobalAccountDataRepository>() }
        singleOf(::RealmRoomAccountDataRepository) { bind<RoomAccountDataRepository>() }
    }
}
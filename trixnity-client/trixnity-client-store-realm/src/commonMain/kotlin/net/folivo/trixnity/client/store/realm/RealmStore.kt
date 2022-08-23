package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

class RealmStore(
    realm: Realm,
    contentMappings: EventContentSerializerMappings,
    json: Json,
    scope: CoroutineScope,
) : Store(
    scope = scope,
    contentMappings = contentMappings,
    rtm = object : RepositoryTransactionManager {
        override suspend fun <T> transaction(block: suspend () -> T): T {
            // FIXME is this right?
//            return realm.write {
//            runBlocking {
            return block()
//            }
//            }
        }
    },
    accountRepository = RealmAccountRepository(realm),
    crossSigningKeysRepository = RealmCrossSigningKeysRepository(realm, json),
    deviceKeysRepository = RealmDeviceKeysRepository(realm, json),
    globalAccountDataRepository = RealmGlobalAccountDataRepository(realm, json),
    inboundMegolmMessageIndexRepository = RealmInboundMegolmMessageIndexRepository(realm),
    inboundMegolmSessionRepository = RealmInboundMegolmSessionRepository(realm, json),
    keyChainLinkRepository = RealmKeyChainLinkRepository(realm),
    keyVerificationStateRepository = RealmKeyVerificationStateRepository(realm, json),
    mediaRepository = RealmMediaRepository(realm),
    olmAccountRepository = RealmOlmAccountRepository(realm),
    olmSessionRepository = RealmOlmSessionRepository(realm, json),
    outboundMegolmSessionRepository = RealmOutboundMegolmSessionRepository(realm, json),
    outdatedKeysRepository = RealmOutdatedKeysRepository(realm, json),
    roomAccountDataRepository = RealmRoomAccountDataRepository(realm, json),
    roomOutboxMessageRepository = RealmRoomOutboxMessageRepository(realm, json, contentMappings),
    roomRepository = RealmRoomRepository(realm, json),
    roomStateRepository = RealmRoomStateRepository(realm, json),
    roomUserRepository = RealmRoomUserRepository(realm, json),
    secretKeyRequestRepository = RealmSecretKeyRequestRepository(realm, json),
    secretsRepository = RealmSecretsRepository(realm, json),
    timelineEventRelationRepository = RealmTimelineEventRelationRepository(realm),
    timelineEventRepository = RealmTimelineEventRepository(realm, json),
    uploadMediaRepository = RealmUploadMediaRepository(realm),
)
package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

private val log = KotlinLogging.logger { }

class RealmStoreFactory(
    private val scope: CoroutineScope,
    private val config: RealmConfiguration.Builder.() -> Unit = {},
) : StoreFactory {
    override suspend fun createStore(contentMappings: EventContentSerializerMappings, json: Json): Store {
        log.info { "create RealmStore" }

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
                RealmMedia::class,
                RealmOlmAccount::class,
                RealmOlmSession::class,
                RealmOutboundMegolmSession::class,
                RealmOutdatedKeys::class,
                RealmRoom::class,
                RealmRoomAccountData::class,
                RealmRoomOutboxMessage::class,
                RealmRoomState::class,
                RealmRoomUser::class,
                RealmSecretKeyRequest::class,
                RealmSecrets::class,
                RealmTimelineEvent::class,
                RealmTimelineEventRelation::class,
                RealmUploadMedia::class,
            )
        ).apply {
            deleteRealmIfMigrationNeeded()
            config
        }.build()
        val realm = Realm.open(realmConfiguration)
        log.debug { "realm db path: ${realm.configuration.path}" }

        return RealmStore(
            realm = realm,
            contentMappings = contentMappings,
            json = json,
            scope = scope,
        )
    }
}
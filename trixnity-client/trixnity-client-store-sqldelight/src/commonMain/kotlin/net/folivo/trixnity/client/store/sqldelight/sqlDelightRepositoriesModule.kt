package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

private val log = KotlinLogging.logger {}

fun createSqlDelightRepositoriesModule(
    driver: SqlDriver,
    json: Json,
    contentMappings: EventContentSerializerMappings,
    databaseCoroutineContext: CoroutineContext,
    blockingTransactionCoroutineContext: CoroutineContext,
): Module {
    log.debug { "create schema version table, if it does not exists" }
    driver.execute(
        null, """
            CREATE TABLE IF NOT EXISTS schema_version (
                id INTEGER PRIMARY KEY NOT NULL,
                version INTEGER NOT NULL
            );
        """.trimIndent(), 0
    )
    log.debug { "get schema version" }
    val currentVersion = driver.executeQuery(
        null, """
            SELECT version FROM schema_version
            WHERE id = 1;
        """.trimIndent(), 0
    ).let {
        if (it.next()) it.getLong(0)
        else null
    }?.toInt()

    if (currentVersion == null) {
        log.debug { "create new schema" }
        Database.Schema.create(driver)
    } else {
        log.debug { "start migration from schema version $currentVersion to ${Database.Schema.version}" }
        Database.Schema.migrate(driver, currentVersion, Database.Schema.version)
    }

    log.debug { "save new schema version ${Database.Schema.version}" }
    driver.execute(
        null, """
            INSERT OR REPLACE INTO schema_version
            VALUES (1,${Database.Schema.version});
        """.trimIndent(), 0
    )

    log.debug { "create database and store from driver" }
    val db = Database(driver)
    return module {
        single<RepositoryTransactionManager> {
            SqlDelightRepositoriesTransactionManager(db, blockingTransactionCoroutineContext)
        }
        single<AccountRepository> {
            SqlDelightAccountRepository(db.accountQueries, databaseCoroutineContext)
        }
        single<OutdatedKeysRepository> {
            SqlDelightOutdatedDeviceKeysRepository(db.keysQueries, json, databaseCoroutineContext)
        }
        single<DeviceKeysRepository> {
            SqlDelightDeviceKeysRepository(db.keysQueries, json, databaseCoroutineContext)
        }
        single<CrossSigningKeysRepository> {
            SqlDelightCrossSigningKeysRepository(db.keysQueries, json, databaseCoroutineContext)
        }
        single<KeyVerificationStateRepository> {
            SqlDelightKeyVerificationStateRepository(db.keysQueries, json, databaseCoroutineContext)
        }
        single<KeyChainLinkRepository> {
            SqlDelightKeyChainLinkRepository(db.keysQueries, databaseCoroutineContext)
        }
        single<SecretsRepository> {
            SqlDelightSecretsRepository(db.keysQueries, json, databaseCoroutineContext)
        }
        single<SecretKeyRequestRepository> {
            SqlDelightSecretKeyRequestRepository(db.keysQueries, json, databaseCoroutineContext)
        }
        single<OlmAccountRepository> {
            SqlDelightOlmAccountRepository(db.olmQueries, databaseCoroutineContext)
        }
        single<OlmSessionRepository> {
            SqlDelightOlmSessionRepository(db.olmQueries, json, databaseCoroutineContext)
        }
        single<InboundMegolmSessionRepository> {
            SqlDelightInboundMegolmSessionRepository(db.olmQueries, json, databaseCoroutineContext)
        }
        single<InboundMegolmMessageIndexRepository> {
            SqlDelightInboundMegolmMessageIndexRepository(db.olmQueries, databaseCoroutineContext)
        }
        single<OutboundMegolmSessionRepository> {
            SqlDelightOutboundMegolmSessionRepository(db.olmQueries, json, databaseCoroutineContext)
        }
        single<RoomRepository> {
            SqlDelightRoomRepository(db.roomQueries, json, databaseCoroutineContext)
        }
        single<RoomUserRepository> {
            SqlDelightRoomUserRepository(db.roomUserQueries, json, databaseCoroutineContext)
        }
        single<RoomStateRepository> {
            SqlDelightRoomStateRepository(db.roomStateQueries, json, databaseCoroutineContext)
        }
        single<TimelineEventRepository> {
            SqlDelightTimelineEventRepository(db.roomTimelineQueries, json, databaseCoroutineContext)
        }
        single<TimelineEventRelationRepository> {
            SqlDelightTimelineEventRelationRepository(db.roomTimelineQueries, databaseCoroutineContext)
        }
        single<RoomOutboxMessageRepository> {
            SqlDelightRoomOutboxMessageRepository(
                db.roomOutboxMessageQueries, json, contentMappings, databaseCoroutineContext
            )
        }
        single<MediaRepository> {
            SqlDelightMediaRepository(db.mediaQueries, databaseCoroutineContext)
        }
        single<UploadMediaRepository> {
            SqlDelightUploadMediaRepository(db.mediaQueries, databaseCoroutineContext)
        }
        single<GlobalAccountDataRepository> {
            SqlDelightGlobalAccountDataRepository(db.globalAccountDataQueries, json, databaseCoroutineContext)
        }
        single<RoomAccountDataRepository> {
            SqlDelightRoomAccountDataRepository(db.roomAccountDataQueries, json, databaseCoroutineContext)
        }
    }
}
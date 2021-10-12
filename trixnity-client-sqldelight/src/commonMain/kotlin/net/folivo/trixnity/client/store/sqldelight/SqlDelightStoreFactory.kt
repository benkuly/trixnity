package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.coroutines.CoroutineContext

class SqlDelightStoreFactory(
    private val driver: SqlDriver,
    private val databaseCoroutineContext: CoroutineContext,
) : StoreFactory {

    override suspend fun createStore(
        contentMappings: EventContentSerializerMappings,
        json: Json,
        storeCoroutineContext: CoroutineContext,
        loggerFactory: LoggerFactory
    ): Store {
        val log = newLogger(loggerFactory)

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
        val database = Database(driver)

        return Store(
            scope = CoroutineScope(storeCoroutineContext),
            accountRepository = SqlDelightAccountRepository(database.accountQueries, databaseCoroutineContext),
            outdatedDeviceKeysRepository = SqlDelightOutdatedDeviceKeysRepository(
                database.deviceKeysQueries, json, databaseCoroutineContext
            ),
            deviceKeysRepository = SqlDelightDeviceKeysRepository(
                database.deviceKeysQueries, json, databaseCoroutineContext
            ),
            olmAccountRepository = SqlDelightOlmAccountRepository(database.olmQueries, databaseCoroutineContext),
            olmSessionRepository = SqlDelightOlmSessionRepository(database.olmQueries, json, databaseCoroutineContext),
            inboundMegolmSessionRepository = SqlDelightInboundMegolmSessionRepository(
                database.olmQueries, databaseCoroutineContext
            ),
            inboundMegolmMessageIndexRepository = SqlDelightInboundMegolmMessageIndexRepository(
                database.olmQueries, databaseCoroutineContext
            ),
            outboundMegolmSessionRepository = SqlDelightOutboundMegolmSessionRepository(
                database.olmQueries, json, databaseCoroutineContext
            ),
            roomRepository = SqlDelightRoomRepository(database.roomQueries, json, databaseCoroutineContext),
            roomUserRepository = SqlDelightRoomUserRepository(database.roomUserQueries, json, databaseCoroutineContext),
            roomStateRepository = SqlDelightRoomStateRepository(
                database.roomStateQueries,
                json,
                databaseCoroutineContext
            ),
            roomTimelineRepository = SqlDelightRoomTimelineRepository(
                database.roomTimelineQueries, json, databaseCoroutineContext
            ),
            roomOutboxMessageRepository = SqlDelightRoomOutboxMessageRepository(
                database.roomOutboxMessageQueries, json, contentMappings, databaseCoroutineContext
            ),
            mediaRepository = SqlDelightMediaRepository(database.mediaQueries, databaseCoroutineContext),
            uploadMediaRepository = SqlDelightUploadMediaRepository(database.mediaQueries, databaseCoroutineContext),
            contentMappings = contentMappings
        )
    }
}
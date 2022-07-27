package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.coroutines.CoroutineContext

private val log = KotlinLogging.logger {}

/**
 * databaseCoroutineContext and blockingTransactionCoroutineContext should each be a single threaded CoroutineContext
 */

class SqlDelightStoreFactory(
    private val driver: SqlDriver,
    private val scope: CoroutineScope,
    private val databaseCoroutineContext: CoroutineContext,
    private val blockingTransactionCoroutineContext: CoroutineContext,
) : StoreFactory {

    override suspend fun createStore(
        contentMappings: EventContentSerializerMappings,
        json: Json,
    ): Store {

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

        return SqlDelightStore(
            scope = scope,
            dbContext = databaseCoroutineContext,
            blockingTransactionCoroutineContext = blockingTransactionCoroutineContext,
            json = json,
            contentMappings = contentMappings,
            db = database
        )
    }
}
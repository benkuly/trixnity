package net.folivo.trixnity.client.store.repository.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.repository.sqldelight.testutils.createDriver
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings

class SqlDelightStoreFactoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var driver: SqlDriver
    val json = createMatrixEventJson()
    val mappings = DefaultEventContentSerializerMappings
    beforeTest {
        driver = createDriver()
    }
    afterTest {
        driver.close()
    }
    should("save schema version") {
        createSqlDelightRepositoriesModule(driver, json, mappings, Dispatchers.IO, Dispatchers.IO)
        (driver.executeQuery(
            null, """
            SELECT version FROM schema_version
            WHERE id = 1;
        """.trimIndent(), 0
        ).getLong(0) ?: 0) shouldBeGreaterThan 0
    }
    should("handle existing database") {
        createSqlDelightRepositoriesModule(driver, json, mappings, Dispatchers.IO, Dispatchers.IO)
        createSqlDelightRepositoriesModule(driver, json, mappings, Dispatchers.IO, Dispatchers.IO)
    }
})
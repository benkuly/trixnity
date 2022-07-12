package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriver
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings

class SqlDelightStoreFactoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var driver: SqlDriver
    lateinit var cut: SqlDelightStoreFactory
    lateinit var scope: CoroutineScope
    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        driver = createDriver()
        cut = SqlDelightStoreFactory(driver, scope, Dispatchers.Default, Dispatchers.Default)
    }
    afterTest {
        driver.close()
        scope.cancel()
    }
    should("save schema version") {
        cut.createStore(
            DefaultEventContentSerializerMappings,
            createMatrixEventJson(),
        )
        (driver.executeQuery(
            null, """
            SELECT version FROM schema_version
            WHERE id = 1;
        """.trimIndent(), 0
        ).getLong(0) ?: 0) shouldBeGreaterThan 0
    }
    should("handle existing database") {
        cut.createStore(
            DefaultEventContentSerializerMappings,
            createMatrixEventJson(),
        )
        cut.createStore(
            DefaultEventContentSerializerMappings,
            createMatrixEventJson(),
        )
    }
})
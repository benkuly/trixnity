package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriver
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.defaultLogFrontend

class SqlDelightStoreFactoryTest : ShouldSpec({
    lateinit var driver: SqlDriver
    lateinit var cut: SqlDelightStoreFactory
    lateinit var scope: CoroutineScope
    beforeTest {
        driver = createDriver()
        scope = CoroutineScope(Dispatchers.Default)
        cut = SqlDelightStoreFactory(driver, scope, Dispatchers.Default)
    }
    afterTest {
        driver.close()
        scope.cancel()
    }
    should("save schema version") {
        cut.createStore(
            DefaultEventContentSerializerMappings,
            createMatrixJson(),
            loggerFactory = LoggerFactory(listOf(defaultLogFrontend))
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
            createMatrixJson(),
            loggerFactory = LoggerFactory(listOf(defaultLogFrontend))
        )
        cut.createStore(
            DefaultEventContentSerializerMappings,
            createMatrixJson(),
            loggerFactory = LoggerFactory(listOf(defaultLogFrontend))
        )
    }
})
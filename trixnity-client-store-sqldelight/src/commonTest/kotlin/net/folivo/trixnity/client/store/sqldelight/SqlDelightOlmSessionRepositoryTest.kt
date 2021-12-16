package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.client.store.StoredOlmSession
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.serialization.createMatrixJson

class SqlDelightOlmSessionRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightOlmSessionRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightOlmSessionRepository(Database(driver).olmQueries, createMatrixJson(), Dispatchers.Default)
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val key1 = Curve25519Key(null, "curve1")
        val key2 = Curve25519Key(null, "curve2")
        val session1 = StoredOlmSession(key1, "session1", fromEpochMilliseconds(1234), pickled = "1")
        val session2 = StoredOlmSession(key2, "session2", fromEpochMilliseconds(1234), pickled = "2")
        val session3 = StoredOlmSession(key2, "session3", fromEpochMilliseconds(1234), pickled = "2")

        cut.save(key1, setOf(session1))
        cut.save(key2, setOf(session2))
        cut.get(key1) shouldContainExactly setOf(session1)
        cut.get(key2) shouldContainExactly setOf(session2)
        cut.save(key2, setOf(session2, session3))
        cut.get(key2) shouldContainExactly setOf(session2, session3)
        cut.delete(key1)
        cut.get(key1) shouldBe null
    }
})
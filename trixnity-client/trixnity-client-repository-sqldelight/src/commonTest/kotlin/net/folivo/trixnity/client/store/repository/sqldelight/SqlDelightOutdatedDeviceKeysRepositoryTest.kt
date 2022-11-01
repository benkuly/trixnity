package net.folivo.trixnity.client.store.repository.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.repository.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class SqlDelightOutdatedDeviceKeysRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: SqlDelightOutdatedDeviceKeysRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightOutdatedDeviceKeysRepository(
            Database(driver).keysQueries,
            createMatrixEventJson(),
            Dispatchers.Default
        )
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")
        cut.save(1, setOf(alice))
        cut.get(1) shouldContainExactly setOf(alice)
        cut.save(1, setOf(alice, bob))
        cut.get(1) shouldContainExactly setOf(alice, bob)
        cut.delete(1)
        cut.get(1) shouldBe null
    }
})
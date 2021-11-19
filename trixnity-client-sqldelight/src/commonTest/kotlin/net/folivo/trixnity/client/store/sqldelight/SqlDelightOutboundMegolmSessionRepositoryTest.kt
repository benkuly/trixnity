package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.StoredOutboundMegolmSession
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createMatrixJson

class SqlDelightOutboundMegolmSessionRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightOutboundMegolmSessionRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightOutboundMegolmSessionRepository(
            Database(driver).olmQueries,
            createMatrixJson(),
            Dispatchers.Default
        )
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val session1 = StoredOutboundMegolmSession(key1, pickle = "1")
        val session2 = StoredOutboundMegolmSession(key2, pickle = "2")
        val session2Copy = session2.copy(
            newDevices = mapOf(
                UserId("bob", "server") to setOf("Device1"),
                UserId("alice", "server") to setOf("Device2", "Device3")
            )
        )

        cut.save(key1, session1)
        cut.save(key2, session2)
        cut.get(key1) shouldBe session1
        cut.get(key2) shouldBe session2
        cut.save(key2, session2Copy)
        cut.get(key2) shouldBe session2Copy
        cut.delete(key1)
        cut.get(key1) shouldBe null
    }
})
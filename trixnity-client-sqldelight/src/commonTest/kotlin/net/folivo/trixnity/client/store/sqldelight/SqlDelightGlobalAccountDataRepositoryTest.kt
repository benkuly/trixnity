package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.UnknownGlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson

class SqlDelightGlobalAccountDataRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightGlobalAccountDataRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightGlobalAccountDataRepository(
            Database(driver).globalAccountDataQueries,
            createMatrixJson(),
            Dispatchers.Default
        )
    }
    afterTest {
        driver.close()
    }

    should("save, get and delete") {
        val roomId1 = RoomId("room1", "server")
        val roomId2 = RoomId("room2", "server")
        val key1 = "m.fully_read"
        val key2 = "org.example.mynamespace"
        val accountDataEvent1 = GlobalAccountDataEvent(
            DirectEventContent(
                mapOf(
                    MatrixId.UserId(
                        "alice",
                        "server.org"
                    ) to setOf(RoomId("!room", "server"))
                )
            )
        )
        val accountDataEvent2 = GlobalAccountDataEvent(
            UnknownGlobalAccountDataEventContent(
                JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                "org.example.mynamespace"
            ),
        )
        val accountDataEvent1Copy = accountDataEvent1.copy(
            content = DirectEventContent(
                mapOf(
                    MatrixId.UserId(
                        "alice",
                        "server.org"
                    ) to null
                )
            )
        )

        cut.save(key1, accountDataEvent1)
        cut.save(key2, accountDataEvent2)
        cut.get(key1) shouldBe accountDataEvent1
        cut.get(key2) shouldBe accountDataEvent2
        cut.save(key1, accountDataEvent1Copy)
        cut.get(key1) shouldBe accountDataEvent1Copy
        cut.delete(key1)
        cut.get(key1) shouldBe null
    }
})
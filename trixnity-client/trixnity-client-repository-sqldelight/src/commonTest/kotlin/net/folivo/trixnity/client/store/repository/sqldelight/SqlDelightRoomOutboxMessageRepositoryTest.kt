package net.folivo.trixnity.client.store.repository.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.repository.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.ImageMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import kotlin.test.assertNotNull

class SqlDelightRoomOutboxMessageRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: SqlDelightRoomOutboxMessageRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightRoomOutboxMessageRepository(
            Database(driver).roomOutboxMessageQueries,
            createMatrixEventJson(),
            DefaultEventContentSerializerMappings,
            Dispatchers.Default
        )
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val roomId = RoomId("room", "server")
        val key1 = "transaction1"
        val key2 = "transaction2"
        val message1 = RoomOutboxMessage(key1, roomId, TextMessageEventContent("hi"), null)
        val message2 = RoomOutboxMessage(key2, roomId, ImageMessageEventContent("hi"), null)
        val message2Copy = message2.copy(sentAt = fromEpochMilliseconds(24))

        cut.save(key1, message1)
        cut.save(key2, message2)
        val get1 = cut.get(key1)
        assertNotNull(get1)
        get1 shouldBe message1.copy(mediaUploadProgress = get1.mediaUploadProgress)
        val get2 = cut.get(key2)
        assertNotNull(get2)
        get2 shouldBe message2.copy(mediaUploadProgress = get2.mediaUploadProgress)
        cut.save(key2, message2Copy)
        val get2Copy = cut.get(key2)
        assertNotNull(get2Copy)
        get2Copy shouldBe message2Copy.copy(mediaUploadProgress = get2Copy.mediaUploadProgress)
        cut.delete(key1)
        cut.get(key1) shouldBe null
    }
    should("get all") {
        val roomId = RoomId("room", "server")
        val key1 = "transaction1"
        val key2 = "transaction2"
        val message1 = RoomOutboxMessage(key1, roomId, TextMessageEventContent("hi"), null)
        val message2 = RoomOutboxMessage(key1, roomId, ImageMessageEventContent("hi"), null)
        cut.save(key1, message1)
        cut.save(key2, message2)
        cut.getAll() shouldHaveSize 2
    }
})
package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.ImageMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.test.assertNotNull

class ExposedRoomOutboxMessageRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedRoomOutboxMessageRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedRoomOutboxMessage)
        }
        cut = ExposedRoomOutboxMessageRepository(createMatrixJson(), DefaultEventContentSerializerMappings)
    }
    should("save, get and delete") {
        val roomId = RoomId("room", "server")
        val key1 = "transaction1"
        val key2 = "transaction2"
        val message1 = RoomOutboxMessage(key1, roomId, TextMessageEventContent("hi"), null)
        val message2 = RoomOutboxMessage(key2, roomId, ImageMessageEventContent("hi"), null)
        val message2Copy = message2.copy(sentAt = fromEpochMilliseconds(24))

        newSuspendedTransaction {
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
    }
    should("get all") {
        val roomId = RoomId("room", "server")
        val key1 = "transaction1"
        val key2 = "transaction2"
        val message1 = RoomOutboxMessage(key1, roomId, TextMessageEventContent("hi"), null)
        val message2 = RoomOutboxMessage(key1, roomId, ImageMessageEventContent("hi"), null)

        newSuspendedTransaction {
            cut.save(key1, message1)
            cut.save(key2, message2)
            cut.getAll() shouldHaveSize 2
        }
    }
})
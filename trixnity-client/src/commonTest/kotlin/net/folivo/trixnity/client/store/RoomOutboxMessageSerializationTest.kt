package net.folivo.trixnity.client.store

import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.trimToFlatJson
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class RoomOutboxMessageSerializationTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()

    private val roomOutboxMessage = RoomOutboxMessage(
        transactionId = "t1",
        roomId = RoomId("room", "server"),
        content = RoomMessageEventContent.TextBased.Text("dino"),
        createdAt = Instant.fromEpochMilliseconds(12),
        sentAt = Instant.fromEpochMilliseconds(24),
        sendError = RoomOutboxMessage.SendError.BadRequest(ErrorResponse.Forbidden("")),
        keepMediaInCache = true,
    )
    private val roomOutboxMessageJson = """
        {
            "roomId":"!room:server",
            "transactionId":"t1",
            "content":{"body":"dino","msgtype":"m.text"},
            "createdAt":"1970-01-01T00:00:00.012Z",
            "sentAt":"1970-01-01T00:00:00.024Z",
            "sendError":{"type":"bad_request","errorResponse":{"errcode":"M_FORBIDDEN","error":""}},
            "keepMediaInCache":true
        }
        """.trimToFlatJson()

    @Test
    fun `serialize RoomOutboxMessage`() = runTest {
        json.encodeToString(roomOutboxMessage) shouldBe roomOutboxMessageJson
    }

    @Test
    fun `deserialize RoomOutboxMessage`() = runTest {
        json.decodeFromString<RoomOutboxMessage<RoomMessageEventContent.TextBased.Text>>(roomOutboxMessageJson) shouldBe roomOutboxMessage
    }
}
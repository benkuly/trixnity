package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import net.folivo.trixnity.client.trimToFlatJson
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class RoomOutboxMessageSerializationTest : ShouldSpec({
    timeout = 60_000
    val json = createMatrixEventJson()

    val roomOutboxMessage = RoomOutboxMessage(
        transactionId = "t1",
        roomId = RoomId("room", "server"),
        content = RoomMessageEventContent.TextBased.Text("dino"),
        createdAt = Instant.fromEpochMilliseconds(12),
        sentAt = Instant.fromEpochMilliseconds(24),
        sendError = RoomOutboxMessage.SendError.BadRequest(ErrorResponse.Forbidden("")),
        keepMediaInCache = true,
    )
    val roomOutboxMessageJson = """
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

    should("serialize ${RoomOutboxMessage::class.simpleName}") {
        json.encodeToString(roomOutboxMessage) shouldBe roomOutboxMessageJson
    }
    should("deserialize ${RoomOutboxMessage::class.simpleName}") {
        json.decodeFromString<RoomOutboxMessage<RoomMessageEventContent.TextBased.Text>>(roomOutboxMessageJson) shouldBe roomOutboxMessage
    }
})
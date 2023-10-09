package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import net.folivo.trixnity.client.trimToFlatJson
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class RoomOutboxMessageSerializationTest : ShouldSpec({
    timeout = 60_000
    val json = createMatrixEventJson()

    val roomOutboxMessage = RoomOutboxMessage(
        transactionId = "t1",
        roomId = RoomId("room", "server"),
        content = TextMessageEventContent("dino"),
        sentAt = Instant.fromEpochMilliseconds(24),
        sendError = RoomOutboxMessage.SendError.BadRequest(ErrorResponse.Forbidden()),
        keepMediaInCache = true,
    )
    val roomOutboxMessageJson = """
        {
            "transactionId":"t1",
            "roomId":"!room:server",
            "content":{"body":"dino","msgtype":"m.text"},
            "sentAt":"1970-01-01T00:00:00.024Z",
            "sendError":{"type":"bad_request","errorResponse":{"errcode":"M_FORBIDDEN"}},
            "keepMediaInCache":true
        }
        """.trimToFlatJson()

    should("serialize ${RoomOutboxMessage::class.simpleName}") {
        json.encodeToString(roomOutboxMessage) shouldBe roomOutboxMessageJson
    }
    should("deserialize ${RoomOutboxMessage::class.simpleName}") {
        json.decodeFromString<RoomOutboxMessage<TextMessageEventContent>>(roomOutboxMessageJson) shouldBe roomOutboxMessage
    }
})
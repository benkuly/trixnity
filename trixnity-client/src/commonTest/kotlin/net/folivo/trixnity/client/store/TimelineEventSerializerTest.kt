package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.client.trimToFlatJson
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings

class TimelineEventSerializerTest : ShouldSpec({
    timeout = 10_000
    val json = createMatrixEventJson(customModule = SerializersModule {
        contextual(
            TimelineEventSerializer(
                DefaultEventContentSerializerMappings.message + DefaultEventContentSerializerMappings.state,
                true
            )
        )
    })

    fun timelineEvent(content: Result<RoomEventContent>?) =
        TimelineEvent(
            event = ClientEvent.RoomEvent.MessageEvent(
                MegolmEncryptedMessageEventContent(ciphertext = "cipher", sessionId = "sessionId"),
                EventId("$1event"),
                UserId("sender", "server"),
                RoomId("room", "server"),
                24,
            ),
            content = content,
            previousEventId = null,
            nextEventId = null,
            gap = null,
        )

    fun timelineEventJson(contentJson: String?) = """
        {
          "event":{
            "content":{
              "algorithm":"m.megolm.v1.aes-sha2",
              "ciphertext":"cipher",
              "session_id":"sessionId"
            },
            "event_id":"$1event",
            "origin_server_ts":24,
            "room_id":"!room:server",
            "sender":"@sender:server",
            "type":"m.room.encrypted"
          }
          ${if (contentJson == null) "" else ""","content":$contentJson"""}
        }
    """.trimToFlatJson()

    context("message event content") {
        val result = timelineEvent(Result.success(RoomMessageEventContent.TextBased.Text("hi")))
        val resultJson = timelineEventJson("""{"type":"m.room.message","value":{"body":"hi","msgtype":"m.text"}}""")
        should("deserialize") {
            json.decodeFromString<TimelineEvent>(resultJson) shouldBe result
        }
        should("serialize") {
            json.encodeToString(result) shouldBe resultJson
        }
    }
    context("state event content") {
        val result = timelineEvent(Result.success(NameEventContent("name")))
        val resultJson = timelineEventJson("""{"type":"m.room.name","value":{"name":"name"}}""")
        should("deserialize") {
            json.decodeFromString<TimelineEvent>(resultJson) shouldBe result
        }
        should("serialize") {
            json.encodeToString(result) shouldBe resultJson
        }
    }
    context("unknown content") {
        val result = timelineEvent(
            Result.success(
                UnknownEventContent(JsonObject(mapOf("dino" to JsonPrimitive("yeah"))), "m.dino")
            )
        )
        val resultJson = timelineEventJson("""{"type":"m.dino","value":{"dino":"yeah"}}""")
        should("deserialize") {
            json.decodeFromString<TimelineEvent>(resultJson) shouldBe result
        }
        should("serialize") {
            json.encodeToString(result) shouldBe resultJson
        }
    }
    context("redacted content") {
        val result = timelineEvent(Result.success(RedactedEventContent("m.room.message")))
        val resultJson = timelineEventJson("""{"type":"m.room.message","value":{}}""")
        should("deserialize") {
            json.decodeFromString<TimelineEvent>(resultJson) shouldBe result
        }
        should("serialize") {
            json.encodeToString(result) shouldBe resultJson
        }
    }
    context("malformed event content") {
        val result = timelineEvent(
            Result.success(
                UnknownEventContent(JsonObject(mapOf("wrong" to JsonPrimitive("name"))), "m.room.name")
            )
        )
        val resultJson = timelineEventJson("""{"type":"m.room.name","value":{"wrong":"name"}}""")
        should("deserialize") {
            json.decodeFromString<TimelineEvent>(resultJson) shouldBe result
        }
    }
    context("failure") {
        val result = timelineEvent(Result.failure(TimelineEvent.TimelineEventContentError.DecryptionTimeout))
        val resultJson = timelineEventJson(null)
        should("serialize") {
            json.encodeToString(result) shouldBe resultJson
        }
    }
    context("null") {
        val result = timelineEvent(null)
        val resultJson = timelineEventJson(null)
        should("deserialize") {
            json.decodeFromString<TimelineEvent>(resultJson) shouldBe result
        }
        should("serialize") {
            json.encodeToString(result) shouldBe resultJson
        }
    }
    context("disabled") {
        val disabledJson = createMatrixEventJson(customModule = SerializersModule {
            contextual(
                TimelineEventSerializer(
                    DefaultEventContentSerializerMappings.message + DefaultEventContentSerializerMappings.state,
                    false
                )
            )
        })
        val result = timelineEvent(Result.success(RoomMessageEventContent.TextBased.Text("hi")))
        val resultJson = timelineEventJson(null)
        should("serialize") {
            disabledJson.encodeToString(result) shouldBe resultJson
        }
    }
})
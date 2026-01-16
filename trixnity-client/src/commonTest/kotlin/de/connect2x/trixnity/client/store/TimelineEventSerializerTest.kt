package de.connect2x.trixnity.client.store

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import de.connect2x.trixnity.client.trimToFlatJson
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.model.events.block.EventContentBlock
import de.connect2x.trixnity.core.model.events.block.EventContentBlocks
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.NameEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.test.Test

class TimelineEventSerializerTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson(customModule = SerializersModule {
        contextual(
            TimelineEvent.Serializer(
                EventContentSerializerMappings.default.message + EventContentSerializerMappings.default.state,
                true
            )
        )
    })

    private fun timelineEvent(content: Result<RoomEventContent>?) =
        TimelineEvent(
            event = ClientEvent.RoomEvent.MessageEvent(
                MegolmEncryptedMessageEventContent(ciphertext = MegolmMessageValue("cipher"), sessionId = "sessionId"),
                EventId("$1event"),
                UserId("sender", "server"),
                RoomId("!room:server"),
                24,
            ),
            content = content,
            previousEventId = null,
            nextEventId = null,
            gap = null,
        )

    private fun timelineEventJson(contentJson: String?) = """
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


    private val messageResult = timelineEvent(Result.success(RoomMessageEventContent.TextBased.Text("hi")))
    private val messageResultJson =
        timelineEventJson("""{"type":"m.room.message","value":{"body":"hi","msgtype":"m.text"}}""")

    @Test
    fun `message event content » deserialize`() = runTest {
        json.decodeFromString<TimelineEvent>(messageResultJson) shouldBe messageResult
    }

    @Test
    fun `message event content » serialize`() = runTest {
        json.encodeToString(messageResult) shouldBe messageResultJson
    }


    private val stateResult = timelineEvent(Result.success(NameEventContent("name")))
    private val stateResultJson = timelineEventJson("""{"type":"m.room.name","value":{"name":"name"}}""")

    @Test
    fun `state event content » deserialize`() = runTest {
        json.decodeFromString<TimelineEvent>(stateResultJson) shouldBe stateResult
    }

    @Test
    fun `state event content » serialize`() = runTest {
        json.encodeToString(stateResult) shouldBe stateResultJson
    }


    private val unknownResult = timelineEvent(
        Result.success(
            UnknownEventContent(
                JsonObject(mapOf("dino" to JsonPrimitive("yeah"))),
                EventContentBlocks(EventContentBlock.Unknown("dino", JsonPrimitive("yeah"))),
                "m.dino"
            )
        )
    )
    private val unknownResultJson = timelineEventJson("""{"type":"m.dino","value":{"dino":"yeah"}}""")

    @Test
    fun `unknown content » deserialize`() = runTest {
        json.decodeFromString<TimelineEvent>(unknownResultJson) shouldBe unknownResult
    }

    @Test
    fun `unknown content » serialize`() = runTest {
        json.encodeToString(unknownResult) shouldBe unknownResultJson
    }

    private val redactedResult = timelineEvent(Result.success(RedactedEventContent("m.room.encrypted")))
    private val redactedResultJson = timelineEventJson("""{"type":"m.room.encrypted","value":{}}""")

    @Test
    fun `redacted content » deserialize`() = runTest {
        json.decodeFromString<TimelineEvent>(redactedResultJson) shouldBe redactedResult
    }

    @Test
    fun `redacted content » serialize`() = runTest {
        json.encodeToString(redactedResult) shouldBe redactedResultJson
    }

    private val malformedResult = timelineEvent(
        Result.success(
            UnknownEventContent(
                JsonObject(mapOf("wrong" to JsonPrimitive("name"))),
                EventContentBlocks(EventContentBlock.Unknown("wrong", JsonPrimitive("name"))),
                "m.room.name"
            )
        )
    )
    private val malformedResultJson = timelineEventJson("""{"type":"m.room.name","value":{"wrong":"name"}}""")

    @Test
    fun `malformed event content » deserialize`() = runTest {
        json.decodeFromString<TimelineEvent>(malformedResultJson) shouldBe malformedResult
    }

    private val failureResult = timelineEvent(Result.failure(TimelineEvent.TimelineEventContentError.DecryptionTimeout))
    private val failureResultJson = timelineEventJson(null)

    @Test
    fun `failure » serialize`() = runTest {
        json.encodeToString(failureResult) shouldBe failureResultJson
    }

    private val nullResult = timelineEvent(null)
    private val nullResultJson = timelineEventJson(null)

    @Test
    fun `null » deserialize`() = runTest {
        json.decodeFromString<TimelineEvent>(nullResultJson) shouldBe nullResult
    }

    @Test
    fun `null » serialize`() = runTest {
        json.encodeToString(nullResult) shouldBe nullResultJson
    }

    private val disabledJson = createMatrixEventJson(customModule = SerializersModule {
        contextual(
            TimelineEvent.Serializer(
                EventContentSerializerMappings.default.message + EventContentSerializerMappings.default.state,
                false
            )
        )
    })
    private val disabledJsonResult = timelineEvent(Result.success(RoomMessageEventContent.TextBased.Text("hi")))
    private val disabledJsonResultJson = timelineEventJson(null)

    @Test
    fun `disabled » serialize`() = runTest {
        disabledJson.encodeToString(disabledJsonResult) shouldBe disabledJsonResultJson
    }
}
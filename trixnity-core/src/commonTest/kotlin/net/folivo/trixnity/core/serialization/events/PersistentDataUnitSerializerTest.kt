package net.folivo.trixnity.core.serialization.events

import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV1
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV1.PersistentStateDataUnitV1
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV12.PersistentMessageDataUnitV12
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV3.PersistentMessageDataUnitV3
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.serialization.createMatrixDataUnitJson
import net.folivo.trixnity.core.serialization.trimToFlatJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

@OptIn(ExperimentalSerializationApi::class)
class PersistentDataUnitSerializerTest : TrixnityBaseTest() {

    private class TestRoomVersionStore(val roomVersion: String) : RoomVersionStore {
        override fun getRoomVersion(roomId: RoomId): String = roomVersion

        override fun setRoomVersion(
            pdu: PersistentDataUnit.PersistentStateDataUnit<*>,
            roomVersion: String
        ) {
        }
    }

    private val jsonV1 = createMatrixDataUnitJson(TestRoomVersionStore("1"))
    private val jsonV3 = createMatrixDataUnitJson(TestRoomVersionStore("3"))
    private val jsonV12 = createMatrixDataUnitJson(TestRoomVersionStore("12"))

    private val statePduV1 = PersistentStateDataUnitV1(
        authEvents = listOf(
            PersistentDataUnitV1.EventHashPair(
                EventId("${'$'}af232176:example.org"),
                PersistentDataUnit.EventHash("abase64encodedsha256hashshouldbe43byteslong")
            )
        ),
        content = MemberEventContent(membership = Membership.JOIN),
        depth = 12u,
        id = EventId("${'$'}a4ecee13e2accdadf56c1025:example.com"),
        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
        originTimestamp = 1404838188000,
        prevEvents = listOf(
            PersistentDataUnitV1.EventHashPair(
                EventId("${'$'}af232176:example.org"),
                PersistentDataUnit.EventHash("abase64encodedsha256hashshouldbe43byteslong")
            )
        ),
        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
        sender = UserId("@alice:example.com"),
        stateKey = "@user:server",
        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
    )

    private val statePduV1Json = """
            {
              "auth_events": [
                "${'$'}af232176:example.org",
                {
                  "sha256": "abase64encodedsha256hashshouldbe43byteslong"
                }
              ],
              "content": {
                "membership": "join"
              },
              "depth": 12,
              "event_id": "${'$'}a4ecee13e2accdadf56c1025:example.com",
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [
                "${'$'}af232176:example.org",
                {
                  "sha256": "abase64encodedsha256hashshouldbe43byteslong"
                }
              ],
              "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
              "sender": "@alice:example.com",
              "state_key": "@user:server",
              "type": "m.room.member",
              "unsigned": {
                "age": 4612
              }
            }
    """.trimToFlatJson()

    @Test
    fun shouldSerializeStateV1() {
        val serializer = requireNotNull(jsonV1.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV1.encodeToString(serializer, statePduV1) shouldBe statePduV1Json
    }

    @Test
    fun shouldDeserializeStateV1() {
        val serializer = requireNotNull(jsonV1.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV1.decodeFromString(serializer, statePduV1Json) shouldBe statePduV1
    }

    private val statePduV3 = PersistentStateDataUnitV3(
        authEvents = listOf(EventId("${'$'}base64encodedeventid"), EventId("${'$'}adifferenteventid")),
        content = MemberEventContent(membership = Membership.JOIN),
        depth = 12u,
        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
        originTimestamp = 1404838188000,
        prevEvents = listOf(EventId("${'$'}base64encodedeventid"), EventId("${'$'}adifferenteventid")),
        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
        sender = UserId("@alice:example.com"),
        stateKey = "@user:server",
        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
    )

    private val statePduV3Json = """
            {
              "auth_events": [
                "${'$'}base64encodedeventid",
                "${'$'}adifferenteventid"
              ],
              "content": {
                "membership": "join"
              },
              "depth": 12,
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [
                "${'$'}base64encodedeventid",
                "${'$'}adifferenteventid"
              ],
              "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
              "sender": "@alice:example.com",
              "state_key": "@user:server",
              "type": "m.room.member",
              "unsigned": {
                "age": 4612
              }
            }
    """.trimToFlatJson()

    @Test
    fun shouldSerializeStateV3() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.encodeToString(serializer, statePduV3) shouldBe statePduV3Json
    }

    @Test
    fun shouldDeserializeStateV3() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.decodeFromString(serializer, statePduV3Json) shouldBe statePduV3
    }

    private val statePduV12 = PersistentStateDataUnitV12(
        authEvents = listOf(EventId("${'$'}base64encodedeventid"), EventId("${'$'}adifferenteventid")),
        content = CreateEventContent(roomVersion = "12"),
        depth = 12u,
        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
        originTimestamp = 1404838188000,
        prevEvents = listOf(EventId("${'$'}base64encodedeventid"), EventId("${'$'}adifferenteventid")),
        roomId = null,
        sender = UserId("@alice:example.com"),
        stateKey = "@user:server",
        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
    )

    private val statePduV12Json = """
            {
              "auth_events": [
                "${'$'}base64encodedeventid",
                "${'$'}adifferenteventid"
              ],
              "content": {"room_version": "12"},
              "depth": 12,
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [
                "${'$'}base64encodedeventid",
                "${'$'}adifferenteventid"
              ],
              "sender": "@alice:example.com",
              "state_key": "@user:server",
              "type": "m.room.create",
              "unsigned": {
                "age": 4612
              }
            }
    """.trimToFlatJson()

    @Test
    fun shouldSerializeStateV12() {
        val serializer = requireNotNull(jsonV12.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV12.encodeToString(serializer, statePduV12) shouldBe statePduV12Json
    }

    @Test
    fun shouldDeserializeStateV12() {
        val serializer = requireNotNull(jsonV12.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV12.decodeFromString(serializer, statePduV12Json) shouldBe statePduV12
    }

    private val messagePduV1 = PersistentDataUnitV1.PersistentMessageDataUnitV1(
        authEvents = listOf(
            PersistentDataUnitV1.EventHashPair(
                EventId("${'$'}af232176:example.org"),
                PersistentDataUnit.EventHash("abase64encodedsha256hashshouldbe43byteslong")
            )
        ),
        content = RoomMessageEventContent.TextBased.Text("hi"),
        depth = 12u,
        id = EventId("${'$'}a4ecee13e2accdadf56c1025:example.com"),
        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
        originTimestamp = 1404838188000,
        prevEvents = listOf(
            PersistentDataUnitV1.EventHashPair(
                EventId("${'$'}af232176:example.org"),
                PersistentDataUnit.EventHash("abase64encodedsha256hashshouldbe43byteslong")
            )
        ),
        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
        sender = UserId("@alice:example.com"),
        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
    )

    private val messagePduV1Json = """
            {
              "auth_events": [
                "${'$'}af232176:example.org",
                {
                  "sha256": "abase64encodedsha256hashshouldbe43byteslong"
                }
              ],
              "content": {
                "body": "hi",
                "msgtype": "m.text"
              },
              "depth": 12,
              "event_id": "${'$'}a4ecee13e2accdadf56c1025:example.com",
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [
                "${'$'}af232176:example.org",
                {
                  "sha256": "abase64encodedsha256hashshouldbe43byteslong"
                }
              ],
              "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
              "sender": "@alice:example.com",
              "type": "m.room.message",
              "unsigned": {
                "age": 4612
              }
            }
    """.trimToFlatJson()

    @Test
    fun shouldSerializeMessageV1() {
        val serializer = requireNotNull(jsonV1.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV1.encodeToString(serializer, messagePduV1) shouldBe messagePduV1Json
    }

    @Test
    fun shouldDeserializeMessageV1() {
        val serializer = requireNotNull(jsonV1.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV1.decodeFromString(serializer, messagePduV1Json) shouldBe messagePduV1
    }

    private val messagePduV3 = PersistentMessageDataUnitV3(
        authEvents = listOf(EventId("${'$'}base64encodedeventid"), EventId("${'$'}adifferenteventid")),
        content = RoomMessageEventContent.TextBased.Text("hi"),
        depth = 12u,
        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
        originTimestamp = 1404838188000,
        prevEvents = listOf(EventId("${'$'}base64encodedeventid"), EventId("${'$'}adifferenteventid")),
        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
        sender = UserId("@alice:example.com"),
        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
    )

    private val messagePduV3Json = """
            {
              "auth_events": [
                "${'$'}base64encodedeventid",
                "${'$'}adifferenteventid"
              ],
              "content": {
                "body": "hi",
                "msgtype": "m.text"
              },
              "depth": 12,
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [
                "${'$'}base64encodedeventid",
                "${'$'}adifferenteventid"
              ],
              "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
              "sender": "@alice:example.com",
              "type": "m.room.message",
              "unsigned": {
                "age": 4612
              }
            }
    """.trimToFlatJson()

    @Test
    fun shouldSerializeMessageV3() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.encodeToString(serializer, messagePduV3) shouldBe messagePduV3Json
    }

    @Test
    fun shouldDeserializeMessageV3() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.decodeFromString(serializer, messagePduV3Json) shouldBe messagePduV3
    }

    private val messagePduV12 = PersistentMessageDataUnitV12(
        authEvents = listOf(EventId("${'$'}base64encodedeventid"), EventId("${'$'}adifferenteventid")),
        content = RoomMessageEventContent.TextBased.Text("hi"),
        depth = 12u,
        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
        originTimestamp = 1404838188000,
        prevEvents = listOf(EventId("${'$'}base64encodedeventid"), EventId("${'$'}adifferenteventid")),
        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
        sender = UserId("@alice:example.com"),
        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
    )

    private val messagePduV12Json = """
            {
              "auth_events": [
                "${'$'}base64encodedeventid",
                "${'$'}adifferenteventid"
              ],
              "content": {
                "body": "hi",
                "msgtype": "m.text"
              },
              "depth": 12,
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [
                "${'$'}base64encodedeventid",
                "${'$'}adifferenteventid"
              ],
              "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
              "sender": "@alice:example.com",
              "type": "m.room.message",
              "unsigned": {
                "age": 4612
              }
            }
    """.trimToFlatJson()

    @Test
    fun shouldSerializeMessageV12() {
        val serializer = requireNotNull(jsonV12.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV12.encodeToString(serializer, messagePduV12) shouldBe messagePduV12Json
    }

    @Test
    fun shouldDeserializeMessageV12() {
        val serializer = requireNotNull(jsonV12.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV12.decodeFromString(serializer, messagePduV12Json) shouldBe messagePduV12
    }

    @Test
    fun shouldDeserializeUnknownPdu() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        val input = """
            {
              "auth_events": [],
              "content": {
                "dino": "unicorn"
              },
              "depth": 12,
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [],
              "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
              "sender": "@alice:example.com",
              "type": "o",
              "unsigned": {
                "age": 4612
              }
            }
        """.trimIndent()
        jsonV3.decodeFromString(serializer, input) shouldBe PersistentMessageDataUnitV3(
            authEvents = listOf(),
            content = UnknownEventContent(buildJsonObject { put("dino", JsonPrimitive("unicorn")) }, "o"),
            depth = 12u,
            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
            originTimestamp = 1404838188000,
            prevEvents = listOf(),
            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
            sender = UserId("@alice:example.com"),
            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
        )
    }

    private val redactionPduJson = """
            {
              "auth_events": [],
              "content": {
                "reason": "spam",
                "redacts": "$1event"
              },
              "depth": 12,
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [],
              "redacts": "$1event",
              "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
              "sender": "@alice:example.com",
              "type": "m.room.redaction",
              "unsigned": {
                "age": 4612
              }
            }
        """.trimToFlatJson()

    private val redactionPdu = PersistentMessageDataUnitV3(
        authEvents = listOf(),
        content = RedactionEventContent(EventId("$1event"), "spam"),
        depth = 12u,
        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
        originTimestamp = 1404838188000,
        prevEvents = listOf(),
        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
        sender = UserId("@alice:example.com"),
        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
    )

    @Test
    fun shouldDeserializeRedactionPdu() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.decodeFromString(serializer, redactionPduJson) shouldBe redactionPdu
    }

    @Test
    fun shouldSerializeRedactionPdu() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.encodeToString(serializer, redactionPdu) shouldBe redactionPduJson
    }

    private val redactedMessagePduJson = """
            {
              "auth_events": [],
              "content": {},
              "depth": 12,
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [],
              "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
              "sender": "@alice:example.com",
              "type": "m.room.message",
              "unsigned": {
                "age": 4612
              }
            }
        """.trimToFlatJson()

    private val redactedMessagePdu = PersistentMessageDataUnitV3(
        authEvents = listOf(),
        content = RedactedEventContent("m.room.message"),
        depth = 12u,
        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
        originTimestamp = 1404838188000,
        prevEvents = listOf(),
        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
        sender = UserId("@alice:example.com"),
        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
    )

    @Test
    fun shouldDeserializeRedactedMessagePdu() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.decodeFromString(serializer, redactedMessagePduJson) shouldBe redactedMessagePdu
    }

    @Test
    fun shouldSerializeRedactedMessagePdu() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.encodeToString(serializer, redactedMessagePdu) shouldBe redactedMessagePduJson
    }

    private val redactedStatePduJson = """
            {
              "auth_events": [],
              "content": {},
              "depth": 12,
              "hashes": {
                "sha256": "thishashcoversallfieldsincasethisisredacted"
              },
              "origin_server_ts": 1404838188000,
              "prev_events": [],
              "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
              "sender": "@alice:example.com",
              "state_key": "@user:server",
              "type": "m.room.name",
              "unsigned": {
                "age": 4612
              }
            }
        """.trimToFlatJson()

    private val redactedStatePdu = PersistentStateDataUnitV3(
        authEvents = listOf(),
        content = RedactedEventContent("m.room.name"),
        depth = 12u,
        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
        originTimestamp = 1404838188000,
        prevEvents = listOf(),
        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
        sender = UserId("@alice:example.com"),
        stateKey = "@user:server",
        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
    )

    @Test
    fun shouldDeserializeRedactedStatePdu() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.decodeFromString(serializer, redactedStatePduJson) shouldBe redactedStatePdu
    }

    @Test
    fun shouldSerializeRedactedStatePdu() {
        val serializer = requireNotNull(jsonV3.serializersModule.getContextual(PersistentDataUnit::class))
        jsonV3.encodeToString(serializer, redactedStatePdu) shouldBe redactedStatePduJson
    }
}
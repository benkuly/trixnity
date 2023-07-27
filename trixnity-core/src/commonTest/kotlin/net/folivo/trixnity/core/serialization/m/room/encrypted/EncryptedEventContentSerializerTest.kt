package net.folivo.trixnity.core.serialization.m.room.encrypted

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContentSerializer
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Unknown
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlin.test.Test
import kotlin.test.assertEquals

class EncryptedEventContentSerializerTest {

    private val json = createMatrixEventJson()

    @Test
    fun shouldSerializeMegolm() {
        val result = json.encodeToString(
            EncryptedEventContentSerializer,
            MegolmEncryptedEventContent(
                senderKey = Key.Curve25519Key("", "<sender_curve25519_key>"),
                deviceId = "<sender_device_id>",
                sessionId = "<outbound_group_session_id>",
                ciphertext = "<encrypted_payload_base_64>",
                relatesTo = RelatesTo.Reference(EventId("$1234"))
            )
        )
        val expectedResult = """
          {
            "ciphertext":"<encrypted_payload_base_64>",
            "sender_key":"<sender_curve25519_key>",
            "device_id":"<sender_device_id>",
            "session_id":"<outbound_group_session_id>",
            "m.relates_to":{
                "event_id":"$1234",
                "rel_type":"m.reference"
            },
            "algorithm":"m.megolm.v1.aes-sha2"
          }
        """.trimIndent().lines().joinToString("") { it.trim() }
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeMegolm() {
        val input = """
          {
            "algorithm": "m.megolm.v1.aes-sha2",
            "sender_key": "<sender_curve25519_key>",
            "device_id": "<sender_device_id>",
            "session_id": "<outbound_group_session_id>",
            "ciphertext": "<encrypted_payload_base_64>",
            "m.relates_to":{
                "event_id":"$1234",
                "rel_type":"m.reference"
            }
          }
        """.trimIndent()
        val result = json.decodeFromString<EncryptedEventContent>(input)
        assertEquals(
            MegolmEncryptedEventContent(
                senderKey = Key.Curve25519Key(null, "<sender_curve25519_key>"),
                deviceId = "<sender_device_id>",
                sessionId = "<outbound_group_session_id>",
                ciphertext = "<encrypted_payload_base_64>",
                relatesTo = RelatesTo.Reference(EventId("$1234"))
            ), result
        )
    }

    @Test
    fun shouldSerializeOlm() {
        val result = json.encodeToString(
            EncryptedEventContentSerializer,
            OlmEncryptedEventContent(
                senderKey = Key.Curve25519Key("", "<sender_curve25519_key>"),
                ciphertext = mapOf(
                    "<device_curve25519_key>" to CiphertextInfo("<encrypted_payload_base_64>", INITIAL_PRE_KEY)
                ),
                relatesTo = RelatesTo.Reference(EventId("$1234"))
            )
        )
        val expectedResult = """
            {
                "ciphertext":{
                  "<device_curve25519_key>":{
                    "body":"<encrypted_payload_base_64>",
                    "type":0
                  }
                },
                "sender_key":"<sender_curve25519_key>",
                "m.relates_to":{
                    "event_id":"$1234",
                    "rel_type":"m.reference"
                },
                "algorithm":"m.olm.v1.curve25519-aes-sha2"
            }
        """.trimIndent().lines().joinToString("") { it.trim() }
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeOlm() {
        val input = """
          {
            "algorithm": "m.olm.v1.curve25519-aes-sha2",
            "sender_key": "<sender_curve25519_key>",
            "ciphertext": {
              "<device_curve25519_key>": {
                "type": 0,
                "body": "<encrypted_payload_base_64>"
              }
            },
            "m.relates_to":{
                "rel_type":"m.reference",
                "event_id":"$1234"
            }
          }
        """.trimIndent()
        val result = json.decodeFromString<EncryptedEventContent>(input)
        assertEquals(
            OlmEncryptedEventContent(
                senderKey = Key.Curve25519Key(null, "<sender_curve25519_key>"),
                ciphertext = mapOf(
                    "<device_curve25519_key>" to CiphertextInfo("<encrypted_payload_base_64>", INITIAL_PRE_KEY)
                ),
                relatesTo = RelatesTo.Reference(EventId("$1234"))
            ), result
        )
    }

    @Test
    fun shouldDeserializeUnknown() {
        val input = """
          {
            "algorithm": "super_duper_algo",
            "ciphertext": "peng"
          }
        """.trimIndent()
        val result = json.decodeFromString<EncryptedEventContent>(input)

        assertEquals(
            UnknownEncryptedEventContent(
                algorithm = Unknown("super_duper_algo"),
                raw = buildJsonObject {
                    put("algorithm", JsonPrimitive("super_duper_algo"))
                    put("ciphertext", JsonPrimitive("peng"))
                }
            ), result
        )
    }
}
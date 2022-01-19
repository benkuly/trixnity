package net.folivo.trixnity.core.serialization.m.room.encrypted

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.*
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContentSerializer
import net.folivo.trixnity.core.serialization.events.createEncryptedEventContentSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals

class EncryptedEventContentSerializerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule = createEncryptedEventContentSerializersModule()
    }

    @Test
    fun shouldSerializeMegolm() {
        val result = json.encodeToString(
            EncryptedEventContentSerializer,
            MegolmEncryptedEventContent(
                senderKey = Key.Curve25519Key("", "<sender_curve25519_key>"),
                deviceId = "<sender_device_id>",
                sessionId = "<outbound_group_session_id>",
                ciphertext = "<encrypted_payload_base_64>",
                algorithm = Megolm
            )
        )
        val expectedResult = """
          {
            "ciphertext":"<encrypted_payload_base_64>",
            "sender_key":"<sender_curve25519_key>",
            "device_id":"<sender_device_id>",
            "session_id":"<outbound_group_session_id>",
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
            "ciphertext": "<encrypted_payload_base_64>"
          }
        """.trimIndent()
        val result = json.decodeFromString<EncryptedEventContent>(input)
        assertEquals(
            MegolmEncryptedEventContent(
                senderKey = Key.Curve25519Key(null, "<sender_curve25519_key>"),
                deviceId = "<sender_device_id>",
                sessionId = "<outbound_group_session_id>",
                ciphertext = "<encrypted_payload_base_64>",
                algorithm = Megolm
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
                algorithm = Olm
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
                algorithm = Olm
            ), result
        )
    }

    @Test
    fun shouldDeserializeUnknown() {
        val input = """
          {
            "algorithm": "super_duper_algo",
            "sender_key": "<sender_curve25519_key>",
            "ciphertext": {
              "<device_curve25519_key>": {
                "type": 0,
                "body": "<encrypted_payload_base_64>"
              }
            }
          }
        """.trimIndent()
        val result = json.decodeFromString<EncryptedEventContent>(input)

        assertEquals(
            UnknownEncryptedEventContent(
                algorithm = Unknown("super_duper_algo"),
                senderKey = Key.Curve25519Key(null, "<sender_curve25519_key>"),
                ciphertext = JsonObject(
                    mapOf(
                        "<device_curve25519_key>" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive(INITIAL_PRE_KEY.value),
                                "body" to JsonPrimitive("<encrypted_payload_base_64>"),
                            )
                        )
                    )
                )
            ), result
        )
    }
}
package net.folivo.trixnity.core.serialization.m.room.encrypted

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContentSerializer
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Unknown
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlin.test.Test
import kotlin.test.assertEquals

class EncryptedToDeviceEventContentSerializerTest {

    private val json = createMatrixEventJson()

    @Test
    fun shouldSerializeOlm() {
        val result = json.encodeToString(
            EncryptedToDeviceEventContentSerializer,
            OlmEncryptedToDeviceEventContent(
                senderKey = Curve25519KeyValue("<sender_curve25519_key>"),
                ciphertext = mapOf(
                    "<device_curve25519_key>" to CiphertextInfo("<encrypted_payload_base_64>", INITIAL_PRE_KEY)
                ),
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
        val result = json.decodeFromString<EncryptedToDeviceEventContent>(input)
        assertEquals(
            OlmEncryptedToDeviceEventContent(
                senderKey = Curve25519KeyValue("<sender_curve25519_key>"),
                ciphertext = mapOf(
                    "<device_curve25519_key>" to CiphertextInfo("<encrypted_payload_base_64>", INITIAL_PRE_KEY)
                ),
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
        val result = json.decodeFromString<EncryptedToDeviceEventContent>(input)

        assertEquals(
            EncryptedToDeviceEventContent.Unknown(
                algorithm = Unknown("super_duper_algo"),
                raw = buildJsonObject {
                    put("algorithm", JsonPrimitive("super_duper_algo"))
                    put("ciphertext", JsonPrimitive("peng"))
                }
            ), result
        )
    }
}
package net.folivo.trixnity.core.serialization.m.room.encrypted

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.core.MegolmMessageValue
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContentSerializer
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Unknown
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EncryptedMessageEventContentSerializerTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()

    @Test
    fun shouldSerializeMegolm() {
        val result = json.encodeToString(
            EncryptedMessageEventContentSerializer,
            MegolmEncryptedMessageEventContent(
                senderKey = Curve25519KeyValue("<sender_curve25519_key>"),
                deviceId = "<sender_device_id>",
                sessionId = "<outbound_group_session_id>",
                ciphertext = MegolmMessageValue("<encrypted_payload_base_64>"),
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
        val result = json.decodeFromString<EncryptedMessageEventContent>(input)
        assertEquals(
            MegolmEncryptedMessageEventContent(
                senderKey = Curve25519KeyValue("<sender_curve25519_key>"),
                deviceId = "<sender_device_id>",
                sessionId = "<outbound_group_session_id>",
                ciphertext = MegolmMessageValue("<encrypted_payload_base_64>"),
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
        val result = json.decodeFromString<EncryptedMessageEventContent>(input)

        assertEquals(
            EncryptedMessageEventContent.Unknown(
                algorithm = Unknown("super_duper_algo"),
                raw = buildJsonObject {
                    put("algorithm", JsonPrimitive("super_duper_algo"))
                    put("ciphertext", JsonPrimitive("peng"))
                }
            ), result
        )
    }
}
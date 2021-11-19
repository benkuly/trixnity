package net.folivo.trixnity.core.serialization.crypto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import kotlin.test.Test
import kotlin.test.assertEquals

class SignedSerializerTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun shouldSerializeSignaturesOfUserIds() {
        val content = Signed(
            DeviceKeys(
                UserId("alice", "example.com"), "ALICEDEVICE", setOf(
                    EncryptionAlgorithm.Olm,
                    EncryptionAlgorithm.Megolm
                ), keysOf(Ed25519Key("ABC", "keyValue"), Key.Curve25519Key("DEF", "keyValue"))
            ), mapOf(
                UserId("alice", "example.com") to keysOf(Ed25519Key("JLAFKJWSCS", "aKey")),
            )
        )

        val expectedResult = """
            {
              "user_id":"@alice:example.com",
              "device_id":"ALICEDEVICE",
              "algorithms":[
                "m.olm.v1.curve25519-aes-sha2",
                "m.megolm.v1.aes-sha2"
              ],
              "keys":{
                "ed25519:ABC":"keyValue",
                "curve25519:DEF":"keyValue"
              },
              "signatures":{
                "@alice:example.com":{
                  "ed25519:JLAFKJWSCS":"aKey"
                }
              }
            }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(content)
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeSignaturesOfUserIds() {
        val input = """
            {
              "user_id": "@alice:example.com",
              "device_id": "ALICEDEVICE",
              "algorithms": [
                "m.olm.v1.curve25519-aes-sha2",
                "m.megolm.v1.aes-sha2"
              ],
              "keys": {
                "ed25519:ABC": "keyValue",
                "curve25519:DEF": "keyValue"
              },
              "signatures": {
                "@alice:example.com": {
                  "ed25519:JLAFKJWSCS": "aKey"
                }
              }
            }
    """.trimIndent()
        val result = json.decodeFromString<Signed<DeviceKeys, UserId>>(input)
        assertEquals(
            DeviceKeys(
                UserId("alice", "example.com"), "ALICEDEVICE", setOf(
                    EncryptionAlgorithm.Olm,
                    EncryptionAlgorithm.Megolm
                ), keysOf(Ed25519Key("ABC", "keyValue"), Key.Curve25519Key("DEF", "keyValue"))
            ), result.signed
        )
        assertEquals(
            mapOf(
                UserId("alice", "example.com") to keysOf(Ed25519Key("JLAFKJWSCS", "aKey")),
            ), result.signatures
        )
    }
}
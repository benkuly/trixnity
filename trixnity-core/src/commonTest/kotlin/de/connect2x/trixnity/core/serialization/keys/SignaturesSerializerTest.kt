package de.connect2x.trixnity.core.serialization.keys

import kotlinx.serialization.json.Json
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.Signatures
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SignaturesSerializerTest : TrixnityBaseTest() {

    private val json = Json

    @Test
    fun shouldSerializeSignatures() {
        val content: Signatures<String> = mapOf(
            "@alice:example.com" to keysOf(Ed25519Key("JLAFKJWSCS", "aKey")),
            "example.org" to keysOf(
                Ed25519Key("0", "some9signature"),
                Ed25519Key("1", "some10signature")
            )
        )
        val expectedResult = """
            {
              "@alice:example.com":{
                "ed25519:JLAFKJWSCS":"aKey"
              },
              "example.org":{
                "ed25519:0":"some9signature",
                "ed25519:1":"some10signature"
              }
            }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(content)
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeSignatures() {
        val input = """
            {
              "@alice:example.com": {
                "ed25519:JLAFKJWSCS": "aKey"
              },
              "example.org": {
                "ed25519:0": "some9signature",
                "ed25519:1": "some10signature"
              }
            }
    """.trimIndent()
        val result = json.decodeFromString<Signatures<String>>(input)
        assertEquals(
            mapOf(
                "@alice:example.com" to keysOf(Ed25519Key("JLAFKJWSCS", "aKey")),
                "example.org" to keysOf(
                    Ed25519Key("0", "some9signature"),
                    Ed25519Key("1", "some10signature")
                )
            ), result
        )
    }

    @Test
    fun shouldSerializeSignaturesOfUserIds() {
        val content = mapOf(
            UserId("alice", "example.com") to keysOf(Ed25519Key("JLAFKJWSCS", "aKey")),
        )

        val expectedResult = """
            {
              "@alice:example.com":{
                "ed25519:JLAFKJWSCS":"aKey"
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
              "@alice:example.com": {
                "ed25519:JLAFKJWSCS": "aKey"
              }
            }
    """.trimIndent()
        val result = json.decodeFromString<Signatures<UserId>>(input)
        assertEquals(
            mapOf(
                UserId("alice", "example.com") to keysOf(Ed25519Key("JLAFKJWSCS", "aKey"))
            ), result
        )
    }
}
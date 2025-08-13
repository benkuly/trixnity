package net.folivo.trixnity.client.notification

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import kotlin.test.Test

class JsonPathTest {
    @Test
    fun `jsonPath - get property from key`() {
        jsonPath(
            JsonObject(mapOf("a" to JsonPrimitive("value"))),
            "a"
        ) shouldBe JsonPrimitive("value")
    }

    @Test
    fun `jsonPath - get property from path`() {
        jsonPath(
            JsonObject(mapOf("a" to JsonObject(mapOf("b" to JsonPrimitive("value"))))),
            "a.b"
        ) shouldBe JsonPrimitive("value")
    }

    @Test
    fun `jsonPath - return null when property not found`() {
        jsonPath(
            JsonObject(mapOf("a" to JsonObject(mapOf("b.c" to JsonPrimitive("value"))))),
            "a.b.c"
        ) shouldBe null
    }

    @Test
    fun `jsonPath - escape path`() {

        jsonPath(
            JsonObject(mapOf("a" to JsonObject(mapOf("b.c" to JsonPrimitive("value"))))),
            "a.b\\.c"
        ) shouldBe JsonPrimitive("value")
    }

    @Test
    fun `jsonPath - escaped backslash`() {
        jsonPath(
            buildJsonObject {
                putJsonObject("""content""") {
                    put("""m\foo""", "value")
                }
            },
            """content.m\\foo"""
        ) shouldBe JsonPrimitive("value")
    }

    @Test
    fun `jsonPath - other escapes`() {
        jsonPath(
            buildJsonObject {
                put("""\x""", "value")
            },
            """\x"""
        ) shouldBe JsonPrimitive("value")
    }
}

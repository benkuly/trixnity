package net.folivo.trixnity.core.serialization

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

// Tests are taken from here: https://matrix.org/docs/spec/appendices#examples
class CanonicalJsonTest {
    @Test
    fun test1() {
        val input = JsonObject(mapOf())
        val output = canonicalJsonString(input)
        val expected = "{}"
        assertEquals(expected, output)
    }

    @Test
    fun test2() {
        val input = JsonObject(
            mapOf(
                "one" to JsonPrimitive(1),
                "two" to JsonPrimitive("Two")
            )
        )
        val output = canonicalJsonString(input)
        val expected = """{"one":1,"two":"Two"}"""
        assertEquals(expected, output)
    }

    @Test
    fun test3() {
        val input = JsonObject(
            mapOf(
                "b" to JsonPrimitive("2"),
                "a" to JsonPrimitive("1")
            )
        )
        val output = canonicalJsonString(input)
        val expected = """{"a":"1","b":"2"}"""
        assertEquals(expected, output)
    }

    @Test
    fun test4() {
        val input = JsonObject(
            mapOf(
                "auth" to JsonObject(
                    mapOf(
                        "success" to JsonPrimitive(true),
                        "mxid" to JsonPrimitive(UserId("john.doe", "example.com").full),
                        "profile" to JsonObject(
                            mapOf(
                                "display_name" to JsonPrimitive("John Doe"),
                                "three_pids" to JsonArray(
                                    listOf(
                                        JsonObject(
                                            mapOf(
                                                "medium" to JsonPrimitive("email"),
                                                "address" to JsonPrimitive("john.doe@example.org")
                                            )
                                        ),
                                        JsonObject(
                                            mapOf(
                                                "medium" to JsonPrimitive("msisdn"),
                                                "address" to JsonPrimitive("123456789")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        val output = canonicalJsonString(input)
        val expected =
            """{"auth":{"mxid":"@john.doe:example.com","profile":{"display_name":"John Doe","three_pids":[{"address":"john.doe@example.org","medium":"email"},{"address":"123456789","medium":"msisdn"}]},"success":true}}"""
        assertEquals(expected, output)
    }

    @Test
    fun test5() {
        val input = JsonObject(
            mapOf(
                "a" to JsonPrimitive("日本語")
            )
        )
        val output = canonicalJsonString(input)
        val expected = """{"a":"日本語"}"""
        assertEquals(expected, output)
    }

    @Test
    fun test6() {
        val input = JsonObject(
            mapOf(
                "本" to JsonPrimitive(2),
                "日" to JsonPrimitive(1)
            )
        )
        val output = canonicalJsonString(input)
        val expected = """{"日":1,"本":2}"""
        assertEquals(expected, output)
    }

//    @Test // I think that is not relevant to us, because we always encode as UTF-8
//    fun test7() {
//        val input = JsonObject(
//            mapOf(
//                "a" to JsonPrimitive("""\u65E5""")
//            )
//        )
//        val output = canonicalJson(input)
//        val expected = """{"a":"日"}"""
//        assertEquals(expected, output)
//    }

    @Test
    fun test8() {
        val input = JsonObject(
            mapOf(
                "a" to JsonNull
            )
        )
        val output = canonicalJsonString(input)
        val expected = """{"a":null}"""
        assertEquals(expected, output)
    }

    @Test
    fun test9() {
        val input = JsonObject(
            mapOf(
                "a" to JsonPrimitive(-0),
//                "b" to JsonPrimitive(1e10) // not relevant for us, because 1e10 in Kotlin is Double, but only Int is allowed
            )
        )
        val output = canonicalJsonString(input)
        val expected = """{"a":0}"""//"""{"a":0,"b":10000000000}"""
        assertEquals(expected, output)
    }
}
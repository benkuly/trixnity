package net.folivo.trixnity.client.api

import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorResponseTest {

    private val json = createMatrixJson()

    @Test
    fun shouldSerializeErrorResponse() = runBlockingTest {
        val result = json.encodeToString(ErrorResponseSerializer, ErrorResponse.LimitExceeded("liiiimit", 1234))
        assertEquals("""{"errcode":"M_LIMIT_EXCEEDED","error":"liiiimit","retry_after_ms":1234}""", result)
    }

    @Test
    fun shouldDeserializeErrorResponse() = runBlockingTest {
        val result = json.decodeFromString(
            ErrorResponseSerializer,
            """{"errcode":"M_LIMIT_EXCEEDED","error":"liiiimit","retry_after_ms":1234}"""
        )
        assertEquals(ErrorResponse.LimitExceeded("liiiimit", 1234), result)
    }

    @Test
    fun shouldSerializeUnknownErrorResponse() = runBlockingTest {
        val result = json.encodeToString(ErrorResponseSerializer, ErrorResponse.CustomErrorResponse("ANANAS", "oh"))
        assertEquals("""{"errcode":"ANANAS","error":"oh"}""", result)
    }

    @Test
    fun shouldDeserializeUnknownErrorResponse() = runBlockingTest {
        val result = json.decodeFromString(ErrorResponseSerializer, """{"errcode":"ANANAS","error":"oh"}""")
        assertEquals(ErrorResponse.CustomErrorResponse("ANANAS", "oh"), result)
    }

}
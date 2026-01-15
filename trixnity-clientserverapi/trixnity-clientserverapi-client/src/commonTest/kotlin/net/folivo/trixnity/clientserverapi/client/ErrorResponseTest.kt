package net.folivo.trixnity.clientserverapi.client

import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorResponseTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()

    @Test
    fun shouldSerializeErrorResponse() = runTest {
        val result = json.encodeToString(
            ErrorResponse.Serializer,
            ErrorResponse.LimitExceeded("liiiimit", 1234)
        )
        assertEquals("""{"errcode":"M_LIMIT_EXCEEDED","error":"liiiimit","retry_after_ms":1234}""", result)
    }

    @Test
    fun shouldDeserializeErrorResponse() = runTest {
        val result = json.decodeFromString(
            ErrorResponse.Serializer,
            """{"errcode":"M_LIMIT_EXCEEDED","error":"liiiimit","retry_after_ms":1234}"""
        )
        assertEquals(ErrorResponse.LimitExceeded("liiiimit", 1234), result)
    }

    @Test
    fun shouldSerializeUnknownErrorResponse() = runTest {
        val result = json.encodeToString(
            ErrorResponse.Serializer,
            ErrorResponse.CustomErrorResponse("ANANAS", "oh")
        )
        assertEquals("""{"errcode":"ANANAS","error":"oh"}""", result)
    }

    @Test
    fun shouldDeserializeUnknownErrorResponse() = runTest {
        val result = json.decodeFromString(
            ErrorResponse.Serializer,
            """{"errcode":"ANANAS","error":"oh"}"""
        )
        assertEquals(
            ErrorResponse.CustomErrorResponse("ANANAS", "oh"),
            result
        )
    }

}
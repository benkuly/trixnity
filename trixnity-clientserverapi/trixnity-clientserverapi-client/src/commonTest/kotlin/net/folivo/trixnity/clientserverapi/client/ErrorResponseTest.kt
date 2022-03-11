package net.folivo.trixnity.clientserverapi.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorResponseTest {

    private val json = createMatrixJson()

    @Test
    fun shouldSerializeErrorResponse() = runTest {
        val result = json.encodeToString(
            ErrorResponseSerializer,
            ErrorResponse.LimitExceeded("liiiimit", 1234)
        )
        assertEquals("""{"errcode":"M_LIMIT_EXCEEDED","error":"liiiimit","retry_after_ms":1234}""", result)
    }

    @Test
    fun shouldDeserializeErrorResponse() = runTest {
        val result = json.decodeFromString(
            ErrorResponseSerializer,
            """{"errcode":"M_LIMIT_EXCEEDED","error":"liiiimit","retry_after_ms":1234}"""
        )
        assertEquals(ErrorResponse.LimitExceeded("liiiimit", 1234), result)
    }

    @Test
    fun shouldSerializeUnknownErrorResponse() = runTest {
        val result = json.encodeToString(
            ErrorResponseSerializer,
            ErrorResponse.CustomErrorResponse("ANANAS", "oh")
        )
        assertEquals("""{"errcode":"ANANAS","error":"oh"}""", result)
    }

    @Test
    fun shouldDeserializeUnknownErrorResponse() = runTest {
        val result = json.decodeFromString(
            ErrorResponseSerializer,
            """{"errcode":"ANANAS","error":"oh"}"""
        )
        assertEquals(
            ErrorResponse.CustomErrorResponse("ANANAS", "oh"),
            result
        )
    }

}
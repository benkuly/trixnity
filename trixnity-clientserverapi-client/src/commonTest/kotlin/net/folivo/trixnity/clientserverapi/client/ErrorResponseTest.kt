package net.folivo.trixnity.clientserverapi.client

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorResponseTest {

    private val json = createMatrixJson()

    @Test
    fun shouldSerializeErrorResponse() = runTest {
        val result = json.encodeToString(
            net.folivo.trixnity.clientserverapi.model.ErrorResponseSerializer,
            net.folivo.trixnity.clientserverapi.model.ErrorResponse.LimitExceeded("liiiimit", 1234)
        )
        assertEquals("""{"errcode":"M_LIMIT_EXCEEDED","error":"liiiimit","retry_after_ms":1234}""", result)
    }

    @Test
    fun shouldDeserializeErrorResponse() = runTest {
        val result = json.decodeFromString(
            net.folivo.trixnity.clientserverapi.model.ErrorResponseSerializer,
            """{"errcode":"M_LIMIT_EXCEEDED","error":"liiiimit","retry_after_ms":1234}"""
        )
        assertEquals(net.folivo.trixnity.clientserverapi.model.ErrorResponse.LimitExceeded("liiiimit", 1234), result)
    }

    @Test
    fun shouldSerializeUnknownErrorResponse() = runTest {
        val result = json.encodeToString(
            net.folivo.trixnity.clientserverapi.model.ErrorResponseSerializer,
            net.folivo.trixnity.clientserverapi.model.ErrorResponse.CustomErrorResponse("ANANAS", "oh")
        )
        assertEquals("""{"errcode":"ANANAS","error":"oh"}""", result)
    }

    @Test
    fun shouldDeserializeUnknownErrorResponse() = runTest {
        val result = json.decodeFromString(
            net.folivo.trixnity.clientserverapi.model.ErrorResponseSerializer,
            """{"errcode":"ANANAS","error":"oh"}"""
        )
        assertEquals(
            net.folivo.trixnity.clientserverapi.model.ErrorResponse.CustomErrorResponse("ANANAS", "oh"),
            result
        )
    }

}
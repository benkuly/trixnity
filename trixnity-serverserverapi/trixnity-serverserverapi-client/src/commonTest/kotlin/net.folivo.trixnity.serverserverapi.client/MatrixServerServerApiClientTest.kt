package net.folivo.trixnity.serverserverapi.client

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test

class MatrixServerServerApiClientTest : TrixnityBaseTest() {

    @Test
    fun shouldUseDelegate() = runTest {
        var endpointCalled = false
        val cut = MatrixServerServerApiClientImpl(
            hostname = "myHost",
            getDelegatedDestination = { hostname, port ->
                hostname shouldBe "otherHost"
                port shouldBe 80
                "otherHostDelegate" to 443
            },
            sign = { Key.Ed25519Key("ABC", "signature") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.host shouldBe "otherHostDelegate"
                    request.url.port shouldBe 443
                    endpointCalled = true
                    respond("{}")
                }
            })
        cut.baseClient.baseClient.get {
            host = "otherHost"
            port = 80
        }
        endpointCalled shouldBe true
    }

    @Test
    fun shouldCreateSignatureAuthenticationHeader() = runTest {
        var endpointCalled = false
        val cut = MatrixServerServerApiClientImpl(
            hostname = "myHost",
            getDelegatedDestination = { hostname, port -> hostname to port },
            sign = {
                it shouldBe """{"content":{"key":"value"},"destination":"otherHost:80","method":"POST","origin":"myHost","uri":"/test"}"""
                Key.Ed25519Key("ABC", "signature")
            },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.headers[HttpHeaders.Authorization] shouldBe """X-Matrix origin="myHost",destination="otherHost:80",key="ed25519:ABC",sig="signature""""
                    request.body.toByteArray().decodeToString() shouldBe """{"key":"value"}"""
                    endpointCalled = true
                    respond("{}")
                }
            })
        cut.baseClient.baseClient.post("/test") {
            host = "otherHost"
            port = 80
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("key", JsonPrimitive("value")) })
        }
        endpointCalled shouldBe true
    }

    @Test
    fun shouldCreateSignatureAuthenticationHeaderWithoutBody() = runTest {
        var endpointCalled = false
        val cut = MatrixServerServerApiClientImpl(
            hostname = "myHost",
            getDelegatedDestination = { hostname, port -> hostname to port },
            sign = {
                it shouldBe """{"destination":"otherHost:80","method":"GET","origin":"myHost","uri":"/test"}"""
                Key.Ed25519Key("ABC", "signature")
            },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.headers[HttpHeaders.Authorization] shouldBe """X-Matrix origin="myHost",destination="otherHost:80",key="ed25519:ABC",sig="signature""""
                    endpointCalled = true
                    respond("{}")
                }
            })
        cut.baseClient.baseClient.get("/test") {
            host = "otherHost"
            port = 80
        }
        endpointCalled shouldBe true
    }
}
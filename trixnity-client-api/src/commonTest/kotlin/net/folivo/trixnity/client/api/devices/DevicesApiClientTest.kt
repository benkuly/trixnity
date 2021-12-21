package net.folivo.trixnity.client.api.devices

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.uia.UIA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesApiClientTest {

    @Test
    fun shouldGetDevices() = runTest {
        val response = """
            {
              "devices":[
                {
                  "device_id":"ABCDEF",
                  "display_name":"desktop",
                  "last_seen_ip":"1.2.3.4",
                  "last_seen_ts":1474491775024
                }
              ]
            }
        """.trimIndent().lines().joinToString("") { it.trim() }
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/devices", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            response,
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.devices.getDevices().getOrThrow()
        assertEquals(
            GetDevicesResponse(
                listOf(
                    Device(
                        deviceId = "ABCDEF",
                        displayName = "desktop",
                        lastSeenIp = "1.2.3.4",
                        lastSeenTs = 1474491775024L
                    )
                )
            ),
            result
        )
    }

    @Test
    fun shouldGetDevice() = runTest {
        val response = """
            {
              "device_id":"ABCDEF",
              "display_name":"desktop",
              "last_seen_ip":"1.2.3.4",
              "last_seen_ts":1474491775024
            }
        """.trimIndent().lines().joinToString("") { it.trim() }
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/devices/ABCDEF", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            response,
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.devices.getDevice("ABCDEF").getOrThrow()
        assertEquals(
            Device(
                deviceId = "ABCDEF",
                displayName = "desktop",
                lastSeenIp = "1.2.3.4",
                lastSeenTs = 1474491775024L
            ),
            result
        )
    }

    @Test
    fun shouldUpdateDevice() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/devices/ABCDEF", request.url.fullPath)
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals("""{"display_name":"desktop"}""", request.body.toByteArray().decodeToString())
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.devices.updateDevice(deviceId = "ABCDEF", displayName = "desktop")
    }

    @Test
    fun shouldDeleteDevices() = runTest {
        val expectedRequest = """
            {
              "devices":[
                "ABCDEFG"
              ]
            }
        """.trimIndent().lines().joinToString("") { it.trim() }
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/delete_devices", request.url.fullPath)
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(expectedRequest, request.body.toByteArray().decodeToString())
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.devices.deleteDevices(devices = listOf("ABCDEFG")).getOrThrow()
        assertTrue { result is UIA.UIASuccess }
    }

    @Test
    fun shouldDeleteDevice() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/devices/ABCDEFG", request.url.fullPath)
                        assertEquals(HttpMethod.Delete, request.method)
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.devices.deleteDevice(deviceId = "ABCDEFG").getOrThrow()
        assertTrue { result is UIA.UIASuccess }
    }
}

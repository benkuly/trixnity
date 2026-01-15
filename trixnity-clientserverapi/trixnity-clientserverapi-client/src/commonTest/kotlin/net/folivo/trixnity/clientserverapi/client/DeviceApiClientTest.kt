package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.clientserverapi.model.device.*
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.*
import net.folivo.trixnity.core.model.keys.SessionKeyValue
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceApiClientTest : TrixnityBaseTest() {

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
        """.trimIndent()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/devices", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        response,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.device.getDevices().getOrThrow()
        assertEquals(
            listOf(
                Device(
                    deviceId = "ABCDEF",
                    displayName = "desktop",
                    lastSeenIp = "1.2.3.4",
                    lastSeenTs = 1474491775024L
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
        """.trimIndent()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/devices/ABCDEF", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        response,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.device.getDevice("ABCDEF").getOrThrow()
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
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
            })
        matrixRestClient.device.updateDevice(deviceId = "ABCDEF", displayName = "desktop")
    }

    @Test
    fun shouldDeleteDevices() = runTest {
        val expectedRequest = """
            {
              "devices":[
                "ABCDEFG"
              ]
            }
        """.trimToFlatJson()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
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
            })
        val result = matrixRestClient.device.deleteDevices(devices = listOf("ABCDEFG")).getOrThrow()
        assertTrue { result is UIA.Success }
    }

    @Test
    fun shouldDeleteDevice() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/devices/ABCDEFG", request.url.fullPath)
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals("{}", request.body.toByteArray().decodeToString())
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.device.deleteDevice(deviceId = "ABCDEFG").getOrThrow()
        assertTrue { result is UIA.Success }
    }

    @OptIn(MSC3814::class)
    @Test
    fun shouldGetDehydratedDevice() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("", request.body.toByteArray().decodeToString())
                    respond(
                        """
                            {
                                "device_id":"ABCDEFG",
                                "device_data":{
                                    "algorithm": "org.matrix.msc3814.v2",
                                    "device_pickle": "encrypted dehydrated device",
                                    "nonce": "random nonce"
                                }
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.device.getDehydratedDevice().getOrThrow()
        result shouldBe GetDehydratedDevice.Response(
            deviceId = "ABCDEFG",
            deviceData = DehydratedDeviceData.DehydrationV2("encrypted dehydrated device", "random nonce")
        )
    }

    @OptIn(MSC3814::class)
    @Test
    fun shouldSetDehydratedDevice() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                        {
                            "device_id": "ABCDEFG",
                            "device_data": {
                                "device_pickle": "encrypted dehydrated device",
                                "nonce": "random nonce",
                                "algorithm": "org.matrix.msc3814.v2"
                            },
                            "device_keys": {
                                "user_id": "@alice:example.com",
                                "device_id": "JLAFKJWSCS",
                                "algorithms": [
                                    "m.olm.v1.curve25519-aes-sha2",
                                    "m.megolm.v1.aes-sha2"
                                ],
                                "keys": {
                                    "curve25519:JLAFKJWSCS": "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI",
                                    "ed25519:JLAFKJWSCS": "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI"
                                },
                                "dehydrated": true,
                                "signatures": {
                                    "@alice:example.com": {
                                        "ed25519:JLAFKJWSCS": "dSO80A01XiigH3uBiDVx/EjzaoycHcjq9lfQX0uWsqxl2giMIiSPR8a4d291W1ihKJL/a+myXS367WT6NAIcBA"
                                    }
                                }
                            },
                            "one_time_keys": {
                                "curve25519:AAAAAQ": "/qyvZvwjiTxGdGU0RCguDCLeR+nmsb3FfNG3/Ve4vU8",
                                "signed_curve25519:AAAAHg": {
                                    "key": "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                                    "signatures": {
                                        "@alice:example.com": {
                                            "ed25519:JLAFKJWSCS": "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                                        }
                                    }
                                },
                                "signed_curve25519:AAAAHQ": {
                                    "key": "j3fR3HemM16M7CWhoI4Sk5ZsdmdfQHsKL1xuSft6MSw",
                                    "signatures": {
                                        "@alice:example.com": {
                                            "ed25519:JLAFKJWSCS": "IQeCEPb9HFk217cU9kw9EOiusC6kMIkoIRnbnfOh5Oc63S1ghgyjShBGpu34blQomoalCyXWyhaaT3MrLZYQAA"
                                        }
                                    }
                                }
                            },
                            "fallback_keys": {
                                "signed_curve25519:AAAAHg": {
                                    "key": "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                                    "fallback": true,
                                    "signatures": {
                                        "@alice:example.com": {
                                            "ed25519:JLAFKJWSCS": "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                                        }
                                    }
                                }
                            },
                            "initial_device_display_name": "dehydrated device"
                        }
                    """.trimToFlatJson()
                    respond(
                        """{"device_id":"ABCDEFG"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.device.setDehydratedDevice(
            deviceId = "ABCDEFG",
            deviceData = DehydratedDeviceData.DehydrationV2("encrypted dehydrated device", "random nonce"),
            deviceKeys = Signed(
                DeviceKeys(
                    userId = UserId("alice", "example.com"),
                    deviceId = "JLAFKJWSCS",
                    algorithms = setOf(Olm, Megolm),
                    keys = keysOf(
                        Curve25519Key("JLAFKJWSCS", "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI"),
                        Ed25519Key("JLAFKJWSCS", "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI")
                    ),
                    dehydrated = true,
                ),
                mapOf(
                    UserId("alice", "example.com") to keysOf(
                        Ed25519Key(
                            "JLAFKJWSCS",
                            "dSO80A01XiigH3uBiDVx/EjzaoycHcjq9lfQX0uWsqxl2giMIiSPR8a4d291W1ihKJL/a+myXS367WT6NAIcBA"
                        )
                    )
                ),
            ),
            oneTimeKeys = keysOf(
                Curve25519Key("AAAAAQ", "/qyvZvwjiTxGdGU0RCguDCLeR+nmsb3FfNG3/Ve4vU8"),
                SignedCurve25519Key(
                    "AAAAHg", "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs", signatures = mapOf(
                        UserId("alice", "example.com") to keysOf(
                            Ed25519Key(
                                "JLAFKJWSCS",
                                "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                            )
                        )
                    )
                ),
                SignedCurve25519Key(
                    "AAAAHQ", "j3fR3HemM16M7CWhoI4Sk5ZsdmdfQHsKL1xuSft6MSw", signatures = mapOf(
                        UserId("alice", "example.com") to keysOf(
                            Ed25519Key(
                                "JLAFKJWSCS",
                                "IQeCEPb9HFk217cU9kw9EOiusC6kMIkoIRnbnfOh5Oc63S1ghgyjShBGpu34blQomoalCyXWyhaaT3MrLZYQAA"
                            )
                        )
                    )
                )
            ),
            fallbackKeys = keysOf(
                SignedCurve25519Key(
                    "AAAAHg", "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs", true, mapOf(
                        UserId("alice", "example.com") to keysOf(
                            Ed25519Key(
                                "JLAFKJWSCS",
                                "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                            )
                        )
                    )
                )
            ),
            initialDeviceDisplayName = "dehydrated device"
        ).getOrThrow()
        result shouldBe SetDehydratedDevice.Response("ABCDEFG")
    }

    @OptIn(MSC3814::class)
    @Test
    fun shouldDeleteDehydratedDevice() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals("", request.body.toByteArray().decodeToString())
                    respond(
                        """{"device_id":"ABCDEFG"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.device.deleteDehydratedDevice().getOrThrow()
        result shouldBe DeleteDehydratedDevice.Response("ABCDEFG")
    }

    @OptIn(MSC3814::class)
    @Test
    fun shouldGetDehydratedDeviceEvents() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device/ABCDEFG/events",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """{"next_batch":"batch_me_if_you_can"}""",
                        request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
                            {
                                "events":[
                                    {
                                        "content":{
                                            "algorithm":"m.megolm.v1.aes-sha2",
                                            "room_id":"!Cuyf34gef24t:localhost",
                                            "session_id":"X3lUlvLELLYxeTx4yOVu6UDpasGEVO0Jbu+QFnm0cKQ",
                                            "session_key":"AgAAAADxKHa9uFxcXzwYoNueL5Xqi69IkD4sni8LlfJL7qNBEY..."
                                        },
                                        "sender":"@user:matrix.org",
                                        "type":"m.room_key"
                                    }
                                ],
                                "next_batch":"next"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.device.getDehydratedDeviceEvents("ABCDEFG", "batch_me_if_you_can").getOrThrow()
        result shouldBe GetDehydratedDeviceEvents.Response(
            "next", listOf(
                ClientEvent.ToDeviceEvent(
                    RoomKeyEventContent(
                        roomId = RoomId("!Cuyf34gef24t:localhost"),
                        sessionId = "X3lUlvLELLYxeTx4yOVu6UDpasGEVO0Jbu+QFnm0cKQ",
                        sessionKey = SessionKeyValue("AgAAAADxKHa9uFxcXzwYoNueL5Xqi69IkD4sni8LlfJL7qNBEY..."),
                        algorithm = Megolm
                    ),
                    sender = UserId("@user:matrix.org"),
                )
            )
        )
    }
}

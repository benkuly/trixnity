package de.connect2x.trixnity.clientserverapi.server

import dev.mokkery.*
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.clientserverapi.model.device.*
import de.connect2x.trixnity.clientserverapi.model.uia.RequestWithUIA
import de.connect2x.trixnity.clientserverapi.model.uia.ResponseWithUIA
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.RoomKeyEventContent
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import de.connect2x.trixnity.core.model.keys.Key.*
import de.connect2x.trixnity.core.model.keys.SessionKeyValue
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class DevicesRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = EventContentSerializerMappings.default

    val handlerMock = mock<DeviceApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = AccessTokenAuthenticationFunction {
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(
                            UserId("user", "server"),
                            "deviceId"
                        ), null
                    )
                }
            }
            matrixApiServer(json) {
                deviceApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetDevices() = testApplication {
        initCut()
        everySuspend { handlerMock.getDevices(any()) }
            .returns(
                GetDevices.Response(
                    listOf(
                        Device(
                            deviceId = "ABCDEF",
                            displayName = "desktop",
                            lastSeenIp = "1.2.3.4",
                            lastSeenTs = 1474491775024L
                        )
                    )
                )
            )
        val response = client.get("/_matrix/client/v3/devices") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
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
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getDevices(any())
        }
    }

    @Test
    fun shouldGetDevice() = testApplication {
        initCut()
        everySuspend { handlerMock.getDevice(any()) }
            .returns(
                Device(
                    deviceId = "ABCDEF",
                    displayName = "desktop",
                    lastSeenIp = "1.2.3.4",
                    lastSeenTs = 1474491775024L
                )
            )
        val response = client.get("/_matrix/client/v3/devices/ABCDEF") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "device_id":"ABCDEF",
                  "display_name":"desktop",
                  "last_seen_ip":"1.2.3.4",
                  "last_seen_ts":1474491775024
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getDevice(assert { it.endpoint.deviceId shouldBe "ABCDEF" })
        }
    }

    @Test
    fun shouldUpdateDevice() = testApplication {
        initCut()
        everySuspend { handlerMock.updateDevice(any()) }
            .returns(Unit)
        val response = client.put("/_matrix/client/v3/devices/ABCDEF") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody("""{"display_name":"desktop"}""")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.updateDevice(assert {
                it.endpoint.deviceId shouldBe "ABCDEF"
                it.requestBody shouldBe UpdateDevice.Request("desktop")
            })
        }
    }

    @Test
    fun shouldDeleteDevices() = testApplication {
        initCut()
        everySuspend { handlerMock.deleteDevices(any()) }
            .returns(ResponseWithUIA.Success(Unit))
        val response = client.post("/_matrix/client/v3/delete_devices") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "devices":[
                    "ABCDEFG"
                  ]
                }
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.deleteDevices(assert {
                it.requestBody shouldBe RequestWithUIA(DeleteDevices.Request(listOf("ABCDEFG")), null)
            })
        }
    }

    @Test
    fun shouldDeleteDevice() = testApplication {
        initCut()
        everySuspend { handlerMock.deleteDevice(any()) }
            .returns(ResponseWithUIA.Success(Unit))
        val response = client.delete("/_matrix/client/v3/devices/ABCDEF") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.deleteDevice(assert {
                it.endpoint.deviceId shouldBe "ABCDEF"
            })
        }
    }

    @OptIn(MSC3814::class)
    @Test
    fun shouldGetDehydratedDevice() = testApplication {
        initCut()
        everySuspend { handlerMock.getDehydratedDevice(any()) }
            .returns(
                GetDehydratedDevice.Response(
                    deviceId = "ABCDEFG",
                    deviceData = DehydratedDeviceData.DehydrationV2("encrypted dehydrated device", "random nonce")
                )
            )
        val response = client.get("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                    "device_id":"ABCDEFG",
                    "device_data":{
                        "device_pickle": "encrypted dehydrated device",
                        "nonce": "random nonce",
                        "algorithm": "org.matrix.msc3814.v2"
                    }
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getDehydratedDevice(any())
        }
    }

    @OptIn(MSC3814::class)
    @Test
    fun shouldSetDehydratedDevice() = testApplication {
        initCut()
        everySuspend { handlerMock.setDehydratedDevice(any()) }
            .returns(SetDehydratedDevice.Response("ABCDEFG"))
        val response = client.put("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
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
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"device_id":"ABCDEFG"}"""
        }
        verifySuspend {
            handlerMock.setDehydratedDevice(assert {
                it.requestBody shouldBe SetDehydratedDevice.Request(
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
                )
            })
        }
    }

    @OptIn(MSC3814::class)
    @Test
    fun shouldDeleteDehydratedDevice() = testApplication {
        initCut()
        everySuspend { handlerMock.deleteDehydratedDevice(any()) }
            .returns(DeleteDehydratedDevice.Response("ABCDEFG"))
        val response = client.delete("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"device_id":"ABCDEFG"}"""
        }
        verifySuspend {
            handlerMock.deleteDehydratedDevice(any())
        }
    }

    @OptIn(MSC3814::class)
    @Test
    fun shouldGetDehydratedDeviceEvents() = testApplication {
        initCut()
        everySuspend { handlerMock.getDehydratedDeviceEvents(any()) }
            .returns(
                GetDehydratedDeviceEvents.Response(
                    "next",
                    listOf(
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
            )
        val response = client.post("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device/ABCDEFG/events") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody("""{"next_batch":"batch_me_if_you_can"}""")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                    "next_batch":"next",
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
                    ]
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getDehydratedDeviceEvents(assert {
                it.endpoint.deviceId shouldBe "ABCDEFG"
                it.requestBody.nextBatch shouldBe "batch_me_if_you_can"
            })
        }
    }
}

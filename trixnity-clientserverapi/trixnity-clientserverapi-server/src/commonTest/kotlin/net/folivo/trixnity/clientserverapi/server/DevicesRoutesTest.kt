package net.folivo.trixnity.clientserverapi.server

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
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.devices.DeleteDevices
import net.folivo.trixnity.clientserverapi.model.devices.Device
import net.folivo.trixnity.clientserverapi.model.devices.GetDevices
import net.folivo.trixnity.clientserverapi.model.devices.UpdateDevice
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class DevicesRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = createDefaultEventContentSerializerMappings()

    val handlerMock = mock<DevicesApiHandler>()

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
                devicesApiRoutes(handlerMock, json, mapping)
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
}

package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.devices.DeleteDevices
import net.folivo.trixnity.clientserverapi.model.devices.Device
import net.folivo.trixnity.clientserverapi.model.devices.GetDevices
import net.folivo.trixnity.clientserverapi.model.devices.UpdateDevice
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class DevicesRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: DevicesApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                routing {
                    devicesApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @Test
    fun shouldGetDevices() = testApplication {
        initCut()
        everySuspending { handlerMock.getDevices(isAny()) }
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
        verifyWithSuspend {
            handlerMock.getDevices(isAny())
        }
    }

    @Test
    fun shouldGetDevice() = testApplication {
        initCut()
        everySuspending { handlerMock.getDevice(isAny()) }
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
        verifyWithSuspend {
            handlerMock.getDevice(assert { it.endpoint.deviceId shouldBe "ABCDEF" })
        }
    }

    @Test
    fun shouldUpdateDevice() = testApplication {
        initCut()
        everySuspending { handlerMock.updateDevice(isAny()) }
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
        verifyWithSuspend {
            handlerMock.updateDevice(assert {
                it.endpoint.deviceId shouldBe "ABCDEF"
                it.requestBody shouldBe UpdateDevice.Request("desktop")
            })
        }
    }

    @Test
    fun shouldDeleteDevices() = testApplication {
        initCut()
        everySuspending { handlerMock.deleteDevices(isAny()) }
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
        verifyWithSuspend {
            handlerMock.deleteDevices(assert {
                it.requestBody shouldBe RequestWithUIA(DeleteDevices.Request(listOf("ABCDEFG")), null)
            })
        }
    }

    @Test
    fun shouldDeleteDevice() = testApplication {
        initCut()
        everySuspending { handlerMock.deleteDevice(isAny()) }
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
        verifyWithSuspend {
            handlerMock.deleteDevice(assert {
                it.endpoint.deviceId shouldBe "ABCDEF"
            })
        }
    }
}

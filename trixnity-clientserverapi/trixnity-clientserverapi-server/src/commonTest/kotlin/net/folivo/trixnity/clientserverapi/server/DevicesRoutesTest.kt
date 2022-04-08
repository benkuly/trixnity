package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import io.mockative.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.devices.DeleteDevices
import net.folivo.trixnity.clientserverapi.model.devices.Device
import net.folivo.trixnity.clientserverapi.model.devices.GetDevices
import net.folivo.trixnity.clientserverapi.model.devices.UpdateDevice
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesRoutesTest {
    private val json = createMatrixJson()
    private val mapping = createEventContentSerializerMappings()

    @OptIn(ConfigurationApi::class)
    @Mock
    val handlerMock = configure(mock(classOf<DevicesApiHandler>())) { stubsUnitByDefault = true }

    private fun ApplicationTestBuilder.initCut() {
        application {
            install(Authentication) {
                matrixAccessTokenAuth {
                    authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
                }
            }
            matrixApiServer(json) {
                routing {
                    devicesApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @AfterTest
    fun afterTest() {
        verify(handlerMock).hasNoUnmetExpectations()
        verify(handlerMock).hasNoUnverifiedExpectations()
    }

    @Test
    fun shouldGetDevices() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getDevices)
            .whenInvokedWith(any())
            .then {
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
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getDevices)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldGetDevice() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getDevice)
            .whenInvokedWith(any())
            .then {
                Device(
                    deviceId = "ABCDEF",
                    displayName = "desktop",
                    lastSeenIp = "1.2.3.4",
                    lastSeenTs = 1474491775024L
                )
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getDevice)
            .with(matching {
                it.endpoint.deviceId shouldBe "ABCDEF"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldUpdateDevice() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::updateDevice)
            .with(matching {
                it.endpoint.deviceId shouldBe "ABCDEF"
                it.requestBody shouldBe UpdateDevice.Request("desktop")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDeleteDevices() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::deleteDevices)
            .whenInvokedWith(any())
            .then { ResponseWithUIA.Success(Unit) }
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
        verify(handlerMock).suspendFunction(handlerMock::deleteDevices)
            .with(matching {
                it.requestBody shouldBe RequestWithUIA(DeleteDevices.Request(listOf("ABCDEFG")), null)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDeleteDevice() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::deleteDevice)
            .whenInvokedWith(any())
            .then { ResponseWithUIA.Success(Unit) }
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
        verify(handlerMock).suspendFunction(handlerMock::deleteDevice)
            .with(matching {
                it.endpoint.deviceId shouldBe "ABCDEF"
                true
            })
            .wasInvoked()
    }
}

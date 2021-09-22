package net.folivo.trixnity.appservice.rest

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.ErrorResponse
import net.folivo.trixnity.core.model.MatrixId.RoomAliasId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


fun Application.testAppAppserviceController(appserviceService: AppserviceService) {
    install(CallLogging)
    matrixAppserviceModule(MatrixAppserviceProperties("validToken"), appserviceService)
}

class AppserviceControllerTest {

    @Test
    fun `transactions should return 200 and delegate events to handler`() {
        val appserviceHandlerMock = mockk<AppserviceService>()
        val slot = slot<Flow<Event<*>>>()
        coEvery { appserviceHandlerMock.addTransactions("1", capture(slot)) } just Runs

        withTestApplication(moduleFunction = { testAppAppserviceController(appserviceHandlerMock) }) {
            val response = handleRequest(HttpMethod.Put, "/_matrix/app/v1/transactions/1?access_token=validToken") {
                addHeader(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.withCharset(Charset.defaultCharset()).toString()
                )
                setBody(
                    """
                    {
                      "events": [
                        {
                          "content": {
                            "membership": "join",
                            "avatar_url": "mxc://example.org/SEsfnsuifSDFSSEF",
                            "displayname": "Alice Margatroid"
                          },
                          "type": "m.room.member",
                          "event_id": "$143273582443PhrSn:example.org",
                          "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
                          "sender": "@example:example.org",
                          "origin_server_ts": 1432735824653,
                          "unsigned": {
                            "age": 1234
                          },
                          "state_key": "@alice:example.org"
                        },
                        {
                          "content": {
                            "body": "This is an example text message",
                            "msgtype": "m.text",
                            "format": "org.matrix.custom.html",
                            "formatted_body": "<b>This is an example text message</b>"
                          },
                          "type": "m.room.message",
                          "event_id": "$143273582443PhrSn:example.org",
                          "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
                          "sender": "@example:example.org",
                          "origin_server_ts": 1432735824653,
                          "unsigned": {
                            "age": 1234
                          }
                        }
                      ]
                    }
                """.trimIndent()
                )
            }.response
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("{}", response.content)
        }
        runBlocking {
            val events = slot.captured.toList()
            assertEquals(2, events.size)
            assertTrue { events[0].content is MemberEventContent }
            assertTrue { events[1].content is RoomMessageEventContent }
        }
    }

    @Test
    fun `users should return 200 when handler is true`() {
        val appserviceHandlerMock = mockk<AppserviceService>()
        coEvery { appserviceHandlerMock.hasUser(UserId("user", "server")) } returns true

        withTestApplication(moduleFunction = { testAppAppserviceController(appserviceHandlerMock) }) {
            val response =
                handleRequest(
                    HttpMethod.Get,
                    "/_matrix/app/v1/users/${"@user:server".encodeURLPath()}?access_token=validToken"
                ).response
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("{}", response.content)
        }
    }

    @Test
    fun `users should return 404 when handler is false`() {
        val appserviceHandlerMock = mockk<AppserviceService>()
        coEvery { appserviceHandlerMock.hasUser(UserId("user", "server")) } returns false

        withTestApplication(moduleFunction = { testAppAppserviceController(appserviceHandlerMock) }) {
            val response =
                handleRequest(
                    HttpMethod.Get,
                    "/_matrix/app/v1/users/${"@user:server".encodeURLPath()}?access_token=validToken"
                ).response
            assertEquals(HttpStatusCode.NotFound, response.status())
            assertEquals(
                Json.encodeToString(
                    ErrorResponse(
                        "NET.FOLIVO.MATRIX_NOT_FOUND",
                        "user @user:server not found"
                    )
                ), response.content
            )
        }
    }

    @Test
    fun `rooms should return 200 when handler is true`() {
        val appserviceHandlerMock = mockk<AppserviceService>()
        coEvery { appserviceHandlerMock.hasRoomAlias(RoomAliasId("alias", "server")) } returns true

        withTestApplication(moduleFunction = { testAppAppserviceController(appserviceHandlerMock) }) {
            val response =
                handleRequest(
                    HttpMethod.Get,
                    "/_matrix/app/v1/rooms/${"#alias:server".encodeURLPath()}?access_token=validToken"
                ).response
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("{}", response.content)
        }
    }

    @Test
    fun `rooms should return 404 when handler is false`() {
        val appserviceHandlerMock = mockk<AppserviceService>()
        coEvery { appserviceHandlerMock.hasRoomAlias(RoomAliasId("alias", "server")) } returns false

        withTestApplication(moduleFunction = { testAppAppserviceController(appserviceHandlerMock) }) {
            val response =
                handleRequest(
                    HttpMethod.Get,
                    "/_matrix/app/v1/rooms/${"#alias:server".encodeURLPath()}?access_token=validToken"
                ).response
            assertEquals(HttpStatusCode.NotFound, response.status())
            assertEquals(
                Json.encodeToString(
                    ErrorResponse(
                        "NET.FOLIVO.MATRIX_NOT_FOUND",
                        "no room alias #alias:server found"
                    )
                ), response.content
            )
        }
    }
}
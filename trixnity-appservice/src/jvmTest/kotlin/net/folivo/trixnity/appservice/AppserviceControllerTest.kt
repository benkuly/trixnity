package net.folivo.trixnity.appservice

import io.kotest.assertions.throwables.shouldThrow
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.appservice.rest.matrixAppserviceModule
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


private fun Application.testAppAppserviceController(appserviceService: AppserviceService) {
    install(CallLogging)
    matrixAppserviceModule(MatrixAppserviceProperties("validToken"), appserviceService)
}

class AppserviceControllerTest {

    @Test
    fun `transactions should return 200 and delegate events to handler`() = testApplication {
        val appserviceHandlerMock = mockk<AppserviceService>()
        application { testAppAppserviceController(appserviceHandlerMock) }
        val slot = slot<Flow<Event<*>>>()
        coEvery { appserviceHandlerMock.addTransactions("1", capture(slot)) } just Runs

        val response = client.put("/_matrix/app/v1/transactions/1?access_token=validToken") {
            headers {
                append(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.withCharset(Charset.defaultCharset()).toString()
                )
            }
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
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{}", response.body())
        runBlocking {
            val events = slot.captured.toList()
            assertEquals(2, events.size)
            assertTrue { events[0].content is MemberEventContent }
            assertTrue { events[1].content is RoomMessageEventContent }
        }
    }

    @Test
    fun `users should return 200 when handler is true`() = testApplication {
        val appserviceHandlerMock = mockk<AppserviceService>()
        application { testAppAppserviceController(appserviceHandlerMock) }
        coEvery { appserviceHandlerMock.hasUser(UserId("user", "server")) } returns true

        val response = client.get("/_matrix/app/v1/users/${"@user:server".encodeURLPath()}?access_token=validToken")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{}", response.body())
    }

    @Test
    fun `users should return 404 when handler is false`() = testApplication {
        val appserviceHandlerMock = mockk<AppserviceService>()
        application { testAppAppserviceController(appserviceHandlerMock) }
        coEvery { appserviceHandlerMock.hasUser(UserId("user", "server")) } returns false

        val response = shouldThrow<ClientRequestException> {
            client.get("/_matrix/app/v1/users/${"@user:server".encodeURLPath()}?access_token=validToken")
        }.response
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(
            """{"errcode":"M_NOT_FOUND","error":"user @user:server not found"}""", response.body()
        )
    }

    @Test
    fun `rooms should return 200 when handler is true`() = testApplication {
        val appserviceHandlerMock = mockk<AppserviceService>()
        application { testAppAppserviceController(appserviceHandlerMock) }
        coEvery { appserviceHandlerMock.hasRoomAlias(RoomAliasId("alias", "server")) } returns true

        val response = client.get("/_matrix/app/v1/rooms/${"#alias:server".encodeURLPath()}?access_token=validToken")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{}", response.body())
    }

    @Test
    fun `rooms should return 404 when handler is false`() = testApplication {
        val appserviceHandlerMock = mockk<AppserviceService>()
        application { testAppAppserviceController(appserviceHandlerMock) }
        coEvery { appserviceHandlerMock.hasRoomAlias(RoomAliasId("alias", "server")) } returns false

        val response = shouldThrow<ClientRequestException> {
            client.get("/_matrix/app/v1/rooms/${"#alias:server".encodeURLPath()}?access_token=validToken")
        }.response
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"errcode":"M_NOT_FOUND","error":"no room alias #alias:server found"}""", response.body())
    }
}
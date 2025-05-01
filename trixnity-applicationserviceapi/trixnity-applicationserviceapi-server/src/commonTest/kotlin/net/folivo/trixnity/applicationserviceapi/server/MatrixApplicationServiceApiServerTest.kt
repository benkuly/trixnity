package net.folivo.trixnity.applicationserviceapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


private fun Application.matrixApplicationServiceApiServerTestApplication(
    applicationServiceApiServerHandler: ApplicationServiceApiServerHandler,
) {
    matrixApplicationServiceApiServer("validToken") {
        matrixApplicationServiceApiServerRoutes(applicationServiceApiServerHandler)
    }
}

class TestApplicationServiceApiServerHandler : ApplicationServiceApiServerHandler {
    var addTransaction: Pair<String, List<RoomEvent<*>>>? = null
    var hasUser: Boolean = false
    var requestedUser: UserId? = null
    var hasRoom: Boolean = false
    var requestedRoom: RoomAliasId? = null
    var ping: String? = null

    override suspend fun addTransaction(txnId: String, events: List<RoomEvent<*>>) {
        addTransaction = txnId to events
    }

    override suspend fun hasUser(userId: UserId) {
        requestedUser = userId
        if (!hasUser) throw MatrixServerException(HttpStatusCode.NotFound, ErrorResponse.NotFound(""))
    }

    override suspend fun hasRoomAlias(roomAlias: RoomAliasId) {
        requestedRoom = roomAlias
        if (!hasRoom) throw MatrixServerException(HttpStatusCode.NotFound, ErrorResponse.NotFound(""))
    }

    override suspend fun ping(txnId: String?) {
        ping = txnId
    }

}

class MatrixApplicationServiceApiServerTest : TrixnityBaseTest() {

    @Test
    fun `should authorize`() = testApplication {
        val handler = TestApplicationServiceApiServerHandler()
        application { matrixApplicationServiceApiServerTestApplication(handler) }
        handler.hasUser = true

        val response = client.get("/_matrix/app/v1/users/${"@user:server".encodeURLPath()}")
        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `transactions should return 200 and delegate events to handler`() = testApplication {
        val handler = TestApplicationServiceApiServerHandler()
        application { matrixApplicationServiceApiServerTestApplication(handler) }

        val response = client.put("/_matrix/app/v1/transactions/1?access_token=validToken") {
            contentType(ContentType.Application.Json)
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
        assertSoftly(handler.addTransaction) {
            assertNotNull(it)
            it.first shouldBe "1"
            it.second shouldHaveSize 2
            it.second[0].content.shouldBeInstanceOf<MemberEventContent>()
            it.second[1].content.shouldBeInstanceOf<RoomMessageEventContent>()
        }
    }

    @Test
    fun `users should return 200 when handler is true`() = testApplication {
        val handler = TestApplicationServiceApiServerHandler()
        application { matrixApplicationServiceApiServerTestApplication(handler) }
        handler.hasUser = true

        val response = client.get("/_matrix/app/v1/users/${"@user:server".encodeURLPath()}?access_token=validToken")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{}", response.body())
        handler.requestedUser shouldBe UserId("@user:server")
    }

    @Test
    fun `users should return 404 when handler is false`() = testApplication {
        val handler = TestApplicationServiceApiServerHandler()
        application { matrixApplicationServiceApiServerTestApplication(handler) }
        handler.hasUser = false

        val response =
            client.get("/_matrix/app/v1/users/${"@user:server".encodeURLPath()}?access_token=validToken")
        response.status shouldBe HttpStatusCode.NotFound
        Json.decodeFromString(ErrorResponseSerializer, response.body())
            .shouldBeInstanceOf<ErrorResponse.NotFound>()
        handler.requestedUser shouldBe UserId("@user:server")
    }

    @Test
    fun `rooms should return 200 when handler is true`() = testApplication {
        val handler = TestApplicationServiceApiServerHandler()
        application { matrixApplicationServiceApiServerTestApplication(handler) }
        handler.hasRoom = true

        val response = client.get("/_matrix/app/v1/rooms/${"#alias:server".encodeURLPath()}?access_token=validToken")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{}", response.body())
        handler.requestedRoom shouldBe RoomAliasId("#alias:server")
    }

    @Test
    fun `rooms should return 404 when handler is false`() = testApplication {
        val handler = TestApplicationServiceApiServerHandler()
        application { matrixApplicationServiceApiServerTestApplication(handler) }
        handler.hasRoom = false

        val response =
            client.get("/_matrix/app/v1/rooms/${"#alias:server".encodeURLPath()}?access_token=validToken")
        response.status shouldBe HttpStatusCode.NotFound
        Json.decodeFromString(ErrorResponseSerializer, response.body())
            .shouldBeInstanceOf<ErrorResponse.NotFound>()
        handler.requestedRoom shouldBe RoomAliasId("#alias:server")
    }

    @Test
    fun ping() = testApplication {
        val handler = TestApplicationServiceApiServerHandler()
        application { matrixApplicationServiceApiServerTestApplication(handler) }

        val response = client.post("/_matrix/app/v1/ping?access_token=validToken") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "transaction_id": "1"
                    }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{}", response.body())
        handler.ping shouldBe "1"
    }
}
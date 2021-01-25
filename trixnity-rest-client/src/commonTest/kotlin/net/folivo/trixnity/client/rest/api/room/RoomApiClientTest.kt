package net.folivo.trixnity.client.rest.api.room

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.matrix.restclient.api.rooms.Membership
import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.MatrixClientProperties
import net.folivo.trixnity.client.rest.MatrixClientProperties.MatrixHomeServerProperties
import net.folivo.trixnity.client.rest.api.room.CreateRoomRequest.Invite3Pid
import net.folivo.trixnity.client.rest.api.room.JoinRoomRequest.ThirdPartySigned
import net.folivo.trixnity.client.rest.makeHttpClient
import net.folivo.trixnity.client.rest.runBlockingTest
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.m.room.MemberEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEvent.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEvent.MemberEventContent.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.MemberEvent.MemberUnsignedData
import net.folivo.trixnity.core.model.events.m.room.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEvent
import net.folivo.trixnity.core.model.events.m.room.NameEvent.NameEventContent
import net.folivo.trixnity.core.serialization.event.createEventSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RoomApiClientTest {

    private val json = Json {
        encodeDefaults = true
        serializersModule = createEventSerializersModule()
    }

    @Test
    fun shouldEncodeUrlParameter() = runBlockingTest {
        val response = NameEvent(
            id = EventId("event", "server"),
            roomId = RoomId("room", "server"),
            unsigned = StandardUnsignedData(),
            originTimestamp = 1234,
            sender = UserId("sender", "server"),
            content = NameEventContent()
        )
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/event/%24event%3Aserver?user_id=%40user%3Aserver",
                    request.url.fullPath
                )
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        matrixClient.room.getEvent(
            RoomId("room", "server"),
            EventId("event", "server"),
            asUserId = UserId("user", "server")
        )
    }

    @Test
    fun shouldGetRoomEvent() = runBlockingTest {
        val response = NameEvent(
            id = EventId("event", "server"),
            roomId = RoomId("room", "server"),
            unsigned = StandardUnsignedData(),
            originTimestamp = 1234,
            sender = UserId("sender", "server"),
            content = NameEventContent()
        )
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/event/%24event%3Aserver",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result: Event<*> = matrixClient.room.getEvent(
            RoomId("room", "server"),
            EventId("event", "server")
        )
        assertTrue(result is NameEvent)
    }

    @Test
    fun shouldGetStateEvent() = runBlockingTest {
        val response = NameEventContent("name")
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals("/_matrix/client/r0/rooms/%21room%3Aserver/state/m.room.name/", request.url.fullPath)
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.getStateEvent<NameEvent, NameEventContent>(
            roomId = RoomId("room", "server"),
        )
        assertEquals(NameEventContent::class, result::class)
    }

    @Test
    fun shouldGetCompleteState() = runBlockingTest {
        val response = listOf(
            NameEvent(
                id = EventId("event1", "server"),
                roomId = RoomId("room", "server"),
                unsigned = StandardUnsignedData(),
                originTimestamp = 12341,
                sender = UserId("sender", "server"),
                content = NameEventContent()
            ),
            MemberEvent(
                id = EventId("event2", "server"),
                roomId = RoomId("room", "server"),
                unsigned = MemberUnsignedData(),
                originTimestamp = 12342,
                sender = UserId("sender", "server"),
                relatedUser = UserId("user", "server"),
                content = MemberEventContent(membership = INVITE)
            )
        )
        println(json.encodeToString(response))
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals("/_matrix/client/r0/rooms/%21room%3Aserver/state", request.url.fullPath)
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.getState(RoomId("room", "server"))
        assertEquals(2, result.size)
        assertEquals(NameEvent::class, result[0]::class)
        assertEquals(MemberEvent::class, result[1]::class)
    }

    @Test
    fun shouldGetMembers() = runBlockingTest {
        val response = GetMembersResponse(
            listOf(
                MemberEvent(
                    id = EventId("event1", "server"),
                    roomId = RoomId("room", "server"),
                    unsigned = MemberUnsignedData(),
                    originTimestamp = 12341,
                    sender = UserId("sender", "server"),
                    relatedUser = UserId("user1", "server"),
                    content = MemberEventContent(membership = INVITE)
                ),
                MemberEvent(
                    id = EventId("event2", "server"),
                    roomId = RoomId("room", "server"),
                    unsigned = MemberUnsignedData(),
                    originTimestamp = 12342,
                    sender = UserId("sender", "server"),
                    relatedUser = UserId("user2", "server"),
                    content = MemberEventContent(membership = INVITE)
                )
            )
        )
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/members?at=someAt&membership=join",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.getMembers(
            roomId = RoomId("room", "server"),
            at = "someAt",
            membership = Membership.JOIN
        )
        assertEquals(2, result.size)
        assertEquals(MemberEvent::class, result[0]::class)
        assertEquals(MemberEvent::class, result[1]::class)
        assertEquals(EventId("event2", "server"), result[1].id)
    }

    @Test
    fun shouldGetJoinedMembers() = runBlockingTest {
        val response = GetJoinedMembersResponse(
            joined = mapOf(
                UserId("user1", "server") to GetJoinedMembersResponse.RoomMember("Unicorn"),
                UserId("user2", "server") to GetJoinedMembersResponse.RoomMember("Dino")
            )
        )
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/joined_members",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.getJoinedMembers(RoomId("room", "server"))
        assertEquals(response, result)
    }

    @Test
    fun shouldGetEvents() = runBlockingTest {
        val response = GetEventsResponse(
            start = "start",
            end = "end",
            chunk = listOf(),
            state = listOf()
        )
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/messages?from=from&dir=f&limit=10",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.getEvents(
            roomId = RoomId("room", "server"),
            from = "from",
            dir = Direction.FORWARD
        )
        assertEquals(response, result)
    }

    @Test
    fun shouldSendStateEvent() = runBlockingTest {
        val response = SendEventResponse(EventId("event", "server"))
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/state/m.room.name/someStateKey",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Put, request.method)
                assertEquals("""{"name":"name"}""", request.body.toByteArray().decodeToString())
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val eventContent = NameEventContent("name")

        val result = matrixClient.room.sendStateEvent<NameEvent, NameEventContent>(
            roomId = RoomId("room", "server"),
            eventContent = eventContent,
            stateKey = "someStateKey"
        )
        assertEquals(EventId("event", "server"), result)
    }

    @Test
    fun shouldHaveErrorWhenNoEventTypeFoundOnSendingStateEvent() = runBlockingTest {
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) { addHandler { respondOk() } })
        val eventContent = object {
            val banana: String = "yeah"
        }

        try {
            matrixClient.room.sendStateEvent(
                roomId = RoomId("room", "server"),
                eventContent = eventContent,
                stateKey = "someStateKey"
            )
        } catch (error: Throwable) {
            if (error !is IllegalArgumentException) {
                fail("error should be of type ${IllegalArgumentException::class} but was ${error::class}")
            }
        }
    }

    @Test
    fun shouldSendRoomEvent() = runBlockingTest {
        val response = SendEventResponse(EventId("event", "server"))
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/send/m.room.message/someTxnId",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Put, request.method)
                assertEquals(
                    """{"body":"someBody","format":null,"formatted_body":null,"msgtype":"m.text"}""",
                    request.body.toByteArray().decodeToString()
                )
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val eventContent = TextMessageEventContent("someBody")
        val result = matrixClient.room.sendRoomEvent<MessageEvent, TextMessageEventContent>(
            roomId = RoomId("room", "server"),
            eventContent = eventContent,
            txnId = "someTxnId"
        )
        assertEquals(EventId("event", "server"), result)
    }

    @Test
    fun shouldHaveErrorWhenNoEventTypeFoundOnSendingRoomEvent() = runBlockingTest {
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) { addHandler { respondOk() } })
        val eventContent = object {
            val banana: String = "yeah"
        }

        try {
            matrixClient.room.sendRoomEvent(
                roomId = RoomId("room", "server"),
                eventContent = eventContent
            )
        } catch (error: Throwable) {
            if (error !is IllegalArgumentException) {
                fail("error should be of type ${IllegalArgumentException::class} but was ${error::class}")
            }
        }
    }

    @Test
    fun shouldSendRedactEvent() = runBlockingTest {
        val response = SendEventResponse(EventId("event", "server"))
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/redact/%24eventToRedact%3Aserver/someTxnId",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Put, request.method)
                assertEquals("""{"reason":"someReason"}""", request.body.toByteArray().decodeToString())
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.sendRedactEvent(
            roomId = RoomId("room", "server"),
            eventId = EventId("eventToRedact", "server"),
            reason = "someReason",
            txnId = "someTxnId"
        )
        assertEquals(EventId("event", "server"), result)
    }

    @Test
    fun shouldCreateRoom() = runBlockingTest {
        val response = CreateRoomResponse(RoomId("room", "server"))
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals("/_matrix/client/r0/createRoom", request.url.fullPath)
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(
                    """
                    {
                        "visibility":"private",
                        "room_alias_name":null,
                        "name":"someRoomName",
                        "topic":null,
                        "invite":["@user1:server"],
                        "invite_3pid":[{
                            "id_server":"identityServer",
                            "id_access_token":"token",
                            "medium":"email",
                            "address":"user2@example.org"
                        }],
                        "room_version":null,
                        "creation_content":null,
                        "initial_state":null,
                        "preset":null,
                        "is_direct":true,
                        "power_level_content_override":null
                    }
                    """.trimIndent().lines().joinToString("") { it.trim() }, request.body.toByteArray().decodeToString()
                )
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.createRoom(
            visibility = Visibility.PRIVATE,
            invite = setOf(UserId("user1", "server")),
            isDirect = true,
            name = "someRoomName",
            invite3Pid = setOf(Invite3Pid("identityServer", "token", "email", "user2@example.org"))
        )
        assertEquals(RoomId("room", "server"), result)
    }

    @Test
    fun shouldSetRoomAlias() = runBlockingTest {
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/directory/room/%23unicorns%3Aserver",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Put, request.method)
                assertEquals("""{"room_id":"!room:server"}""", request.body.toByteArray().decodeToString())
                respond(
                    "{}",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        matrixClient.room.setRoomAlias(
            roomId = RoomId("room", "server"),
            roomAliasId = RoomAliasId("unicorns", "server")
        )
    }

    @Test
    fun shouldGetRoomAlias() = runBlockingTest {
        val response = GetRoomAliasResponse(
            roomId = RoomId("room", "server"),
            servers = listOf("server1", "server2")
        )
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/directory/room/%23unicorns%3Aserver",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.getRoomAlias(RoomAliasId("unicorns", "server"))
        assertEquals(response, result)
    }

    @Test
    fun shouldDeleteRoomAlias() = runBlockingTest {
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/directory/room/%23unicorns%3Aserver",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Delete, request.method)
                respond(
                    "{}",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        matrixClient.room.deleteRoomAlias(RoomAliasId("unicorns", "server"))
    }

    @Test
    fun shouldGetJoinedRooms() = runBlockingTest {
        val response = GetJoinedRoomsResponse(
            setOf(
                RoomId("room1", "server"), RoomId("room2", "server")
            )
        )
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/joined_rooms",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.getJoinedRooms().toSet()
        assertTrue { result.containsAll(setOf(RoomId("room1", "server"), RoomId("room2", "server"))) }
    }

    @Test
    fun shouldInviteUser() = runBlockingTest {
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/invite",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("""{"user_id":"@user:server"}""", request.body.toByteArray().decodeToString())
                respond(
                    "{}",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        matrixClient.room.inviteUser(RoomId("room", "server"), UserId("user", "server"))
    }

    @Test
    fun shouldJoinRoomByRoomId() = runBlockingTest {
        val response = JoinRoomResponse(RoomId("room", "server"))
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/join/%21room%3Aserver?server_name=server1.com&server_name=server2.com",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(
                    """
                    {
                      "third_party_signed":{
                        "sender":"@alice:server",
                        "mxid":"@bob:server",
                        "token":"someToken",
                        "signatures":{
                          "example.org":{
                            "ed25519:0":"some9signature"
                          }
                        }
                      }
                    }
                    """.trimIndent().lines().joinToString("") { it.trim() }, request.body.toByteArray().decodeToString()
                )
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.joinRoom(
            roomId = RoomId("room", "server"),
            serverNames = setOf("server1.com", "server2.com"),
            thirdPartySigned = ThirdPartySigned(
                sender = UserId("alice", "server"),
                mxid = UserId("bob", "server"),
                token = "someToken",
                signatures = mapOf(
                    "example.org" to
                            mapOf("ed25519:0" to "some9signature")
                )
            )
        )
        assertEquals(RoomId("room", "server"), result)
    }

    @Test
    fun shouldJoinRoomByRoomAlias() = runBlockingTest {
        val response = JoinRoomResponse(RoomId("room", "server"))
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/join/%23alias%3Aserver?server_name=server1.com&server_name=server2.com",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(
                    """
                    {
                      "third_party_signed":{
                        "sender":"@alice:server",
                        "mxid":"@bob:server",
                        "token":"someToken",
                        "signatures":{
                          "example.org":{
                            "ed25519:0":"some9signature"
                          }
                        }
                      }
                    }
                    """.trimIndent().lines().joinToString("") { it.trim() }, request.body.toByteArray().decodeToString()
                )
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        val result = matrixClient.room.joinRoom(
            roomAliasId = RoomAliasId("alias", "server"),
            serverNames = setOf("server1.com", "server2.com"),
            thirdPartySigned = ThirdPartySigned(
                sender = UserId("alice", "server"),
                mxid = UserId("bob", "server"),
                token = "someToken",
                signatures = mapOf(
                    "example.org" to
                            mapOf("ed25519:0" to "some9signature")
                )
            )
        )
        assertEquals(RoomId("room", "server"), result)
    }

    @Test
    fun shouldLeaveRoom() = runBlockingTest {
        val matrixClient = MatrixClient(makeHttpClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/%21room%3Aserver/leave",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Post, request.method)
                respond(
                    "{}",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        })
        matrixClient.room.leaveRoom(RoomId("room", "server"))
    }
}
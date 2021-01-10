package net.folivo.trixnity.client.rest.api.room

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.matrix.restclient.api.rooms.Membership
import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.MatrixClientProperties
import net.folivo.trixnity.client.rest.MatrixClientProperties.MatrixHomeServerProperties
import net.folivo.trixnity.client.rest.runBlockingTest
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.m.room.MemberEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEvent.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEvent.MemberEventContent.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.MemberEvent.MemberUnsignedData
import net.folivo.trixnity.core.model.events.m.room.NameEvent
import net.folivo.trixnity.core.model.events.m.room.NameEvent.NameEventContent
import net.folivo.trixnity.core.serialization.EventSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomApiClientTest {

    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun shouldGetSomeRoomEvent() = runBlockingTest {
        val response = NameEvent(
            id = EventId("event", "server"),
            roomId = RoomId("room", "server"),
            unsigned = StandardUnsignedData(),
            originTimestamp = 1234,
            sender = UserId("sender", "server"),
            content = NameEventContent()
        )
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals("/_matrix/client/r0/rooms/!room:server/event/\$event:server", request.url.fullPath)
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
        val result = matrixClient.room.getEvent(
            RoomId("room", "server"),
            EventId("event", "server")
        )
        assertEquals(NameEvent::class, result::class)
    }

    @Test
    fun shouldGetSomeStateEvent() = runBlockingTest {
        val response = NameEventContent("name")
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals("/_matrix/client/r0/rooms/!room:server/state/m.room.name/", request.url.fullPath)
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
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
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals("/_matrix/client/r0/rooms/!room:server/state", request.url.fullPath)
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(ListSerializer(EventSerializer()), response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
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
                    unsigned = MemberEvent.MemberUnsignedData(),
                    originTimestamp = 12341,
                    sender = UserId("sender", "server"),
                    relatedUser = UserId("user1", "server"),
                    content = MemberEventContent(membership = INVITE)
                ),
                MemberEvent(
                    id = EventId("event2", "server"),
                    roomId = RoomId("room", "server"),
                    unsigned = MemberEvent.MemberUnsignedData(),
                    originTimestamp = 12342,
                    sender = UserId("sender", "server"),
                    relatedUser = UserId("user2", "server"),
                    content = MemberEventContent(membership = INVITE)
                )
            )
        )
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/!room:server/members?at=someAt&membership=join",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
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
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/!room:server/joined_members",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
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
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/rooms/!room:server/messages?from=from&dir=f&limit=10",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
        val result = matrixClient.room.getEvents(
            roomId = RoomId("room", "server"),
            from = "from",
            dir = Direction.FORWARD
        )
        assertEquals(response, result)
    }
//
//    @Test
//    fun `should send state event`() {
//        val response = SendEventResponse(EventId("event", "server"))
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val eventContent = NameEventContent("name")
//        val result = runBlocking {
//            matrixClient.roomsApi.sendStateEvent(
//                roomId = RoomId("room", "server"),
//                eventContent = eventContent,
//                stateKey = "someStateKey"
//            )
//        }
//
//        result.shouldBe(EventId("event", "server"))
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/rooms/%21room%3Aserver/state/m.room.name/someStateKey")
//        assertThat(request.body.readUtf8()).isEqualTo(objectMapper.writeValueAsString(eventContent))
//        assertThat(request.method).isEqualTo(HttpMethod.PUT.toString())
//    }
//
//    @Test
//    fun `should have error when no eventType found on sending state event`() {
//        val response = SendEventResponse(EventId("event", "server"))
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val eventContent = object : StateEventContent {
//            val banana: String = "yeah"
//        }
//
//        try {
//            runBlocking {
//                matrixClient.roomsApi.sendStateEvent(
//                    roomId = RoomId("room", "server"),
//                    eventContent = eventContent,
//                    stateKey = "someStateKey"
//                )
//            }
//            fail<Unit>("error has error")
//        } catch (error: Throwable) {
//            if (error !is IllegalArgumentException) {
//                fail<Unit>("error should be of type ${IllegalArgumentException::class} but was ${error::class}")
//            }
//        }
//    }
//
//    @Test
//    fun `should send room event`() {
//        val response = SendEventResponse(EventId("event", "server"))
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val eventContent = TextMessageEventContent("someBody")
//        val result = runBlocking {
//            matrixClient.roomsApi.sendRoomEvent(
//                roomId = RoomId("room", "server"),
//                eventContent = eventContent,
//                txnId = "someTxnId"
//            )
//        }
//
//        result.shouldBe(EventId("event", "server"))
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/rooms/%21room%3Aserver/send/m.room.message/someTxnId")
//        assertThat(request.body.readUtf8()).isEqualTo(objectMapper.writeValueAsString(eventContent))
//        assertThat(request.method).isEqualTo(HttpMethod.PUT.toString())
//    }
//
//    @Test
//    fun `should have error when no eventType found on sending room event`() {
//        val response = SendEventResponse(EventId("event", "server"))
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val eventContent = object : RoomEventContent {
//            val banana: String = "yeah"
//        }
//        try {
//            runBlocking {
//                matrixClient.roomsApi.sendRoomEvent(
//                    roomId = RoomId("room", "server"),
//                    eventContent = eventContent
//                )
//            }
//            fail<Unit>("error has error")
//        } catch (error: Throwable) {
//            if (error !is IllegalArgumentException) {
//                fail<Unit>("error should be of type ${IllegalArgumentException::class} but was ${error::class}")
//            }
//        }
//    }
//
//    @Test
//    fun `should send redact event`() {
//        val response = SendEventResponse(EventId("event", "server"))
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val result = runBlocking {
//            matrixClient.roomsApi.sendRedactEvent(
//                roomId = RoomId("room", "server"),
//                eventId = EventId("eventToRedact", "server"),
//                reason = "someReason",
//                txnId = "someTxnId"
//            )
//        }
//
//        result.shouldBe(EventId("event", "server"))
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/rooms/%21room%3Aserver/redact/%24eventToRedact%3Aserver/someTxnId")
//        assertThat(request.body.readUtf8()).isEqualTo("""{"reason":"someReason"}""")
//        assertThat(request.method).isEqualTo(HttpMethod.PUT.toString())
//    }
//
//    @Test
//    fun `should create room`() {
//        val response = CreateRoomResponse(RoomId("room", "server"))
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val result = runBlocking {
//            matrixClient.roomsApi.createRoom(
//                visibility = Visibility.PRIVATE,
//                invite = setOf(UserId("user1", "server")),
//                isDirect = true,
//                name = "someRoomName",
//                invite3Pid = setOf(Invite3Pid("identityServer", "token", "email", "user2@example.org"))
//            )
//        }
//
//        result.shouldBe(RoomId("room", "server"))
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/createRoom")
//
//        val expectedRequest = """
//            {
//                "visibility" : "private",
//                "name" : "someRoomName",
//                "invite" : ["@user1:server"],
//                "invite_3pid" : [{
//                    "id_server" : "identityServer",
//                    "id_access_token" : "token",
//                    "medium" : "email",
//                    "address" : "user2@example.org"
//                }],
//                "is_direct" : true
//            }
//        """.trimIndent()
//        assertThat(request.body.readUtf8()).isEqualTo(
//            objectMapper.readValue<JsonNode>(expectedRequest).toString()
//        )
//        assertThat(request.method).isEqualTo(HttpMethod.POST.toString())
//    }
//
//    @Test
//    fun `should set room alias`() {
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody("{}")
//        )
//
//        runBlocking {
//            matrixClient.roomsApi.setRoomAlias(
//                roomId = RoomId("room", "server"),
//                roomAliasId = RoomAliasId("unicorns", "server")
//            )
//        }
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/directory/room/%23unicorns%3Aserver")
//        assertThat(request.body.readUtf8()).isEqualTo("""{"room_id":"!room:server"}""")
//        assertThat(request.method).isEqualTo(HttpMethod.PUT.toString())
//    }
//
//    @Test
//    fun `should get room alias`() {
//        val response = GetRoomAliasResponse(
//            roomId = RoomId("room", "server"),
//            servers = listOf("server1", "server2")
//        )
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val result = runBlocking { matrixClient.roomsApi.getRoomAlias(RoomAliasId("unicorns", "server")) }
//
//        result.shouldBe(response)
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/directory/room/%23unicorns%3Aserver")
//        assertThat(request.method).isEqualTo(HttpMethod.GET.toString())
//    }
//
//    @Test
//    fun `should delete room alias`() {
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody("{}")
//        )
//
//        runBlocking { matrixClient.roomsApi.deleteRoomAlias(RoomAliasId("unicorns", "server")) }
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/directory/room/%23unicorns%3Aserver")
//        assertThat(request.method).isEqualTo(HttpMethod.DELETE.toString())
//    }
//
//    @Test
//    fun `should get joined rooms`() {
//        val response = GetJoinedRoomsResponse(
//            setOf(
//                RoomId("room1", "server"), RoomId("room2", "server")
//            )
//        )
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val result = runBlocking { matrixClient.roomsApi.getJoinedRooms().toSet() }
//
//        result.shouldContainExactlyInAnyOrder(RoomId("room1", "server"), RoomId("room2", "server"))
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/joined_rooms")
//        assertThat(request.method).isEqualTo(HttpMethod.GET.toString())
//    }
//
//    @Test
//    fun `should invite user`() {
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody("{}")
//        )
//
//        runBlocking { matrixClient.roomsApi.inviteUser(RoomId("room", "server"), UserId("user", "server")) }
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/rooms/%21room%3Aserver/invite")
//        assertThat(request.body.readUtf8()).isEqualTo("""{"user_id":"@user:server"}""")
//        assertThat(request.method).isEqualTo(HttpMethod.POST.toString())
//    }
//
//    @Test
//    fun `should join room by room id`() {
//        val response = JoinRoomResponse(RoomId("room", "server"))
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val result = runBlocking {
//            matrixClient.roomsApi.joinRoom(
//                roomId = RoomId("room", "server"),
//                serverNames = setOf("server"),
//                thirdPartySigned = ThirdPartySigned(
//                    sender = UserId("alice", "server"),
//                    mxid = UserId("bob", "server"),
//                    token = "someToken",
//                    signatures = mapOf(
//                        "example.org" to
//                                mapOf("ed25519:0" to "some9signature")
//                    )
//                )
//            )
//        }
//
//        val expectedRequest = """
//            {
//              "third_party_signed": {
//                "sender": "@alice:server",
//                "mxid": "@bob:server",
//                "token": "someToken",
//                "signatures": {
//                  "example.org": {
//                    "ed25519:0": "some9signature"
//                  }
//                }
//              }
//            }
//
//        """.trimIndent()
//
//        result.shouldBe(RoomId("room", "server"))
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/join/%21room%3Aserver?server_name=server")
//        assertThat(request.body.readUtf8()).isEqualTo(
//            objectMapper.readValue<JsonNode>(expectedRequest).toString()
//        )
//        assertThat(request.method).isEqualTo(HttpMethod.POST.toString())
//    }
//
//    @Test
//    fun `should join room by room alias`() {
//        val response = JoinRoomResponse(RoomId("room", "server"))
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody(objectMapper.writeValueAsString(response))
//        )
//
//        val result = runBlocking {
//            matrixClient.roomsApi.joinRoom(
//                roomAliasId = RoomAliasId("alias", "server"),
//                serverNames = setOf("server"),
//                thirdPartySigned = ThirdPartySigned(
//                    sender = UserId("alice", "server"),
//                    mxid = UserId("bob", "server"),
//                    token = "someToken",
//                    signatures = mapOf(
//                        "example.org" to
//                                mapOf("ed25519:0" to "some9signature")
//                    )
//                )
//            )
//        }
//
//        val expectedRequest = """
//            {
//              "third_party_signed": {
//                "sender": "@alice:server",
//                "mxid": "@bob:server",
//                "token": "someToken",
//                "signatures": {
//                  "example.org": {
//                    "ed25519:0": "some9signature"
//                  }
//                }
//              }
//            }
//
//        """.trimIndent()
//
//        result.shouldBe(RoomId("room", "server"))
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/join/%23alias%3Aserver?server_name=server")
//        assertThat(request.body.readUtf8()).isEqualTo(
//            objectMapper.readValue<JsonNode>(expectedRequest).toString()
//        )
//        assertThat(request.method).isEqualTo(HttpMethod.POST.toString())
//    }
//
//    @Test
//    fun `should leave room`() {
//        mockWebServer.enqueue(
//            MockResponse()
//                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
//                .setBody("{}")
//        )
//
//        runBlocking { matrixClient.roomsApi.leaveRoom(RoomId("room", "server")) }
//
//        val request = mockWebServer.takeRequest()
//        assertThat(request.path).isEqualTo("/_matrix/client/r0/rooms/%21room%3Aserver/leave")
//        assertThat(request.method).isEqualTo(HttpMethod.POST.toString())
//    }
}
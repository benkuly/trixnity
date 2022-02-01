package net.folivo.trixnity.client.api

import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import net.folivo.trixnity.client.api.model.rooms.*
import net.folivo.trixnity.client.api.model.rooms.CreateRoomRequest.Invite3Pid
import net.folivo.trixnity.client.api.model.rooms.JoinRoomRequest.ThirdParty
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalSerializationApi::class, ExperimentalCoroutinesApi::class)
class RoomsApiClientTest {

    private val json = createMatrixJson()

    @Test
    fun shouldEncodeUrlParameter() = runTest {
        val response = StateEvent(
            id = EventId("event"),
            roomId = RoomId("room", "server"),
            unsigned = UnsignedStateEventData(),
            originTimestamp = 1234,
            sender = UserId("sender", "server"),
            content = NameEventContent("name"),
            stateKey = ""
        )
        val serializer = json.serializersModule.getContextual(StateEvent::class)
        requireNotNull(serializer)
        val matrixRestClient = MatrixApiClient(
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/event/%24event?user_id=%40user%3Aserver",
                            request.url.fullPath
                        )
                        respond(
                            json.encodeToString(serializer, response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
            baseUrl = Url("https://matrix.host"),
        )
        matrixRestClient.rooms.getEvent(
            RoomId("room", "server"),
            EventId("\$event"),
            asUserId = UserId("user", "server")
        ).getOrThrow()
    }

    @Test
    fun shouldGetRoomEvent() = runTest {
        val response = StateEvent(
            id = EventId("event"),
            roomId = RoomId("room", "server"),
            unsigned = UnsignedStateEventData(),
            originTimestamp = 1234,
            sender = UserId("sender", "server"),
            content = NameEventContent("a"),
            stateKey = ""
        )
        val serializer = json.serializersModule.getContextual(StateEvent::class)
        requireNotNull(serializer)
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/event/%24event",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            json.encodeToString(serializer, response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
        )
        val result: Event<*> = matrixRestClient.rooms.getEvent(
            RoomId("room", "server"),
            EventId("\$event")
        ).getOrThrow()
        assertTrue(result is StateEvent && result.content is NameEventContent)
    }

    @Test
    fun shouldGetStateEvent() = runTest {
        val response = NameEventContent("name")
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/state/m.room.name/",
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
            })
        val result = matrixRestClient.rooms.getStateEvent<NameEventContent>(
            roomId = RoomId("room", "server"),
        ).getOrThrow()
        assertEquals(NameEventContent::class, result::class)
    }

    @ExperimentalSerializationApi
    @Test
    fun shouldGetCompleteState() = runTest {
        val response: List<StateEvent<StateEventContent>> = listOf(
            StateEvent(
                id = EventId("event1"),
                roomId = RoomId("room", "server"),
                unsigned = UnsignedStateEventData(),
                originTimestamp = 12341,
                sender = UserId("sender", "server"),
                content = NameEventContent("a"),
                stateKey = ""
            ),
            StateEvent(
                id = EventId("event2"),
                roomId = RoomId("room", "server"),
                unsigned = UnsignedStateEventData(),
                originTimestamp = 12342,
                sender = UserId("sender", "server"),
                stateKey = UserId("user", "server").full,
                content = MemberEventContent(membership = INVITE)
            )
        )
        val serializer = json.serializersModule.getContextual(StateEvent::class)
        requireNotNull(serializer)
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/rooms/%21room%3Aserver/state", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            json.encodeToString(
                                ListSerializer(serializer),
                                response
                            ),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.rooms.getState(RoomId("room", "server")).getOrThrow().toList()
        assertEquals(2, result.size)
        assertEquals(NameEventContent::class, result[0].content::class)
        assertEquals(MemberEventContent::class, result[1].content::class)
    }

    @Test
    fun shouldGetMembers() = runTest {
        val response = GetMembersResponse(
            listOf(
                StateEvent(
                    id = EventId("event1"),
                    roomId = RoomId("room", "server"),
                    unsigned = UnsignedStateEventData(),
                    originTimestamp = 12341,
                    sender = UserId("sender", "server"),
                    stateKey = UserId("user1", "server").full,
                    content = MemberEventContent(membership = INVITE)
                ),
                StateEvent(
                    id = EventId("event2"),
                    roomId = RoomId("room", "server"),
                    unsigned = UnsignedStateEventData(),
                    originTimestamp = 12342,
                    sender = UserId("sender", "server"),
                    stateKey = UserId("user2", "server").full,
                    content = MemberEventContent(membership = INVITE)
                )
            )
        )
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/members?at=someAt&membership=join",
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
            })
        val result = matrixRestClient.rooms.getMembers(
            roomId = RoomId("room", "server"),
            at = "someAt",
            membership = Membership.JOIN
        ).getOrThrow().toList()
        assertEquals(2, result.size)
        assertEquals(MemberEventContent::class, result[0].content::class)
        assertEquals(MemberEventContent::class, result[1].content::class)
        assertEquals(EventId("event2"), result[1].id)
    }

    @Test
    fun shouldGetJoinedMembers() = runTest {
        val response = GetJoinedMembersResponse(
            joined = mapOf(
                UserId(
                    "user1",
                    "server"
                ) to GetJoinedMembersResponse.RoomMember("Unicorn"),
                UserId(
                    "user2",
                    "server"
                ) to GetJoinedMembersResponse.RoomMember("Dino")
            )
        )
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/joined_members",
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
            })
        val result = matrixRestClient.rooms.getJoinedMembers(RoomId("room", "server")).getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldGetEvents() = runTest {
        val response = GetEventsResponse(
            start = "start",
            end = "end",
            chunk = listOf(
                MessageEvent(
                    TextMessageEventContent("hi"),
                    EventId("event"),
                    UserId("user", "server"),
                    RoomId("room", "server"),
                    1234L
                )
            ),
            state = listOf(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("event"),
                    UserId("user", "server"),
                    RoomId("room", "server"),
                    1234L,
                    stateKey = UserId("dino", "server").full
                )
            )
        )
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/messages?from=from&dir=f&limit=10",
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
            })
        val result = matrixRestClient.rooms.getEvents(
            roomId = RoomId("room", "server"),
            from = "from",
            dir = Direction.FORWARD
        ).getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldSendStateEvent() = runTest {
        val response = SendEventResponse(EventId("event"))
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/state/m.room.name/someStateKey",
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
                }
            })
        val eventContent = NameEventContent("name")

        val result = matrixRestClient.rooms.sendStateEvent(
            roomId = RoomId("room", "server"),
            eventContent = eventContent,
            stateKey = "someStateKey"
        ).getOrThrow()
        assertEquals(EventId("event"), result)
    }

    @Test
    fun shouldHaveErrorWhenNoEventTypeFoundOnSendingStateEvent() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) { engine { addHandler { respondOk() } } })
        val eventContent = object : StateEventContent {
            val banana: String = "yeah"
        }

        try {
            matrixRestClient.rooms.sendStateEvent(
                roomId = RoomId("room", "server"),
                eventContent = eventContent,
                stateKey = "someStateKey"
            ).getOrThrow()
        } catch (error: Throwable) {
            if (error !is IllegalArgumentException) {
                fail("error should be of type ${IllegalArgumentException::class} but was ${error::class}")
            }
        }
    }

    @Test
    fun shouldSendRoomEvent() = runTest {
        val response = SendEventResponse(EventId("event"))
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/send/m.room.message/someTxnId",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals(
                            """{"body":"someBody","msgtype":"m.text"}""",
                            request.body.toByteArray().decodeToString()
                        )
                        respond(
                            json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val eventContent = TextMessageEventContent("someBody")
        val result = matrixRestClient.rooms.sendMessageEvent(
            roomId = RoomId("room", "server"),
            eventContent = eventContent,
            txnId = "someTxnId"
        ).getOrThrow()
        assertEquals(EventId("event"), result)
    }

    @Test
    fun shouldHaveErrorWhenNoEventTypeFoundOnSendingRoomEvent() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) { engine { addHandler { respondOk() } } })
        val eventContent = object : MessageEventContent {
            val banana: String = "yeah"
            override val relatesTo = RelatesTo.Reference(EventId("$1event"))
        }

        try {
            matrixRestClient.rooms.sendMessageEvent(
                roomId = RoomId("room", "server"),
                eventContent = eventContent
            ).getOrThrow()
        } catch (error: Throwable) {
            if (error !is IllegalArgumentException) {
                fail("error should be of type ${IllegalArgumentException::class} but was ${error::class}")
            }
        }
    }

    @Test
    fun shouldSendRedactEvent() = runTest {
        val response = SendEventResponse(EventId("event"))
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/redact/%24eventToRedact/someTxnId",
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
                }
            })
        val result = matrixRestClient.rooms.redactEvent(
            roomId = RoomId("room", "server"),
            eventId = EventId("\$eventToRedact"),
            reason = "someReason",
            txnId = "someTxnId"
        ).getOrThrow()
        assertEquals(EventId("event"), result)
    }

    @Test
    fun shouldCreateRoom() = runTest {
        val response = CreateRoomResponse(RoomId("room", "server"))
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/createRoom", request.url.fullPath)
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(
                            """
                    {
                        "visibility":"private",
                        "name":"someRoomName",
                        "invite":["@user1:server"],
                        "invite_3pid":[{
                            "id_server":"identityServer",
                            "id_access_token":"token",
                            "medium":"email",
                            "address":"user2@example.org"
                        }],
                        "is_direct":true
                    }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                        )
                        respond(
                            json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.rooms.createRoom(
            visibility = Visibility.PRIVATE,
            invite = setOf(UserId("user1", "server")),
            isDirect = true,
            name = "someRoomName",
            invite3Pid = setOf(Invite3Pid("identityServer", "token", "email", "user2@example.org"))
        ).getOrThrow()
        assertEquals(RoomId("room", "server"), result)
    }

    @Test
    fun shouldSetRoomAlias() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/directory/room/%23unicorns%3Aserver",
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
                }
            })
        matrixRestClient.rooms.setRoomAlias(
            roomId = RoomId("room", "server"),
            roomAliasId = RoomAliasId("unicorns", "server")
        ).getOrThrow()
    }

    @Test
    fun shouldGetRoomAlias() = runTest {
        val response = GetRoomAliasResponse(
            roomId = RoomId("room", "server"),
            servers = listOf("server1", "server2")
        )
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/directory/room/%23unicorns%3Aserver",
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
            })
        val result = matrixRestClient.rooms.getRoomAlias(RoomAliasId("unicorns", "server")).getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldDeleteRoomAlias() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/directory/room/%23unicorns%3Aserver",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Delete, request.method)
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.deleteRoomAlias(RoomAliasId("unicorns", "server")).getOrThrow()
    }

    @Test
    fun shouldGetJoinedRooms() = runTest {
        val response = GetJoinedRoomsResponse(
            setOf(
                RoomId("room1", "server"), RoomId("room2", "server")
            )
        )
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/joined_rooms",
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
            })
        val result = matrixRestClient.rooms.getJoinedRooms().getOrThrow().toSet()
        assertTrue { result.containsAll(setOf(RoomId("room1", "server"), RoomId("room2", "server"))) }
    }

    @Test
    fun shouldInviteUser() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/invite",
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
                }
            })
        matrixRestClient.rooms.inviteUser(RoomId("room", "server"), UserId("user", "server")).getOrThrow()
    }

    @Test
    fun shouldKickUser() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/kick",
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
                }
            })
        matrixRestClient.rooms.kickUser(RoomId("room", "server"), UserId("user", "server"))
    }

    @Test
    fun shouldBanUser() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/ban",
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
                }
            })
        matrixRestClient.rooms.banUser(RoomId("room", "server"), UserId("user", "server"))
    }

    @Test
    fun shouldUnbanUser() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/unban",
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
                }
            })
        matrixRestClient.rooms.unbanUser(RoomId("room", "server"), UserId("user", "server"))
    }

    @Test
    fun shouldJoinRoomByRoomId() = runTest {
        val response = JoinRoomResponse(RoomId("room", "server"))
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/join/%21room%3Aserver?server_name=server1.com&server_name=server2.com",
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
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                        )
                        respond(
                            json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.rooms.joinRoom(
            roomId = RoomId("room", "server"),
            serverNames = setOf("server1.com", "server2.com"),
            thirdPartySigned = Signed(
                ThirdParty(
                    sender = UserId("alice", "server"),
                    mxid = UserId("bob", "server"),
                    token = "someToken"
                ),
                mapOf(
                    "example.org" to
                            keysOf(Key.Ed25519Key("0", "some9signature"))
                )
            )
        ).getOrThrow()
        assertEquals(RoomId("room", "server"), result)
    }

    @Test
    fun shouldJoinRoomByRoomAlias() = runTest {
        val response = JoinRoomResponse(RoomId("room", "server"))
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/join/%23alias%3Aserver?server_name=server1.com&server_name=server2.com",
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
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                        )
                        respond(
                            json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.rooms.joinRoom(
            roomAliasId = RoomAliasId("alias", "server"),
            serverNames = setOf("server1.com", "server2.com"),
            thirdPartySigned = Signed(
                ThirdParty(
                    sender = UserId("alice", "server"),
                    mxid = UserId("bob", "server"),
                    token = "someToken"
                ),
                mapOf(
                    "example.org" to
                            keysOf(Key.Ed25519Key("0", "some9signature"))
                )
            )
        ).getOrThrow()
        assertEquals(RoomId("room", "server"), result)
    }

    @Test
    fun shouldKnockRoomByRoomId() = runTest {
        val response = JoinRoomResponse(RoomId("room", "server"))
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/knock/%21room%3Aserver?server_name=server1.com&server_name=server2.com",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(
                            """
                    {
                      "reason":"reason"
                    }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                        )
                        respond(
                            json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.rooms.knockRoom(
            roomId = RoomId("room", "server"),
            serverNames = setOf("server1.com", "server2.com"),
            reason = "reason"
        ).getOrThrow()
        assertEquals(RoomId("room", "server"), result)
    }

    @Test
    fun shouldKnockRoomByRoomAlias() = runTest {
        val response = JoinRoomResponse(RoomId("room", "server"))
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/knock/%23alias%3Aserver?server_name=server1.com&server_name=server2.com",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(
                            """
                    {
                      "reason":"reason"
                    }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                        )
                        respond(
                            json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.rooms.knockRoom(
            roomAliasId = RoomAliasId("alias", "server"),
            serverNames = setOf("server1.com", "server2.com"),
            reason = "reason"
        ).getOrThrow()
        assertEquals(RoomId("room", "server"), result)
    }

    @Test
    fun shouldLeaveRoom() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/leave",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Post, request.method)
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.leaveRoom(RoomId("room", "server")).getOrThrow()
    }

    @Test
    fun shouldForgetRoom() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/forget",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Post, request.method)
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.forgetRoom(RoomId("room", "server"))
    }

    @Test
    fun shouldSetReceipt() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/receipt/m.read/%24event",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Post, request.method)
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.setReceipt(RoomId("room", "server"), EventId("\$event")).getOrThrow()
    }

    @Test
    fun shouldSetReadMarkers() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/read_markers",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(
                            """
                    {
                      "m.fully_read":"${'$'}event",
                      "m.read":"${'$'}event"
                    }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                        )
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.setReadMarkers(RoomId("room", "server"), EventId("\$event")).getOrThrow()
    }

    @Test
    fun shouldGetAccountData() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_read",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """{"event_id":"$1event"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.getAccountData<FullyReadEventContent>(
            RoomId("room", "server"),
            UserId("alice", "example.com")
        ).getOrThrow().shouldBe(
            FullyReadEventContent(EventId("$1event"))
        )
    }

    @Test
    fun shouldGetAccountDataWithKey() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_readkey",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """{"event_id":"$1event"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.getAccountData<FullyReadEventContent>(
            RoomId("room", "server"),
            UserId("alice", "example.com"),
            key = "key"
        ).getOrThrow().shouldBe(
            FullyReadEventContent(EventId("$1event"))
        )
    }

    @Test
    fun shouldSetAccountData() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_read",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals(
                            """{"event_id":"$1event"}""",
                            request.body.toByteArray().decodeToString()
                        )
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.setAccountData(
            FullyReadEventContent(EventId("$1event")),
            RoomId("room", "server"),
            UserId("alice", "example.com")
        ).getOrThrow()
    }

    @Test
    fun shouldSetAccountDataWithKey() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_readkey",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals(
                            """{"event_id":"$1event"}""",
                            request.body.toByteArray().decodeToString()
                        )
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.setAccountData(
            FullyReadEventContent(EventId("$1event")),
            RoomId("room", "server"),
            UserId("alice", "example.com"),
            key = "key"
        ).getOrThrow()
    }

    @Test
    fun shouldSetUserIsTyping() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/v3/rooms/%21room%3Aserver/typing/%40alice%3Aexample%2Ecom",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals(
                            """{"typing":true,"timeout":10000}""",
                            request.body.toByteArray().decodeToString()
                        )
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.rooms.setUserIsTyping(
            RoomId("room", "server"),
            UserId("alice", "example.com"),
            typing = true,
            timeout = 10_000,
        ).getOrThrow()
    }
}
package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.events.m.space.ChildEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalSerializationApi::class, ExperimentalCoroutinesApi::class)
class RoomsApiClientTest {

    private val json = createMatrixEventJson()

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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/event/${'$'}event?user_id=%40user%3Aserver",
                        request.url.fullPath
                    )
                    respond(
                        json.encodeToString(serializer, response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/event/${'$'}event",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        json.encodeToString(serializer, response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/state/m.room.name/",
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
                content = MemberEventContent(membership = Membership.INVITE)
            )
        )
        val serializer = json.serializersModule.getContextual(StateEvent::class)
        requireNotNull(serializer)
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/rooms/!room:server/state", request.url.fullPath)
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
            })
        val result = matrixRestClient.rooms.getState(RoomId("room", "server")).getOrThrow().toList()
        assertEquals(2, result.size)
        assertEquals(NameEventContent::class, result[0].content::class)
        assertEquals(MemberEventContent::class, result[1].content::class)
    }

    @Test
    fun shouldGetMembers() = runTest {
        val response = GetMembers.Response(
            setOf(
                StateEvent(
                    id = EventId("event1"),
                    roomId = RoomId("room", "server"),
                    unsigned = UnsignedStateEventData(),
                    originTimestamp = 12341,
                    sender = UserId("sender", "server"),
                    stateKey = UserId("user1", "server").full,
                    content = MemberEventContent(membership = Membership.INVITE)
                ),
                StateEvent(
                    id = EventId("event2"),
                    roomId = RoomId("room", "server"),
                    unsigned = UnsignedStateEventData(),
                    originTimestamp = 12342,
                    sender = UserId("sender", "server"),
                    stateKey = UserId("user2", "server").full,
                    content = MemberEventContent(membership = Membership.INVITE)
                )
            )
        )
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/members?at=someAt&membership=join",
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
        val response = GetJoinedMembers.Response(
            joined = mapOf(
                UserId(
                    "user1",
                    "server"
                ) to GetJoinedMembers.Response.RoomMember("Unicorn"),
                UserId(
                    "user2",
                    "server"
                ) to GetJoinedMembers.Response.RoomMember("Dino")
            )
        )
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/joined_members",
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
        val result = matrixRestClient.rooms.getJoinedMembers(RoomId("room", "server")).getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldGetEvents() = runTest {
        val response = GetEvents.Response(
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
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("event"),
                    UserId("user", "server"),
                    RoomId("room", "server"),
                    1234L,
                    stateKey = UserId("dino", "server").full
                )
            )
        )
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/messages?from=from&dir=f&limit=10",
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
        val result = matrixRestClient.rooms.getEvents(
            roomId = RoomId("room", "server"),
            from = "from",
            dir = GetEvents.Direction.FORWARDS,
            limit = 10
        ).getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldGetRelations() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v1/rooms/!room:server/relations/${'$'}1event?from=from&limit=10",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "chunk": [
                                {
                                  "content": {
                                    "body":"hi",
                                    "msgtype":"m.text"
                                  },
                                  "event_id": "$2event",
                                  "origin_server_ts": 1234,
                                  "room_id": "!room:server",
                                  "sender": "@user:server",
                                  "type": "m.room.message"
                                }
                              ],
                              "next_batch": "end",
                              "prev_batch": "start"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getRelations(
            roomId = RoomId("room", "server"),
            eventId = EventId("$1event"),
            from = "from",
            limit = 10
        ).getOrThrow() shouldBe GetRelationsResponse(
            start = "start",
            end = "end",
            chunk = listOf(
                MessageEvent(
                    TextMessageEventContent("hi"),
                    EventId("$2event"),
                    UserId("user", "server"),
                    RoomId("room", "server"),
                    1234L
                )
            )
        )
    }

    @Test
    fun shouldGetRelationsByRelationType() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v1/rooms/!room:server/relations/${'$'}1event/m.reference?from=from&limit=10",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "chunk": [
                                {
                                  "content": {
                                    "body":"hi",
                                    "msgtype":"m.text"
                                  },
                                  "event_id": "$2event",
                                  "origin_server_ts": 1234,
                                  "room_id": "!room:server",
                                  "sender": "@user:server",
                                  "type": "m.room.message"
                                }
                              ],
                              "next_batch": "end",
                              "prev_batch": "start"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getRelations(
            roomId = RoomId("room", "server"),
            eventId = EventId("$1event"),
            relationType = RelationType.Reference,
            from = "from",
            limit = 10
        ).getOrThrow() shouldBe GetRelationsResponse(
            start = "start",
            end = "end",
            chunk = listOf(
                MessageEvent(
                    TextMessageEventContent("hi"),
                    EventId("$2event"),
                    UserId("user", "server"),
                    RoomId("room", "server"),
                    1234L
                )
            )
        )
    }

    @Test
    fun shouldGetRelationsByRelationTypeAndEventType() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v1/rooms/!room:server/relations/${'$'}1event/m.reference/m.room.message?from=from&limit=10",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "chunk": [
                                {
                                  "content": {
                                    "body":"hi",
                                    "msgtype":"m.text"
                                  },
                                  "event_id": "$2event",
                                  "origin_server_ts": 1234,
                                  "room_id": "!room:server",
                                  "sender": "@user:server",
                                  "type": "m.room.message"
                                }
                              ],
                              "next_batch": "end",
                              "prev_batch": "start"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getRelationsByType<RoomMessageEventContent>(
            roomId = RoomId("room", "server"),
            eventId = EventId("$1event"),
            relationType = RelationType.Reference,
            from = "from",
            limit = 10
        ).getOrThrow() shouldBe GetRelationsResponse(
            start = "start",
            end = "end",
            chunk = listOf(
                MessageEvent(
                    TextMessageEventContent("hi"),
                    EventId("$2event"),
                    UserId("user", "server"),
                    RoomId("room", "server"),
                    1234L
                )
            )
        )
    }

    @Test
    fun shouldGetThreads() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v1/rooms/!room:server/threads?from=from&include=all&limit=10",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "chunk": [
                                {
                                  "content": {
                                    "body": "hi",
                                    "msgtype": "m.text"
                                  },
                                  "event_id": "$2event",
                                  "origin_server_ts": 1234,
                                  "room_id": "!room:server",
                                  "sender": "@user:server",
                                  "type": "m.room.message"
                                }
                              ],
                              "next_batch": "end"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getThreads(
            roomId = RoomId("room", "server"),
            from = "from",
            include = GetThreads.Include.ALL,
            limit = 10
        ).getOrThrow() shouldBe GetThreads.Response(
            end = "end",
            chunk = listOf(
                MessageEvent(
                    TextMessageEventContent("hi"),
                    EventId("$2event"),
                    UserId("user", "server"),
                    RoomId("room", "server"),
                    1234L
                )
            )
        )
    }

    @Test
    fun shouldSendStateEvent() = runTest {
        val response = SendEventResponse(EventId("event"))
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/state/m.room.name/someStateKey",
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

        val result = matrixRestClient.rooms.sendStateEvent(
            roomId = RoomId("room", "server"),
            eventContent = eventContent,
            stateKey = "someStateKey"
        ).getOrThrow()
        assertEquals(EventId("event"), result)
    }

    @Test
    fun shouldHaveErrorWhenNoEventTypeFoundOnSendingStateEvent() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory { addHandler { respondOk() } })
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/send/m.room.message/someTxnId",
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory { addHandler { respondOk() } })
        val eventContent = object : MessageEventContent {
            val banana: String = "yeah"
            override val relatesTo = RelatesTo.Reference(EventId("$1event"))
            override val mentions: Mentions? = null
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/redact/${'$'}eventToRedact/someTxnId",
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
        val response = CreateRoom.Response(RoomId("room", "server"))
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
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
            })
        val result = matrixRestClient.rooms.createRoom(
            visibility = DirectoryVisibility.PRIVATE,
            invite = setOf(UserId("user1", "server")),
            isDirect = true,
            name = "someRoomName",
            inviteThirdPid = setOf(
                CreateRoom.Request.InviteThirdPid(
                    "identityServer",
                    "token",
                    "email",
                    "user2@example.org"
                )
            )
        ).getOrThrow()
        assertEquals(RoomId("room", "server"), result)
    }

    @Test
    fun shouldSetRoomAlias() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/directory/room/%23unicorns:server",
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
        matrixRestClient.rooms.setRoomAlias(
            roomId = RoomId("room", "server"),
            roomAliasId = RoomAliasId("unicorns", "server")
        ).getOrThrow()
    }

    @Test
    fun shouldGetRoomAlias() = runTest {
        val response = GetRoomAlias.Response(
            roomId = RoomId("room", "server"),
            servers = listOf("server1", "server2")
        )
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/directory/room/%23unicorns:server",
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
        val result = matrixRestClient.rooms.getRoomAlias(RoomAliasId("unicorns", "server")).getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldGetRoomAliases() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/aliases",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "aliases": [
                                "#somewhere:example.com",
                                "#another:example.com",
                                "#hat_trick:example.com"
                              ]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getRoomAliases(RoomId("room", "server")).getOrThrow() shouldBe setOf(
            RoomAliasId("#somewhere:example.com"),
            RoomAliasId("#another:example.com"),
            RoomAliasId("#hat_trick:example.com")
        )
    }

    @Test
    fun shouldDeleteRoomAlias() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/directory/room/%23unicorns:server",
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
        matrixRestClient.rooms.deleteRoomAlias(RoomAliasId("unicorns", "server")).getOrThrow()
    }

    @Test
    fun shouldGetJoinedRooms() = runTest {
        val response = GetJoinedRooms.Response(
            setOf(
                RoomId("room1", "server"), RoomId("room2", "server")
            )
        )
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
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
            })
        val result = matrixRestClient.rooms.getJoinedRooms().getOrThrow().toSet()
        assertTrue { result.containsAll(setOf(RoomId("room1", "server"), RoomId("room2", "server"))) }
    }

    @Test
    fun shouldInviteUser() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/invite",
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
        matrixRestClient.rooms.inviteUser(RoomId("room", "server"), UserId("user", "server")).getOrThrow()
    }

    @Test
    fun shouldKickUser() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/kick",
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
        matrixRestClient.rooms.kickUser(RoomId("room", "server"), UserId("user", "server"))
    }

    @Test
    fun shouldBanUser() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/ban",
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
        matrixRestClient.rooms.banUser(RoomId("room", "server"), UserId("user", "server"))
    }

    @Test
    fun shouldUnbanUser() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/unban",
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
        matrixRestClient.rooms.unbanUser(RoomId("room", "server"), UserId("user", "server"))
    }

    @Test
    fun shouldJoinRoomByRoomId() = runTest {
        val response = JoinRoom.Response(RoomId("room", "server"))
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/join/!room:server?server_name=server1.com&server_name=server2.com",
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
            })
        val result = matrixRestClient.rooms.joinRoom(
            roomId = RoomId("room", "server"),
            serverNames = setOf("server1.com", "server2.com"),
            thirdPartySigned = Signed(
                JoinRoom.Request.ThirdParty(
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
        val response = JoinRoom.Response(RoomId("room", "server"))
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/join/%23alias:server?server_name=server1.com&server_name=server2.com",
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
            })
        val result = matrixRestClient.rooms.joinRoom(
            roomAliasId = RoomAliasId("alias", "server"),
            serverNames = setOf("server1.com", "server2.com"),
            thirdPartySigned = Signed(
                JoinRoom.Request.ThirdParty(
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
        val response = KnockRoom.Response(RoomId("room", "server"))
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/knock/!room:server?server_name=server1.com&server_name=server2.com",
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
        val response = KnockRoom.Response(RoomId("room", "server"))
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/knock/%23alias:server?server_name=server1.com&server_name=server2.com",
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/leave",
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
        matrixRestClient.rooms.leaveRoom(RoomId("room", "server")).getOrThrow()
    }

    @Test
    fun shouldForgetRoom() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/forget",
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
        matrixRestClient.rooms.forgetRoom(RoomId("room", "server"))
    }

    @Test
    fun shouldSetReceipt() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/receipt/m.read/${'$'}event",
                        request.url.fullPath
                    )
                    request.body.toByteArray().decodeToString() shouldBe "{}"
                    assertEquals(HttpMethod.Post, request.method)
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.setReceipt(RoomId("room", "server"), EventId("\$event")).getOrThrow()
    }

    @Test
    fun shouldSetReadMarkers() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/read_markers",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """
                    {
                      "m.fully_read":"$1event",
                      "m.read":"$2event"
                    }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.setReadMarkers(RoomId("room", "server"), EventId("$1event"), EventId("$2event"))
            .getOrThrow()
    }

    @Test
    fun shouldGetAccountData() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@alice:example.com/rooms/!room:server/account_data/m.fully_read",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{"event_id":"$1event"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@alice:example.com/rooms/!room:server/account_data/m.fully_readkey",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{"event_id":"$1event"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@alice:example.com/rooms/!room:server/account_data/m.fully_read",
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
            })
        matrixRestClient.rooms.setAccountData(
            FullyReadEventContent(EventId("$1event")),
            RoomId("room", "server"),
            UserId("alice", "example.com")
        ).getOrThrow()
    }

    @Test
    fun shouldSetAccountDataWithKey() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@alice:example.com/rooms/!room:server/account_data/m.fully_readkey",
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/typing/@alice:example.com",
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
            })
        matrixRestClient.rooms.setTyping(
            RoomId("room", "server"),
            UserId("alice", "example.com"),
            typing = true,
            timeout = 10_000,
        ).getOrThrow()
    }

    @Test
    fun shouldGetDirectoryVisibility() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/directory/list/room/!room:server",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "visibility": "public"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getDirectoryVisibility(RoomId("room", "server"))
            .getOrThrow() shouldBe DirectoryVisibility.PUBLIC
    }

    @Test
    fun shouldSetDirectoryVisibility() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/directory/list/room/!room:server",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("""{"visibility":"public"}""", request.body.toByteArray().decodeToString())
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.setDirectoryVisibility(
            roomId = RoomId("room", "server"),
            visibility = DirectoryVisibility.PUBLIC
        ).getOrThrow()
    }

    @Test
    fun shouldGetPublicRooms() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/publicRooms?limit=5&server=example&since=since",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "chunk": [
                                {
                                  "avatar_url": "mxc://bleecker.street/CHEDDARandBRIE",
                                  "guest_can_join": false,
                                  "join_rule": "public",
                                  "name": "CHEESE",
                                  "num_joined_members": 37,
                                  "room_id": "!ol19s:bleecker.street",
                                  "topic": "Tasty tasty cheese",
                                  "world_readable": true
                                }
                              ],
                              "next_batch": "p190q",
                              "prev_batch": "p1902",
                              "total_room_count_estimate": 115
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getPublicRooms(limit = 5, server = "example", since = "since")
            .getOrThrow() shouldBe GetPublicRoomsResponse(
            chunk = listOf(
                GetPublicRoomsResponse.PublicRoomsChunk(
                    avatarUrl = "mxc://bleecker.street/CHEDDARandBRIE",
                    guestCanJoin = false,
                    joinRule = JoinRulesEventContent.JoinRule.Public,
                    name = "CHEESE",
                    joinedMembersCount = 37,
                    roomId = RoomId("!ol19s:bleecker.street"),
                    topic = "Tasty tasty cheese",
                    worldReadable = true
                )
            ),
            nextBatch = "p190q",
            prevBatch = "p1902",
            totalRoomCountEstimate = 115
        )
    }

    @Test
    fun shouldGetPublicRoomsWithFilter() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/publicRooms?server=example",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                        {
                          "filter": {
                            "generic_search_term": "foo"
                          },
                          "include_all_networks": false,
                          "limit": 10,
                          "third_party_instance_id": "irc"
                        }
                    """.trimToFlatJson()
                    respond(
                        """
                            {
                              "chunk": [
                                {
                                  "avatar_url": "mxc://bleecker.street/CHEDDARandBRIE",
                                  "guest_can_join": false,
                                  "join_rule": "public",
                                  "name": "CHEESE",
                                  "num_joined_members": 37,
                                  "room_id": "!ol19s:bleecker.street",
                                  "topic": "Tasty tasty cheese",
                                  "world_readable": true
                                }
                              ],
                              "next_batch": "p190q",
                              "prev_batch": "p1902",
                              "total_room_count_estimate": 115
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getPublicRooms(
            limit = 10,
            server = "example",
            since = null,
            filter = GetPublicRoomsWithFilter.Request.Filter("foo"),
            includeAllNetworks = false,
            thirdPartyInstanceId = "irc"
        ).getOrThrow() shouldBe GetPublicRoomsResponse(
            chunk = listOf(
                GetPublicRoomsResponse.PublicRoomsChunk(
                    avatarUrl = "mxc://bleecker.street/CHEDDARandBRIE",
                    guestCanJoin = false,
                    joinRule = JoinRulesEventContent.JoinRule.Public,
                    name = "CHEESE",
                    joinedMembersCount = 37,
                    roomId = RoomId("!ol19s:bleecker.street"),
                    topic = "Tasty tasty cheese",
                    worldReadable = true
                )
            ),
            nextBatch = "p190q",
            prevBatch = "p1902",
            totalRoomCountEstimate = 115
        )
    }

    @Test
    fun shouldGetTags() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@user:server/rooms/!room:server/tags",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "tags": {
                                "m.favourite": {
                                  "order": 0.1
                                },
                                "u.Customers": {},
                                "u.Work": {
                                  "order": 0.7
                                }
                              }
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getTags(UserId("user", "server"), RoomId("room", "server"))
            .getOrThrow() shouldBe TagEventContent(
            mapOf(
                TagEventContent.TagName.Favourite to TagEventContent.Tag(0.1),
                TagEventContent.TagName.Unknown("u.Customers") to TagEventContent.Tag(),
                TagEventContent.TagName.Unknown("u.Work") to TagEventContent.Tag(0.7)
            )
        )
    }

    @Test
    fun shouldSetTag() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@user:server/rooms/!room:server/tags/m.dino",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """{"order":0.25}"""
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.setTag(
            UserId("user", "server"), RoomId("room", "server"), "m.dino",
            TagEventContent.Tag(0.25)
        ).getOrThrow()
    }

    @Test
    fun shouldDeleteTag() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@user:server/rooms/!room:server/tags/m.dino",
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
        matrixRestClient.rooms.deleteTag(UserId("user", "server"), RoomId("room", "server"), "m.dino").getOrThrow()
    }

    @Test
    fun shouldGetEventContext() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/context/event?filter=filter&limit=10",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "end": "t29-57_2_0_2",
                              "event": {
                                "content": {
                                  "body": "filename.jpg",
                                  "info": {
                                    "h": 398,
                                    "mimetype": "image/jpeg",
                                    "size": 31037,
                                    "w": 394
                                  },
                                  "msgtype": "m.image",
                                  "url": "mxc://example.org/JWEIFJgwEIhweiWJE"
                                },
                                "event_id": "${'$'}f3h4d129462ha:example.com",
                                "origin_server_ts": 1432735824653,
                                "room_id": "!636q39766251:example.com",
                                "sender": "@example:example.org",
                                "type": "m.room.message",
                                "unsigned": {
                                  "age": 1234
                                }
                              },
                              "events_after": [
                                {
                                  "content": {
                                    "body": "This is an example text message",
                                    "format": "org.matrix.custom.html",
                                    "formatted_body": "<b>This is an example text message</b>",
                                    "msgtype": "m.text"
                                  },
                                  "event_id": "${'$'}143273582443PhrSn:example.org",
                                  "origin_server_ts": 1432735824653,
                                  "room_id": "!636q39766251:example.com",
                                  "sender": "@example:example.org",
                                  "type": "m.room.message",
                                  "unsigned": {
                                    "age": 1234
                                  }
                                }
                              ],
                              "events_before": [
                                {
                                  "content": {
                                    "body": "something-important.doc",
                                    "filename": "something-important.doc",
                                    "info": {
                                      "mimetype": "application/msword",
                                      "size": 46144
                                    },
                                    "msgtype": "m.file",
                                    "url": "mxc://example.org/FHyPlCeYUSFFxlgbQYZmoEoe"
                                  },
                                  "event_id": "${'$'}143273582443PhrSn:example.org",
                                  "origin_server_ts": 1432735824653,
                                  "room_id": "!636q39766251:example.com",
                                  "sender": "@example:example.org",
                                  "type": "m.room.message",
                                  "unsigned": {
                                    "age": 1234
                                  }
                                }
                              ],
                              "start": "t27-54_2_0_2",
                              "state": [
                                {
                                  "content": {
                                    "creator": "@example:example.org",
                                    "m.federate": true,
                                    "predecessor": {
                                      "event_id": "${'$'}something:example.org",
                                      "room_id": "!oldroom:example.org"
                                    },
                                    "room_version": "1"
                                  },
                                  "event_id": "${'$'}143273582443PhrSn:example.org",
                                  "origin_server_ts": 1432735824653,
                                  "room_id": "!636q39766251:example.com",
                                  "sender": "@example:example.org",
                                  "state_key": "",
                                  "type": "m.room.create",
                                  "unsigned": {
                                    "age": 1234
                                  }
                                },
                                {
                                  "content": {
                                    "avatar_url": "mxc://example.org/SEsfnsuifSDFSSEF",
                                    "displayname": "Alice Margatroid",
                                    "membership": "join",
                                    "reason": "Looking for support"
                                  },
                                  "event_id": "${'$'}143273582443PhrSn:example.org",
                                  "origin_server_ts": 1432735824653,
                                  "room_id": "!636q39766251:example.com",
                                  "sender": "@example:example.org",
                                  "state_key": "@alice:example.org",
                                  "type": "m.room.member",
                                  "unsigned": {
                                    "age": 1234
                                  }
                                }
                              ]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getEventContext(
            roomId = RoomId("room", "server"),
            eventId = EventId("event"),
            filter = "filter",
            limit = 10
        ).getOrThrow() shouldBe GetEventContext.Response(
            start = "t27-54_2_0_2",
            end = "t29-57_2_0_2",
            event = MessageEvent(
                content = RoomMessageEventContent.ImageMessageEventContent(
                    body = "filename.jpg",
                    info = ImageInfo(
                        height = 398,
                        width = 394,
                        mimeType = "image/jpeg",
                        size = 31037,
                        thumbnailUrl = null,
                        thumbnailFile = null,
                        thumbnailInfo = null
                    ),
                    url = "mxc://example.org/JWEIFJgwEIhweiWJE", file = null, relatesTo = null
                ),
                id = EventId("\$f3h4d129462ha:example.com"),
                sender = UserId("@example:example.org"),
                roomId = RoomId("!636q39766251:example.com"),
                originTimestamp = 1432735824653,
                unsigned = UnsignedRoomEventData.UnsignedMessageEventData(
                    age = 1234,
                    redactedBecause = null,
                    transactionId = null
                )
            ),
            eventsBefore = listOf(
                MessageEvent(
                    content = RoomMessageEventContent.FileMessageEventContent(
                        body = "something-important.doc",
                        fileName = "something-important.doc",
                        info = FileInfo(
                            mimeType = "application/msword",
                            size = 46144,
                            thumbnailUrl = null,
                            thumbnailFile = null,
                            thumbnailInfo = null
                        ),
                        url = "mxc://example.org/FHyPlCeYUSFFxlgbQYZmoEoe", file = null, relatesTo = null
                    ),
                    id = EventId("$143273582443PhrSn:example.org"),
                    sender = UserId("@example:example.org"),
                    roomId = RoomId("!636q39766251:example.com"),
                    originTimestamp = 1432735824653,
                    unsigned = UnsignedRoomEventData.UnsignedMessageEventData(
                        age = 1234,
                        redactedBecause = null,
                        transactionId = null
                    )
                )
            ),
            eventsAfter = listOf(
                MessageEvent(
                    content = TextMessageEventContent(
                        body = "This is an example text message",
                        format = "org.matrix.custom.html",
                        formattedBody = "<b>This is an example text message</b>",
                        relatesTo = null
                    ),
                    id = EventId("$143273582443PhrSn:example.org"),
                    sender = UserId("@example:example.org"),
                    roomId = RoomId("!636q39766251:example.com"),
                    originTimestamp = 1432735824653,
                    unsigned = UnsignedRoomEventData.UnsignedMessageEventData(
                        age = 1234,
                        redactedBecause = null,
                        transactionId = null
                    )
                )
            ),
            state = listOf(
                StateEvent(
                    content = CreateEventContent(
                        creator = UserId("@example:example.org"), federate = true, roomVersion = "1",
                        predecessor = CreateEventContent.PreviousRoom(
                            roomId = RoomId("!oldroom:example.org"), eventId = EventId("\$something:example.org")
                        ),
                        type = CreateEventContent.RoomType.Room
                    ),
                    id = EventId("$143273582443PhrSn:example.org"),
                    sender = UserId("@example:example.org"),
                    roomId = RoomId("!636q39766251:example.com"),
                    originTimestamp = 1432735824653,
                    unsigned = UnsignedStateEventData(
                        age = 1234,
                        redactedBecause = null,
                        transactionId = null,
                        previousContent = null
                    ),
                    stateKey = ""
                ),
                StateEvent(
                    content = MemberEventContent(
                        avatarUrl = "mxc://example.org/SEsfnsuifSDFSSEF",
                        displayName = "Alice Margatroid",
                        membership = Membership.JOIN,
                        isDirect = null,
                        joinAuthorisedViaUsersServer = null,
                        thirdPartyInvite = null,
                        reason = "Looking for support"
                    ),
                    id = EventId("$143273582443PhrSn:example.org"),
                    sender = UserId("@example:example.org"),
                    roomId = RoomId("!636q39766251:example.com"),
                    originTimestamp = 1432735824653,
                    unsigned = UnsignedStateEventData(
                        age = 1234,
                        redactedBecause = null,
                        transactionId = null,
                        previousContent = null
                    ),
                    stateKey = "@alice:example.org"
                )
            )
        )
    }

    @Test
    fun shouldReportEvent() = runTest {
        val response = SendEventResponse(EventId("event"))
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/report/${'$'}eventToRedact",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """{"reason":"someReason","score":-100}""",
                        request.body.toByteArray().decodeToString()
                    )
                    respond(
                        json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.reportEvent(
            roomId = RoomId("room", "server"),
            eventId = EventId("\$eventToRedact"),
            reason = "someReason",
            score = -100
        ).getOrThrow()
    }

    @Test
    fun shouldUpgradeRoom() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/upgrade",
                        request.url.fullPath
                    )
                    request.body.toByteArray().decodeToString() shouldBe """{"new_version":"2"}"""
                    assertEquals(HttpMethod.Post, request.method)
                    respond(
                        """{"replacement_room":"!nextRoom:server"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.upgradeRoom(RoomId("room", "server"), "2")
            .getOrThrow() shouldBe RoomId("nextRoom", "server")
    }

    @Test
    fun shouldGetHierarchy() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/rooms/!room:server/hierarchy?from=from&limit=10&max_depth=4&suggested_only=true",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                           {
                             "next_batch": "next_batch_token",
                             "rooms": [
                               {
                                 "avatar_url": "mxc://example.org/abcdef",
                                 "canonical_alias": "#general:example.org",
                                 "children_state": [
                                   {
                                     "content": {
                                       "via": [
                                         "example.org"
                                       ]
                                     },
                                     "origin_server_ts": 1629413349153,
                                     "sender": "@alice:example.org",
                                     "state_key": "!a:example.org",
                                     "type": "m.space.child"
                                   }
                                 ],
                                 "guest_can_join": false,
                                 "join_rule": "public",
                                 "name": "The First Space",
                                 "num_joined_members": 42,
                                 "room_id": "!space:example.org",
                                 "room_type": "m.space",
                                 "topic": "No other spaces were created first, ever",
                                 "world_readable": true
                               }
                             ]
                           }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.getHierarchy(
            roomId = RoomId("room", "server"),
            from = "from",
            limit = 10,
            maxDepth = 4,
            suggestedOnly = true,
        ).getOrThrow() shouldBe GetHierarchy.Response(
            nextBatch = "next_batch_token",
            rooms = listOf(
                GetHierarchy.Response.PublicRoomsChunk(
                    avatarUrl = "mxc://example.org/abcdef",
                    canonicalAlias = RoomAliasId("#general:example.org"),
                    childrenState = setOf(
                        StrippedStateEvent(
                            ChildEventContent(via = setOf("example.org")),
                            originTimestamp = 1629413349153,
                            sender = UserId("@alice:example.org"),
                            stateKey = "!a:example.org",
                        )
                    ),
                    guestCanJoin = false,
                    joinRule = JoinRulesEventContent.JoinRule.Public,
                    name = "The First Space",
                    joinedMembersCount = 42,
                    roomId = RoomId("!space:example.org"),
                    roomType = CreateEventContent.RoomType.Space,
                    topic = "No other spaces were created first, ever",
                    worldReadable = true
                )
            )
        )
    }

    @Test
    fun shouldTimestampToEvent() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v1/rooms/!room:server/timestamp_to_event?ts=24&dir=f",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                           {
                              "event_id": "$143273582443PhrSn:example.org",
                              "origin_server_ts": 1432735824653
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.rooms.timestampToEvent(
            roomId = RoomId("room", "server"),
            timestamp = 24,
            dir = TimestampToEvent.Direction.FORWARDS,
        ).getOrThrow() shouldBe TimestampToEvent.Response(
            eventId = EventId("$143273582443PhrSn:example.org"),
            originTimestamp = 1432735824653,
        )
    }
}
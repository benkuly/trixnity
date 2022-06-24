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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.TagEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.space.ChildEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class RoomsRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: RoomsApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                routing {
                    roomsApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @Test
    fun shouldGetEvent() = testApplication {
        initCut()
        everySuspending { handlerMock.getEvent(isAny()) }
            .returns(
                Event.StateEvent(
                    id = EventId("event"),
                    roomId = RoomId("room", "server"),
                    unsigned = UnsignedRoomEventData.UnsignedStateEventData(),
                    originTimestamp = 1234,
                    sender = UserId("sender", "server"),
                    content = NameEventContent("a"),
                    stateKey = ""
                )
            )
        val response = client.get("/_matrix/client/v3/rooms/%21room%3Aserver/event/%24event") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "content":{
                    "name":"a"
                  },
                  "event_id":"event",
                  "origin_server_ts":1234,
                  "room_id":"!room:server",
                  "sender":"@sender:server",
                  "state_key":"",
                  "type":"m.room.name",
                  "unsigned":{}
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getEvent(assert {
                it.endpoint.evenId shouldBe EventId("\$event")
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldGetStateEvent() = testApplication {
        initCut()
        everySuspending { handlerMock.getStateEvent(isAny()) }
            .returns(NameEventContent("name"))
        val response =
            client.get("/_matrix/client/v3/rooms/%21room%3Aserver/state/m.room.name/") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "name":"name"
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getStateEvent(assert {
                it.endpoint.type shouldBe "m.room.name"
                it.endpoint.stateKey shouldBe ""
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldGetState() = testApplication {
        initCut()
        everySuspending { handlerMock.getState(isAny()) }
            .returns(
                listOf(
                    Event.StateEvent(
                        id = EventId("event1"),
                        roomId = RoomId("room", "server"),
                        unsigned = UnsignedRoomEventData.UnsignedStateEventData(),
                        originTimestamp = 12341,
                        sender = UserId("sender", "server"),
                        content = NameEventContent("a"),
                        stateKey = ""
                    ),
                    Event.StateEvent(
                        id = EventId("event2"),
                        roomId = RoomId("room", "server"),
                        unsigned = UnsignedRoomEventData.UnsignedStateEventData(),
                        originTimestamp = 12342,
                        sender = UserId("sender", "server"),
                        stateKey = UserId("user", "server").full,
                        content = MemberEventContent(membership = Membership.INVITE)
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/rooms/%21room%3Aserver/state") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                [
                  {
                    "content":{
                      "name":"a"
                    },
                    "event_id":"event1",
                    "origin_server_ts":12341,
                    "room_id":"!room:server",
                    "sender":"@sender:server",
                    "state_key":"",
                    "type":"m.room.name",
                    "unsigned":{}
                  },
                  {
                    "content":{
                      "membership":"invite"
                    },
                    "event_id":"event2",
                    "origin_server_ts":12342,
                    "room_id":"!room:server",
                    "sender":"@sender:server",
                    "state_key":"@user:server",
                    "type":"m.room.member",
                    "unsigned":{}
                  }
                ]
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getState(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldGetMembers() = testApplication {
        initCut()
        everySuspending { handlerMock.getMembers(isAny()) }
            .returns(
                GetMembers.Response(
                    setOf(
                        Event.StateEvent(
                            id = EventId("event1"),
                            roomId = RoomId("room", "server"),
                            unsigned = UnsignedRoomEventData.UnsignedStateEventData(),
                            originTimestamp = 12341,
                            sender = UserId("sender", "server"),
                            stateKey = UserId("user1", "server").full,
                            content = MemberEventContent(membership = Membership.INVITE)
                        ),
                        Event.StateEvent(
                            id = EventId("event2"),
                            roomId = RoomId("room", "server"),
                            unsigned = UnsignedRoomEventData.UnsignedStateEventData(),
                            originTimestamp = 12342,
                            sender = UserId("sender", "server"),
                            stateKey = UserId("user2", "server").full,
                            content = MemberEventContent(membership = Membership.INVITE)
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/rooms/%21room%3Aserver/members?at=someAt&membership=join") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "chunk":[
                    {
                      "content":{
                        "membership":"invite"
                      },
                      "event_id":"event1",
                      "origin_server_ts":12341,
                      "room_id":"!room:server",
                      "sender":"@sender:server",
                      "state_key":"@user1:server",
                      "type":"m.room.member",
                      "unsigned":{}
                    },
                    {
                      "content":{
                        "membership":"invite"
                      },
                      "event_id":"event2",
                      "origin_server_ts":12342,
                      "room_id":"!room:server",
                      "sender":"@sender:server",
                      "state_key":"@user2:server",
                      "type":"m.room.member",
                      "unsigned":{}
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getMembers(assert {
                it.endpoint.at shouldBe "someAt"
                it.endpoint.membership shouldBe Membership.JOIN
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldGetJoinedMembers() = testApplication {
        initCut()
        everySuspending { handlerMock.getJoinedMembers(isAny()) }
            .returns(
                GetJoinedMembers.Response(
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
            )
        val response =
            client.get("/_matrix/client/v3/rooms/%21room%3Aserver/joined_members") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                    "joined":{
                        "@user1:server":{
                            "display_name":"Unicorn"
                        },
                        "@user2:server":{
                            "display_name":"Dino"
                        }
                    }
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getJoinedMembers(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldGetEvents() = testApplication {
        initCut()
        everySuspending { handlerMock.getEvents(isAny()) }
            .returns(
                GetEvents.Response(
                    start = "start",
                    end = "end",
                    chunk = listOf(
                        Event.MessageEvent(
                            RoomMessageEventContent.TextMessageEventContent("hi"),
                            EventId("event"),
                            UserId("user", "server"),
                            RoomId("room", "server"),
                            1234L
                        )
                    ),
                    state = listOf(
                        Event.StateEvent(
                            MemberEventContent(membership = Membership.JOIN),
                            EventId("event"),
                            UserId("user", "server"),
                            RoomId("room", "server"),
                            1234L,
                            stateKey = UserId("dino", "server").full
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/rooms/%21room%3Aserver/messages?from=from&dir=f&limit=10") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "start":"start",
                  "end":"end",
                  "chunk":[
                    {
                      "content":{
                        "body":"hi",
                        "msgtype":"m.text"
                      },
                      "event_id":"event",
                      "origin_server_ts":1234,
                      "room_id":"!room:server",
                      "sender":"@user:server",
                      "type":"m.room.message"
                    }
                  ],
                  "state":[
                    {
                      "content":{
                        "membership":"join"
                      },
                      "event_id":"event",
                      "origin_server_ts":1234,
                      "room_id":"!room:server",
                      "sender":"@user:server",
                      "state_key":"@dino:server",
                      "type":"m.room.member"
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getEvents(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.from shouldBe "from"
                it.endpoint.to shouldBe null
                it.endpoint.filter shouldBe null
                it.endpoint.dir shouldBe GetEvents.Direction.FORWARDS
                it.endpoint.limit shouldBe 10
            })
        }
    }

    @Test
    fun shouldGetRelations() = testApplication {
        initCut()
        everySuspending { handlerMock.getRelations(isAny()) }
            .returns(
                GetRelationsResponse(
                    start = "start",
                    end = "end",
                    chunk = listOf(
                        Event.MessageEvent(
                            RoomMessageEventContent.TextMessageEventContent("hi"),
                            EventId("$2event"),
                            UserId("user", "server"),
                            RoomId("room", "server"),
                            1234L
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v1/rooms/%21room%3Aserver/relations/%241event?from=from&limit=10") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "prev_batch": "start",
                  "next_batch": "end",
                  "chunk": [
                    {
                      "content": {
                        "body":"hi",
                        "msgtype":"m.text"
                      },
                      "event_id": "${'$'}2event",
                      "origin_server_ts": 1234,
                      "room_id": "!room:server",
                      "sender": "@user:server",
                      "type": "m.room.message"
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getRelations(assert {
                it.endpoint shouldBe GetRelations(
                    roomId = RoomId("room", "server"),
                    eventId = EventId("$1event"),
                    from = "from",
                    limit = 10
                )
            })
        }
    }

    @Test
    fun shouldGetRelationsByRelationType() = testApplication {
        initCut()
        everySuspending { handlerMock.getRelationsByRelationType(isAny()) }
            .returns(
                GetRelationsResponse(
                    start = "start",
                    end = "end",
                    chunk = listOf(
                        Event.MessageEvent(
                            RoomMessageEventContent.TextMessageEventContent("hi"),
                            EventId("$2event"),
                            UserId("user", "server"),
                            RoomId("room", "server"),
                            1234L
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v1/rooms/%21room%3Aserver/relations/%241event/m.reference?from=from&limit=10") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "prev_batch": "start",
                  "next_batch": "end",
                  "chunk": [
                    {
                      "content": {
                        "body":"hi",
                        "msgtype":"m.text"
                      },
                      "event_id": "${'$'}2event",
                      "origin_server_ts": 1234,
                      "room_id": "!room:server",
                      "sender": "@user:server",
                      "type": "m.room.message"
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getRelationsByRelationType(assert {
                it.endpoint shouldBe GetRelationsByRelationType(
                    roomId = RoomId("room", "server"),
                    eventId = EventId("$1event"),
                    relationType = RelationType.Reference,
                    from = "from",
                    limit = 10
                )
            })
        }
    }

    @Test
    fun shouldGetRelationsByRelationTypeAndEventType() = testApplication {
        initCut()
        everySuspending { handlerMock.getRelationsByRelationTypeAndEventType(isAny()) }
            .returns(
                GetRelationsResponse(
                    start = "start",
                    end = "end",
                    chunk = listOf(
                        Event.MessageEvent(
                            RoomMessageEventContent.TextMessageEventContent("hi"),
                            EventId("$2event"),
                            UserId("user", "server"),
                            RoomId("room", "server"),
                            1234L
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v1/rooms/%21room%3Aserver/relations/%241event/m.reference/m.room.message?from=from&limit=10") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "prev_batch": "start",
                  "next_batch": "end",
                  "chunk": [
                    {
                      "content": {
                        "body":"hi",
                        "msgtype":"m.text"
                      },
                      "event_id": "${'$'}2event",
                      "origin_server_ts": 1234,
                      "room_id": "!room:server",
                      "sender": "@user:server",
                      "type": "m.room.message"
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getRelationsByRelationTypeAndEventType(assert {
                it.endpoint shouldBe GetRelationsByRelationTypeAndEventType(
                    roomId = RoomId("room", "server"),
                    eventId = EventId("$1event"),
                    relationType = RelationType.Reference,
                    eventType = "m.room.message",
                    from = "from",
                    limit = 10
                )
            })
        }
    }

    @Test
    fun shouldSendStateEvent() = testApplication {
        initCut()
        everySuspending { handlerMock.sendStateEvent(isAny()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/%21room%3Aserver/state/m.room.name/") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"name"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "event_id":"event"
               }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.sendStateEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.stateKey shouldBe ""
                it.endpoint.type shouldBe "m.room.name"
                it.requestBody shouldBe NameEventContent("name")
            })
        }
    }

    @Test
    fun shouldSendStateEventEventIfUnknown() = testApplication {
        initCut()
        everySuspending { handlerMock.sendStateEvent(isAny()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/%21room%3Aserver/state/m.unknown/") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"dino":"unicorn"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "event_id":"event"
               }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.sendStateEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.stateKey shouldBe ""
                it.endpoint.type shouldBe "m.unknown"
                it.requestBody shouldBe UnknownStateEventContent(
                    JsonObject(mapOf("dino" to JsonPrimitive("unicorn"))),
                    "m.unknown"
                )
            })
        }
    }

    @Test
    fun shouldSendMessageEvent() = testApplication {
        initCut()
        everySuspending { handlerMock.sendMessageEvent(isAny()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/%21room%3Aserver/send/m.room.message/someTxnId") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"body":"someBody","msgtype":"m.text"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "event_id":"event"
               }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.sendMessageEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.txnId shouldBe "someTxnId"
                it.endpoint.type shouldBe "m.room.message"
                it.requestBody shouldBe RoomMessageEventContent.TextMessageEventContent("someBody")
            })
        }
    }

    @Test
    fun shouldSendMessageEventEventIfUnknown() = testApplication {
        initCut()
        everySuspending { handlerMock.sendMessageEvent(isAny()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/%21room%3Aserver/send/m.unknown/someTxnId") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"dino":"unicorn"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "event_id":"event"
               }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.sendMessageEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.txnId shouldBe "someTxnId"
                it.endpoint.type shouldBe "m.unknown"
                it.requestBody shouldBe UnknownMessageEventContent(
                    JsonObject(mapOf("dino" to JsonPrimitive("unicorn"))),
                    "m.unknown"
                )
            })
        }
    }

    @Test
    fun shouldSendRedactEvent() = testApplication {
        initCut()
        everySuspending { handlerMock.redactEvent(isAny()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/%21room%3Aserver/redact/%24eventToRedact/someTxnId") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"someReason"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "event_id":"event"
               }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.redactEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.txnId shouldBe "someTxnId"
                it.endpoint.eventId shouldBe EventId("\$eventToRedact")
                it.requestBody shouldBe RedactEvent.Request("someReason")
            })
        }
    }

    @Test
    fun shouldCreateRoom() = testApplication {
        initCut()
        everySuspending { handlerMock.createRoom(isAny()) }
            .returns(CreateRoom.Response(RoomId("room", "server")))
        val response = client.post("/_matrix/client/v3/createRoom") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
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
                    """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "room_id":"!room:server"
               }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.createRoom(assert {
                it.requestBody shouldBe CreateRoom.Request(
                    visibility = DirectoryVisibility.PRIVATE,
                    roomAliasLocalPart = null,
                    name = "someRoomName",
                    topic = null,
                    invite = setOf(UserId("user1", "server")),
                    inviteThirdPid = setOf(
                        CreateRoom.Request.InviteThirdPid(
                            "identityServer",
                            "token",
                            "email",
                            "user2@example.org"
                        )
                    ),
                    roomVersion = null,
                    creationContent = null,
                    initialState = null,
                    preset = null,
                    isDirect = true,
                    powerLevelContentOverride = null
                )
            })
        }
    }

    @Test
    fun shouldSetRoomAlias() = testApplication {
        initCut()
        everySuspending { handlerMock.setRoomAlias(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/directory/room/%23unicorns%3Aserver") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"room_id":"!room:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setRoomAlias(assert {
                it.endpoint.roomAliasId shouldBe RoomAliasId("unicorns", "server")
                it.requestBody shouldBe SetRoomAlias.Request(RoomId("!room:server"))
            })
        }
    }

    @Test
    fun shouldGetRoomAlias() = testApplication {
        initCut()
        everySuspending { handlerMock.getRoomAlias(isAny()) }
            .returns(
                GetRoomAlias.Response(
                    roomId = RoomId("room", "server"),
                    servers = listOf("server1", "server2")
                )
            )
        val response =
            client.get("/_matrix/client/v3/directory/room/%23unicorns%3Aserver") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                    "room_id":"!room:server",
                    "servers":["server1","server2"]
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getRoomAlias(assert {
                it.endpoint.roomAliasId shouldBe RoomAliasId("unicorns", "server")
            })
        }
    }

    @Test
    fun shouldGetRoomAliases() = testApplication {
        initCut()
        everySuspending { handlerMock.getRoomAliases(isAny()) }
            .returns(
                GetRoomAliases.Response(
                    setOf(
                        RoomAliasId("#somewhere:example.com"),
                        RoomAliasId("#another:example.com"),
                        RoomAliasId("#hat_trick:example.com")
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/rooms/%21room%3Aserver/aliases") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "aliases": [
                    "#somewhere:example.com",
                    "#another:example.com",
                    "#hat_trick:example.com"
                  ]
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getRoomAliases(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldDeleteRoomAlias() = testApplication {
        initCut()
        everySuspending { handlerMock.deleteRoomAlias(isAny()) }
            .returns(Unit)
        val response =
            client.delete("/_matrix/client/v3/directory/room/%23unicorns%3Aserver") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.deleteRoomAlias(assert {
                it.endpoint.roomAliasId shouldBe RoomAliasId("unicorns", "server")
            })
        }
    }

    @Test
    fun shouldGetJoinedRooms() = testApplication {
        initCut()
        everySuspending { handlerMock.getJoinedRooms(isAny()) }
            .returns(
                GetJoinedRooms.Response(
                    setOf(
                        RoomId("room1", "server"), RoomId("room2", "server")
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/joined_rooms") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {"joined_rooms":["!room1:server","!room2:server"]}
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getJoinedRooms(isAny())
        }
    }

    @Test
    fun shouldInviteUser() = testApplication {
        initCut()
        everySuspending { handlerMock.inviteUser(isAny()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/invite") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"@user:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.inviteUser(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe InviteUser.Request(UserId("@user:server"), null)
            })
        }
    }

    @Test
    fun shouldKickUser() = testApplication {
        initCut()
        everySuspending { handlerMock.kickUser(isAny()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/kick") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"@user:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.kickUser(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe KickUser.Request(UserId("@user:server"), null)
            })
        }
    }

    @Test
    fun shouldBanUser() = testApplication {
        initCut()
        everySuspending { handlerMock.banUser(isAny()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/ban") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"@user:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.banUser(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe BanUser.Request(UserId("@user:server"), null)
            })
        }
    }

    @Test
    fun shouldUnbanUser() = testApplication {
        initCut()
        everySuspending { handlerMock.unbanUser(isAny()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/unban") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"@user:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.unbanUser(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe UnbanUser.Request(UserId("@user:server"), null)
            })
        }
    }

    @Test
    fun shouldJoinRoom() = testApplication {
        initCut()
        everySuspending { handlerMock.joinRoom(isAny()) }
            .returns(JoinRoom.Response(RoomId("room", "server")))
        val response =
            client.post("/_matrix/client/v3/join/%21room%3Aserver?server_name=server1.com&server_name=server2.com") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody(
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
                    """.trimIndent()
                )
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"room_id":"!room:server"}"""
        }
        verifyWithSuspend {
            handlerMock.joinRoom(assert {
                it.endpoint.roomIdOrRoomAliasId shouldBe "!room:server"
                it.endpoint.serverNames shouldBe setOf("server1.com", "server2.com")
                it.requestBody shouldBe JoinRoom.Request(
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
                    ),
                    reason = null
                )
            })
        }
    }

    @Test
    fun shouldKnockRoom() = testApplication {
        initCut()
        everySuspending { handlerMock.knockRoom(isAny()) }
            .returns(KnockRoom.Response(RoomId("room", "server")))
        val response =
            client.post("/_matrix/client/v3/knock/%21room%3Aserver?server_name=server1.com&server_name=server2.com") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"reason"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"room_id":"!room:server"}"""
        }
        verifyWithSuspend {
            handlerMock.knockRoom(assert {
                it.endpoint.roomIdOrRoomAliasId shouldBe "!room:server"
                it.endpoint.serverNames shouldBe setOf("server1.com", "server2.com")
                it.requestBody shouldBe KnockRoom.Request("reason")
            })
        }
    }

    @Test
    fun shouldLeaveRoom() = testApplication {
        initCut()
        everySuspending { handlerMock.leaveRoom(isAny()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/leave") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"reason"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.leaveRoom(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe LeaveRoom.Request("reason")
            })
        }
    }

    @Test
    fun shouldForgetRoom() = testApplication {
        initCut()
        everySuspending { handlerMock.forgetRoom(isAny()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/forget") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.forgetRoom(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
            })
        }
    }

    @Test
    fun shouldSetReceipt() = testApplication {
        initCut()
        everySuspending { handlerMock.setReceipt(isAny()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/receipt/m.read/%24event") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setReceipt(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.receiptType shouldBe SetReceipt.ReceiptType.READ
                it.endpoint.eventId shouldBe EventId("\$event")
            })
        }
    }

    @Test
    fun shouldSetReadMarkers() = testApplication {
        initCut()
        everySuspending { handlerMock.setReadMarkers(isAny()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/read_markers") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "m.fully_read":"$1event",
                      "m.read":"$2event"
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
            handlerMock.setReadMarkers(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe SetReadMarkers.Request(
                    fullyRead = EventId("$1event"),
                    read = EventId("$2event")
                )
            })
        }
    }

    @Test
    fun shouldGetAccountData() = testApplication {
        initCut()
        everySuspending { handlerMock.getAccountData(isAny()) }
            .returns(FullyReadEventContent(EventId("$1event")))
        val response =
            client.get("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_read") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"event_id":"$1event"}"""
        }
        verifyWithSuspend {
            handlerMock.getAccountData(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.type shouldBe "m.fully_read"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
            })
        }
    }

    @Test
    fun shouldGetAccountDataWithKey() = testApplication {
        initCut()
        everySuspending { handlerMock.getAccountData(isAny()) }
            .returns(FullyReadEventContent(EventId("$1event")))
        val response =
            client.get("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_read-readkey") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"event_id":"$1event"}"""
        }
        verifyWithSuspend {
            handlerMock.getAccountData(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.type shouldBe "m.fully_read-readkey"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
            })
        }
    }

    @Test
    fun shouldSetAccountData() = testApplication {
        initCut()
        everySuspending { handlerMock.setAccountData(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_read") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"event_id":"$1event"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setAccountData(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.type shouldBe "m.fully_read"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe FullyReadEventContent(EventId("$1event"))
            })
        }
    }

    @Test
    fun shouldSetAccountDataWithKey() = testApplication {
        initCut()
        everySuspending { handlerMock.setAccountData(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_read-readkey") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"event_id":"$1event"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setAccountData(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.type shouldBe "m.fully_read-readkey"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe FullyReadEventContent(EventId("$1event"))
            })
        }
    }

    @Test
    fun shouldSetTyping() = testApplication {
        initCut()
        everySuspending { handlerMock.setTyping(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/rooms/%21room%3Aserver/typing/%40alice%3Aexample%2Ecom") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"typing":true,"timeout":10000}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setTyping(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe SetTyping.Request(true, 10000)
            })
        }
    }

    @Test
    fun shouldGetDirectoryVisibility() = testApplication {
        initCut()
        everySuspending { handlerMock.getDirectoryVisibility(isAny()) }
            .returns(GetDirectoryVisibility.Response(DirectoryVisibility.PUBLIC))
        val response =
            client.get("/_matrix/client/v3/directory/list/room/%21room%3Aserver")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "visibility": "public"
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getDirectoryVisibility(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldSetDirectoryVisibility() = testApplication {
        initCut()
        everySuspending { handlerMock.setDirectoryVisibility(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/directory/list/room/%21room%3Aserver") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"visibility":"public"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setDirectoryVisibility(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.requestBody shouldBe SetDirectoryVisibility.Request(DirectoryVisibility.PUBLIC)
            })
        }
    }

    @Test
    fun shouldGetPublicRooms() = testApplication {
        initCut()
        everySuspending { handlerMock.getPublicRooms(isAny()) }
            .returns(
                GetPublicRoomsResponse(
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
            )
        val response =
            client.get("/_matrix/client/v3/publicRooms?limit=5&server=example&since=since")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
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
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getPublicRooms(assert {
                it.endpoint.limit shouldBe 5
                it.endpoint.server shouldBe "example"
                it.endpoint.since shouldBe "since"
            })
        }
    }

    @Test
    fun shouldGetPublicRoomsWithFilter() = testApplication {
        initCut()
        everySuspending { handlerMock.getPublicRoomsWithFilter(isAny()) }
            .returns(
                GetPublicRoomsResponse(
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
            )
        val response =
            client.post("/_matrix/client/v3/publicRooms?server=example") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "filter": {
                        "generic_search_term": "foo"
                      },
                      "include_all_networks": false,
                      "limit": 10,
                      "third_party_instance_id": "irc"
                    }
                """.trimIndent()
                )
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
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
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getPublicRoomsWithFilter(assert {
                it.endpoint.server shouldBe "example"
                it.requestBody shouldBe GetPublicRoomsWithFilter.Request(
                    filter = GetPublicRoomsWithFilter.Request.Filter("foo"),
                    includeAllNetworks = false,
                    limit = 10,
                    thirdPartyInstanceId = "irc"
                )
            })
        }
    }

    @Test
    fun shouldGetTags() = testApplication {
        initCut()
        everySuspending { handlerMock.getTags(isAny()) }
            .returns(
                TagEventContent(
                    mapOf(
                        "m.favourite" to TagEventContent.Tag(0.1),
                        "u.Customers" to TagEventContent.Tag(),
                        "u.Work" to TagEventContent.Tag(0.7)
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/user/%40user%3Aserver/rooms/%21room%3Aserver/tags") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
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
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getTags(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.userId shouldBe UserId("user", "server")
            })
        }
    }

    @Test
    fun shouldSetTag() = testApplication {
        initCut()
        everySuspending { handlerMock.setTag(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/user/%40user%3Aserver/rooms/%21room%3Aserver/tags/m%2Edino") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"order":0.25}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setTag(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.userId shouldBe UserId("user", "server")
                it.endpoint.tag shouldBe "m.dino"
                it.requestBody shouldBe TagEventContent.Tag(0.25)
            })
        }
    }

    @Test
    fun shouldDeleteTag() = testApplication {
        initCut()
        everySuspending { handlerMock.deleteTag(isAny()) }
            .returns(Unit)
        val response =
            client.delete("/_matrix/client/v3/user/%40user%3Aserver/rooms/%21room%3Aserver/tags/m%2Edino") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.deleteTag(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.userId shouldBe UserId("user", "server")
                it.endpoint.tag shouldBe "m.dino"
            })
        }
    }

    @Test
    fun shouldGetEventContext() = testApplication {
        initCut()
        everySuspending { handlerMock.getEventContext(isAny()) }
            .returns(
                GetEventContext.Response(
                    start = "t27-54_2_0_2",
                    end = "t29-57_2_0_2",
                    event = Event.MessageEvent(
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
                        Event.MessageEvent(
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
                        Event.MessageEvent(
                            content = RoomMessageEventContent.TextMessageEventContent(
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
                        Event.StateEvent(
                            content = CreateEventContent(
                                creator = UserId("@example:example.org"), federate = true, roomVersion = "1",
                                predecessor = CreateEventContent.PreviousRoom(
                                    roomId = RoomId("!oldroom:example.org"),
                                    eventId = EventId("\$something:example.org")
                                ),
                                type = CreateEventContent.RoomType.Room
                            ),
                            id = EventId("$143273582443PhrSn:example.org"),
                            sender = UserId("@example:example.org"),
                            roomId = RoomId("!636q39766251:example.com"),
                            originTimestamp = 1432735824653,
                            unsigned = UnsignedRoomEventData.UnsignedStateEventData(
                                age = 1234,
                                redactedBecause = null,
                                transactionId = null,
                                previousContent = null
                            ),
                            stateKey = ""
                        ),
                        Event.StateEvent(
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
                            unsigned = UnsignedRoomEventData.UnsignedStateEventData(
                                age = 1234,
                                redactedBecause = null,
                                transactionId = null,
                                previousContent = null
                            ),
                            stateKey = "@alice:example.org"
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/rooms/%21room%3Aserver/context/event?filter=filter&limit=10") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "start": "t27-54_2_0_2",
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
                  "state": [
                    {
                      "content": {
                        "creator": "@example:example.org",
                        "m.federate": true,
                        "predecessor": {
                          "event_id": "${'$'}something:example.org",
                          "room_id": "!oldroom:example.org"
                        },
                        "room_version": "1",
                        "type": null
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
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getEventContext(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.eventId shouldBe EventId("event")
                it.endpoint.filter shouldBe "filter"
                it.endpoint.limit shouldBe 10
            })
        }
    }

    @Test
    fun shouldReportEvent() = testApplication {
        initCut()
        everySuspending { handlerMock.reportEvent(isAny()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/report/%24eventToRedact") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"someReason","score":-100}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.reportEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.eventId shouldBe EventId("\$eventToRedact")
                it.requestBody shouldBe ReportEvent.Request("someReason", -100)
            })
        }
    }

    @Test
    fun shouldUpgradeRoom() = testApplication {
        initCut()
        everySuspending { handlerMock.upgradeRoom(isAny()) }
            .returns(UpgradeRoom.Response(RoomId("nextRoom", "server")))
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/upgrade") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"new_version":"2"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"replacement_room":"!nextRoom:server"}"""
        }
        verifyWithSuspend {
            handlerMock.upgradeRoom(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe UpgradeRoom.Request("2")
            })
        }
    }

    @Test
    fun shouldGetHierarchy() = testApplication {
        initCut()
        everySuspending { handlerMock.getHierarchy(isAny()) }
            .returns(
                GetHierarchy.Response(
                    nextBatch = "next_batch_token",
                    rooms = listOf(
                        GetHierarchy.Response.PublicRoomsChunk(
                            avatarUrl = "mxc://example.org/abcdef",
                            canonicalAlias = RoomAliasId("#general:example.org"),
                            childrenState = setOf(
                                Event.StrippedStateEvent(
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
            )
        val response =
            client.get("/_matrix/client/v3/rooms/%21room%3Aserver/hierarchy?from=from&limit=10&max_depth=4&suggested_only=true") {
                bearerAuth(
                    "token"
                )
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                 "next_batch": "next_batch_token",
                 "rooms": [
                   {
                     "avatar_url": "mxc://example.org/abcdef",
                     "canonical_alias": "#general:example.org",
                     "children_state": [
                       {
                         "content": {
                           "suggested":false,
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
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getHierarchy(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.from shouldBe "from"
                it.endpoint.limit shouldBe 10
                it.endpoint.maxDepth shouldBe 4
                it.endpoint.suggestedOnly shouldBe true
            })
        }
    }
}

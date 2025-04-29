package net.folivo.trixnity.clientserverapi.server

import dev.mokkery.*
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
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
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.TagEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.space.ChildEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RoomsRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = createDefaultEventContentSerializerMappings()

    val handlerMock = mock<RoomsApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = AccessTokenAuthenticationFunction {
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(
                            UserId("user", "server"),
                            "deviceId"
                        ), null
                    )
                }
            }
            matrixApiServer(json) {
                roomsApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetEvent() = testApplication {
        initCut()
        everySuspend { handlerMock.getEvent(any()) }
            .returns(
                StateEvent(
                    id = EventId("event"),
                    roomId = RoomId("room", "server"),
                    unsigned = UnsignedRoomEventData.UnsignedStateEventData(),
                    originTimestamp = 1234,
                    sender = UserId("sender", "server"),
                    content = NameEventContent("a"),
                    stateKey = ""
                )
            )
        val response = client.get("/_matrix/client/v3/rooms/!room:server/event/${'$'}event") { bearerAuth("token") }
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
        verifySuspend {
            handlerMock.getEvent(assert {
                it.endpoint.evenId shouldBe EventId("\$event")
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldGetStateEvent() = testApplication {
        initCut()
        everySuspend { handlerMock.getStateEvent(any()) }
            .returns(NameEventContent("name"))
        val response =
            client.get("/_matrix/client/v3/rooms/!room:server/state/m.room.name/") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "name":"name"
                }
            """.trimToFlatJson()
        }
        verifySuspend {
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
        everySuspend { handlerMock.getState(any()) }
            .returns(
                listOf(
                    StateEvent(
                        id = EventId("event1"),
                        roomId = RoomId("room", "server"),
                        unsigned = UnsignedRoomEventData.UnsignedStateEventData(),
                        originTimestamp = 12341,
                        sender = UserId("sender", "server"),
                        content = NameEventContent("a"),
                        stateKey = ""
                    ),
                    StateEvent(
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
            client.get("/_matrix/client/v3/rooms/!room:server/state") { bearerAuth("token") }
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
        verifySuspend {
            handlerMock.getState(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldGetMembers() = testApplication {
        initCut()
        everySuspend { handlerMock.getMembers(any()) }
            .returns(
                GetMembers.Response(
                    setOf(
                        StateEvent(
                            id = EventId("event1"),
                            roomId = RoomId("room", "server"),
                            unsigned = UnsignedRoomEventData.UnsignedStateEventData(),
                            originTimestamp = 12341,
                            sender = UserId("sender", "server"),
                            stateKey = UserId("user1", "server").full,
                            content = MemberEventContent(membership = Membership.INVITE)
                        ),
                        StateEvent(
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
            client.get("/_matrix/client/v3/rooms/!room:server/members?at=someAt&membership=join") { bearerAuth("token") }
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
        verifySuspend {
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
        everySuspend { handlerMock.getJoinedMembers(any()) }
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
            client.get("/_matrix/client/v3/rooms/!room:server/joined_members") { bearerAuth("token") }
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
        verifySuspend {
            handlerMock.getJoinedMembers(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldGetEvents() = testApplication {
        initCut()
        everySuspend { handlerMock.getEvents(any()) }
            .returns(
                GetEvents.Response(
                    start = "start",
                    end = "end",
                    chunk = listOf(
                        MessageEvent(
                            RoomMessageEventContent.TextBased.Text("hi"),
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
            )
        val response =
            client.get("/_matrix/client/v3/rooms/!room:server/messages?from=from&dir=f&limit=10") { bearerAuth("token") }
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
        verifySuspend {
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
        everySuspend { handlerMock.getRelations(any()) }
            .returns(
                GetRelationsResponse(
                    start = "start",
                    end = "end",
                    chunk = listOf(
                        MessageEvent(
                            RoomMessageEventContent.TextBased.Text("hi"),
                            EventId("$2event"),
                            UserId("user", "server"),
                            RoomId("room", "server"),
                            1234L
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v1/rooms/!room:server/relations/${'$'}1event?from=from&limit=10") {
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
        verifySuspend {
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
        everySuspend { handlerMock.getRelationsByRelationType(any()) }
            .returns(
                GetRelationsResponse(
                    start = "start",
                    end = "end",
                    chunk = listOf(
                        MessageEvent(
                            RoomMessageEventContent.TextBased.Text("hi"),
                            EventId("$2event"),
                            UserId("user", "server"),
                            RoomId("room", "server"),
                            1234L
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v1/rooms/!room:server/relations/${'$'}1event/m.reference?from=from&limit=10") {
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
        verifySuspend {
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
        everySuspend { handlerMock.getRelationsByRelationTypeAndEventType(any()) }
            .returns(
                GetRelationsResponse(
                    start = "start",
                    end = "end",
                    chunk = listOf(
                        MessageEvent(
                            RoomMessageEventContent.TextBased.Text("hi"),
                            EventId("$2event"),
                            UserId("user", "server"),
                            RoomId("room", "server"),
                            1234L
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v1/rooms/!room:server/relations/${'$'}1event/m.reference/m.room.message?from=from&limit=10") {
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
        verifySuspend {
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
    fun shouldGetThreads() = testApplication {
        initCut()
        everySuspend { handlerMock.getThreads(any()) }
            .returns(
                GetThreads.Response(
                    end = "end",
                    chunk = listOf(
                        MessageEvent(
                            RoomMessageEventContent.TextBased.Text("hi"),
                            EventId("$2event"),
                            UserId("user", "server"),
                            RoomId("room", "server"),
                            1234L
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v1/rooms/!room:server/threads?from=from&include=all&limit=10") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "next_batch": "end",
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
                  ]                  
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getThreads(assert {
                it.endpoint shouldBe GetThreads(
                    roomId = RoomId("room", "server"),
                    from = "from",
                    include = GetThreads.Include.ALL,
                    limit = 10
                )
            })
        }
    }

    @Test
    fun shouldSendStateEvent() = testApplication {
        initCut()
        everySuspend { handlerMock.sendStateEvent(any()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/!room:server/state/m.room.name/") {
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
        verifySuspend {
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
        everySuspend { handlerMock.sendStateEvent(any()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/!room:server/state/m.unknown/") {
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
        verifySuspend {
            handlerMock.sendStateEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.stateKey shouldBe ""
                it.endpoint.type shouldBe "m.unknown"
                it.requestBody shouldBe UnknownEventContent(
                    JsonObject(mapOf("dino" to JsonPrimitive("unicorn"))),
                    "m.unknown"
                )
            })
        }
    }

    @Test
    fun shouldSendMessageEvent() = testApplication {
        initCut()
        everySuspend { handlerMock.sendMessageEvent(any()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/!room:server/send/m.room.message/someTxnId") {
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
        verifySuspend {
            handlerMock.sendMessageEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.txnId shouldBe "someTxnId"
                it.endpoint.type shouldBe "m.room.message"
                it.requestBody shouldBe RoomMessageEventContent.TextBased.Text("someBody")
            })
        }
    }

    @Test
    fun shouldSendMessageEventIfUnknown() = testApplication {
        initCut()
        everySuspend { handlerMock.sendMessageEvent(any()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/!room:server/send/m.unknown/someTxnId") {
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
        verifySuspend {
            handlerMock.sendMessageEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.txnId shouldBe "someTxnId"
                it.endpoint.type shouldBe "m.unknown"
                it.requestBody shouldBe UnknownEventContent(
                    JsonObject(mapOf("dino" to JsonPrimitive("unicorn"))),
                    "m.unknown"
                )
            })
        }
    }

    @Test
    fun shouldSendRedactEvent() = testApplication {
        initCut()
        everySuspend { handlerMock.redactEvent(any()) }
            .returns(SendEventResponse(EventId("event")))
        val response =
            client.put("/_matrix/client/v3/rooms/!room:server/redact/${'$'}eventToRedact/someTxnId") {
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
        verifySuspend {
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
        everySuspend { handlerMock.createRoom(any()) }
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
        verifySuspend {
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
        everySuspend { handlerMock.setRoomAlias(any()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/directory/room/%23unicorns:server") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"room_id":"!room:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.setRoomAlias(assert {
                it.endpoint.roomAliasId shouldBe RoomAliasId("unicorns", "server")
                it.requestBody shouldBe SetRoomAlias.Request(RoomId("!room:server"))
            })
        }
    }

    @Test
    fun shouldGetRoomAlias() = testApplication {
        initCut()
        everySuspend { handlerMock.getRoomAlias(any()) }
            .returns(
                GetRoomAlias.Response(
                    roomId = RoomId("room", "server"),
                    servers = listOf("server1", "server2")
                )
            )
        val response =
            client.get("/_matrix/client/v3/directory/room/%23unicorns:server") { bearerAuth("token") }
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
        verifySuspend {
            handlerMock.getRoomAlias(assert {
                it.endpoint.roomAliasId shouldBe RoomAliasId("unicorns", "server")
            })
        }
    }

    @Test
    fun shouldGetRoomAliases() = testApplication {
        initCut()
        everySuspend { handlerMock.getRoomAliases(any()) }
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
            client.get("/_matrix/client/v3/rooms/!room:server/aliases") { bearerAuth("token") }
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
        verifySuspend {
            handlerMock.getRoomAliases(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldDeleteRoomAlias() = testApplication {
        initCut()
        everySuspend { handlerMock.deleteRoomAlias(any()) }
            .returns(Unit)
        val response =
            client.delete("/_matrix/client/v3/directory/room/%23unicorns:server") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.deleteRoomAlias(assert {
                it.endpoint.roomAliasId shouldBe RoomAliasId("unicorns", "server")
            })
        }
    }

    @Test
    fun shouldGetJoinedRooms() = testApplication {
        initCut()
        everySuspend { handlerMock.getJoinedRooms(any()) }
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
        verifySuspend {
            handlerMock.getJoinedRooms(any())
        }
    }

    @Test
    fun shouldInviteUser() = testApplication {
        initCut()
        everySuspend { handlerMock.inviteUser(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/invite") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"@user:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.inviteUser(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe InviteUser.Request(UserId("@user:server"), null)
            })
        }
    }

    @Test
    fun shouldKickUser() = testApplication {
        initCut()
        everySuspend { handlerMock.kickUser(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/kick") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"@user:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.kickUser(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe KickUser.Request(UserId("@user:server"), null)
            })
        }
    }

    @Test
    fun shouldBanUser() = testApplication {
        initCut()
        everySuspend { handlerMock.banUser(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/ban") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"@user:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.banUser(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe BanUser.Request(UserId("@user:server"), null)
            })
        }
    }

    @Test
    fun shouldUnbanUser() = testApplication {
        initCut()
        everySuspend { handlerMock.unbanUser(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/unban") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"user_id":"@user:server"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.unbanUser(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe UnbanUser.Request(UserId("@user:server"), null)
            })
        }
    }

    @Test
    fun shouldJoinRoom() = testApplication {
        initCut()
        everySuspend { handlerMock.joinRoom(any()) }
            .returns(JoinRoom.Response(RoomId("room", "server")))
        val response =
            client.post("/_matrix/client/v3/join/!room:server?via=server1.com&via=server2.com&server_name=server1.com&server_name=server2.com") {
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
        verifySuspend {
            handlerMock.joinRoom(assert {
                it.endpoint.roomIdOrRoomAliasId shouldBe "!room:server"
                it.endpoint.via shouldBe setOf("server1.com", "server2.com")
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
        everySuspend { handlerMock.knockRoom(any()) }
            .returns(KnockRoom.Response(RoomId("room", "server")))
        val response =
            client.post("/_matrix/client/v3/knock/!room:server?via=server1.com&via=server2.com&server_name=server1.com&server_name=server2.com") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"reason"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"room_id":"!room:server"}"""
        }
        verifySuspend {
            handlerMock.knockRoom(assert {
                it.endpoint.roomIdOrRoomAliasId shouldBe "!room:server"
                it.endpoint.via shouldBe setOf("server1.com", "server2.com")
                it.endpoint.serverNames shouldBe setOf("server1.com", "server2.com")
                it.requestBody shouldBe KnockRoom.Request("reason")
            })
        }
    }

    @Test
    fun shouldLeaveRoom() = testApplication {
        initCut()
        everySuspend { handlerMock.leaveRoom(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/leave") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"reason"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.leaveRoom(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe LeaveRoom.Request("reason")
            })
        }
    }

    @Test
    fun shouldForgetRoom() = testApplication {
        initCut()
        everySuspend { handlerMock.forgetRoom(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/forget") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.forgetRoom(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
            })
        }
    }

    @Test
    fun shouldSetReceipt() = testApplication {
        initCut()
        everySuspend { handlerMock.setReceipt(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/receipt/m.read/${'$'}event") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.setReceipt(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.receiptType shouldBe ReceiptType.Read
                it.endpoint.eventId shouldBe EventId("\$event")
            })
        }
    }

    @Test
    fun shouldSetReadMarkers() = testApplication {
        initCut()
        everySuspend { handlerMock.setReadMarkers(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/read_markers") {
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
        verifySuspend {
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
        everySuspend { handlerMock.getAccountData(any()) }
            .returns(FullyReadEventContent(EventId("$1event")))
        val response =
            client.get("/_matrix/client/v3/user/@alice:example.com/rooms/!room:server/account_data/m.fully_read") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"event_id":"$1event"}"""
        }
        verifySuspend {
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
        everySuspend { handlerMock.getAccountData(any()) }
            .returns(FullyReadEventContent(EventId("$1event")))
        val response =
            client.get("/_matrix/client/v3/user/@alice:example.com/rooms/!room:server/account_data/m.fully_read-readkey") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"event_id":"$1event"}"""
        }
        verifySuspend {
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
        everySuspend { handlerMock.setAccountData(any()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/user/@alice:example.com/rooms/!room:server/account_data/m.fully_read") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"event_id":"$1event"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
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
        everySuspend { handlerMock.setAccountData(any()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/user/@alice:example.com/rooms/!room:server/account_data/m.fully_read-readkey") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"event_id":"$1event"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
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
        everySuspend { handlerMock.setTyping(any()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/rooms/!room:server/typing/@alice:example.com") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"typing":true,"timeout":10000}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
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
        everySuspend { handlerMock.getDirectoryVisibility(any()) }
            .returns(GetDirectoryVisibility.Response(DirectoryVisibility.PUBLIC))
        val response =
            client.get("/_matrix/client/v3/directory/list/room/!room:server")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "visibility": "public"
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getDirectoryVisibility(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
            })
        }
    }

    @Test
    fun shouldSetDirectoryVisibility() = testApplication {
        initCut()
        everySuspend { handlerMock.setDirectoryVisibility(any()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/directory/list/room/!room:server") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"visibility":"public"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.setDirectoryVisibility(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.requestBody shouldBe SetDirectoryVisibility.Request(DirectoryVisibility.PUBLIC)
            })
        }
    }

    @Test
    fun shouldGetPublicRooms() = testApplication {
        initCut()
        everySuspend { handlerMock.getPublicRooms(any()) }
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
        verifySuspend {
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
        everySuspend { handlerMock.getPublicRoomsWithFilter(any()) }
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
        verifySuspend {
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
        everySuspend { handlerMock.getTags(any()) }
            .returns(
                TagEventContent(
                    mapOf(
                        TagEventContent.TagName.Favourite to TagEventContent.Tag(0.1),
                        TagEventContent.TagName.Unknown("u.Customers") to TagEventContent.Tag(),
                        TagEventContent.TagName.Unknown("u.Work") to TagEventContent.Tag(0.7)
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/user/@user:server/rooms/!room:server/tags") { bearerAuth("token") }
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
        verifySuspend {
            handlerMock.getTags(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.userId shouldBe UserId("user", "server")
            })
        }
    }

    @Test
    fun shouldSetTag() = testApplication {
        initCut()
        everySuspend { handlerMock.setTag(any()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/user/@user:server/rooms/!room:server/tags/m.dino") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"order":0.25}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
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
        everySuspend { handlerMock.deleteTag(any()) }
            .returns(Unit)
        val response =
            client.delete("/_matrix/client/v3/user/@user:server/rooms/!room:server/tags/m.dino") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
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
        everySuspend { handlerMock.getEventContext(any()) }
            .returns(
                GetEventContext.Response(
                    start = "t27-54_2_0_2",
                    end = "t29-57_2_0_2",
                    event = MessageEvent(
                        content = RoomMessageEventContent.FileBased.Image(
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
                            content = RoomMessageEventContent.FileBased.File(
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
                            content = RoomMessageEventContent.TextBased.Text(
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
            client.get("/_matrix/client/v3/rooms/!room:server/context/event?filter=filter&limit=10") { bearerAuth("token") }
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
        verifySuspend {
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
        everySuspend { handlerMock.reportEvent(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/report/${'$'}eventToRedact") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"someReason","score":-100}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
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
        everySuspend { handlerMock.upgradeRoom(any()) }
            .returns(UpgradeRoom.Response(RoomId("nextRoom", "server")))
        val response =
            client.post("/_matrix/client/v3/rooms/!room:server/upgrade") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"new_version":"2"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"replacement_room":"!nextRoom:server"}"""
        }
        verifySuspend {
            handlerMock.upgradeRoom(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe UpgradeRoom.Request("2")
            })
        }
    }

    @Test
    fun shouldGetHierarchy() = testApplication {
        initCut()
        everySuspend { handlerMock.getHierarchy(any()) }
            .returns(
                GetHierarchy.Response(
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
            )
        val response =
            client.get("/_matrix/client/v1/rooms/!room:server/hierarchy?from=from&limit=10&max_depth=4&suggested_only=true") {
                bearerAuth("token")
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
        verifySuspend {
            handlerMock.getHierarchy(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.from shouldBe "from"
                it.endpoint.limit shouldBe 10
                it.endpoint.maxDepth shouldBe 4
                it.endpoint.suggestedOnly shouldBe true
            })
        }
    }

    @Test
    fun shouldTimestampToEvent() = testApplication {
        initCut()
        everySuspend { handlerMock.timestampToEvent(any()) }
            .returns(
                TimestampToEvent.Response(
                    eventId = EventId("$143273582443PhrSn:example.org"),
                    originTimestamp = 1432735824653,
                )
            )
        val response =
            client.get("/_matrix/client/v1/rooms/!room:server/timestamp_to_event?ts=24&dir=f") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "event_id": "$143273582443PhrSn:example.org",
                  "origin_server_ts": 1432735824653
               }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.timestampToEvent(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.timestamp shouldBe 24
                it.endpoint.dir shouldBe TimestampToEvent.Direction.FORWARDS
            })
        }
    }
}

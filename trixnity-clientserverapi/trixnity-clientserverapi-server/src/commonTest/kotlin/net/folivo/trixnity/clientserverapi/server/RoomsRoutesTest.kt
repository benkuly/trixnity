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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnknownMessageEventContent
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.TagEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.space.ChildEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomsRoutesTest {
    private val json = createMatrixJson()
    private val mapping = createEventContentSerializerMappings()

    @OptIn(ConfigurationApi::class)
    val handlerMock: RoomsApiHandler = configure(RoomsApiHandlerMock()) { stubsUnitByDefault = true }

    private fun ApplicationTestBuilder.initCut() {
        application {
            install(Authentication) {
                matrixAccessTokenAuth {
                    authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
                }
            }
            matrixApiServer(json) {
                routing {
                    roomsApiRoutes(handlerMock, json, mapping)
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
    fun shouldGetEvent() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getEvent)
            .whenInvokedWith(any())
            .then {
                Event.StateEvent(
                    id = EventId("event"),
                    roomId = RoomId("room", "server"),
                    unsigned = UnsignedRoomEventData.UnsignedStateEventData(),
                    originTimestamp = 1234,
                    sender = UserId("sender", "server"),
                    content = NameEventContent("a"),
                    stateKey = ""
                )
            }
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
                  "sender":"@sender:server",
                  "room_id":"!room:server",
                  "origin_server_ts":1234,
                  "unsigned":{},
                  "state_key":"",
                  "type":"m.room.name"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getEvent)
            .with(matching {
                it.endpoint.evenId shouldBe EventId("\$event")
                it.endpoint.roomId shouldBe RoomId("room", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetStateEvent() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getStateEvent)
            .whenInvokedWith(any())
            .then {
                NameEventContent("name")
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getStateEvent)
            .with(matching {
                it.endpoint.type shouldBe "m.room.name"
                it.endpoint.stateKey shouldBe ""
                it.endpoint.roomId shouldBe RoomId("room", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetState() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getState)
            .whenInvokedWith(any())
            .then {
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
            }
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
                    "sender":"@sender:server",
                    "room_id":"!room:server",
                    "origin_server_ts":12341,
                    "unsigned":{},
                    "state_key":"",
                    "type":"m.room.name"
                  },
                  {
                    "content":{
                      "membership":"invite"
                    },
                    "event_id":"event2",
                    "sender":"@sender:server",
                    "room_id":"!room:server",
                    "origin_server_ts":12342,
                    "unsigned":{},
                    "state_key":"@user:server",
                    "type":"m.room.member"
                  }
                ]
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getState)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetMembers() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getMembers)
            .whenInvokedWith(any())
            .then {
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
            }
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
                      "sender":"@sender:server",
                      "room_id":"!room:server",
                      "origin_server_ts":12341,
                      "unsigned":{},
                      "state_key":"@user1:server",
                      "type":"m.room.member"
                    },
                    {
                      "content":{
                        "membership":"invite"
                      },
                      "event_id":"event2",
                      "sender":"@sender:server",
                      "room_id":"!room:server",
                      "origin_server_ts":12342,
                      "unsigned":{},
                      "state_key":"@user2:server",
                      "type":"m.room.member"
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getMembers)
            .with(matching {
                it.endpoint.at shouldBe "someAt"
                it.endpoint.membership shouldBe Membership.JOIN
                it.endpoint.roomId shouldBe RoomId("room", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetJoinedMembers() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getJoinedMembers)
            .whenInvokedWith(any())
            .then {
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
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getJoinedMembers)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetEvents() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getEvents)
            .whenInvokedWith(any())
            .then {
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
            }
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
                      "sender":"@user:server",
                      "room_id":"!room:server",
                      "origin_server_ts":1234,
                      "type":"m.room.message"
                    }
                  ],
                  "state":[
                    {
                      "content":{
                        "membership":"join"
                      },
                      "event_id":"event",
                      "sender":"@user:server",
                      "room_id":"!room:server",
                      "origin_server_ts":1234,
                      "state_key":"@dino:server",
                      "type":"m.room.member"
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getEvents)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.from shouldBe "from"
                it.endpoint.to shouldBe null
                it.endpoint.filter shouldBe null
                it.endpoint.dir shouldBe GetEvents.Direction.FORWARDS
                it.endpoint.limit shouldBe 10
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSendStateEvent() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::sendStateEvent)
            .whenInvokedWith(any())
            .then {
                SendEventResponse(EventId("event"))
            }
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
        verify(handlerMock).suspendFunction(handlerMock::sendStateEvent)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.stateKey shouldBe ""
                it.endpoint.type shouldBe "m.room.name"
                it.requestBody shouldBe NameEventContent("name")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSendStateEventEventIfUnknown() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::sendStateEvent)
            .whenInvokedWith(any())
            .then {
                SendEventResponse(EventId("event"))
            }
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
        verify(handlerMock).suspendFunction(handlerMock::sendStateEvent)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.stateKey shouldBe ""
                it.endpoint.type shouldBe "m.unknown"
                it.requestBody shouldBe UnknownStateEventContent(
                    JsonObject(mapOf("dino" to JsonPrimitive("unicorn"))),
                    "m.unknown"
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSendMessageEvent() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::sendMessageEvent)
            .whenInvokedWith(any())
            .then {
                SendEventResponse(EventId("event"))
            }
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
        verify(handlerMock).suspendFunction(handlerMock::sendMessageEvent)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.txnId shouldBe "someTxnId"
                it.endpoint.type shouldBe "m.room.message"
                it.requestBody shouldBe RoomMessageEventContent.TextMessageEventContent("someBody")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSendMessageEventEventIfUnknown() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::sendMessageEvent)
            .whenInvokedWith(any())
            .then {
                SendEventResponse(EventId("event"))
            }
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
        verify(handlerMock).suspendFunction(handlerMock::sendMessageEvent)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.txnId shouldBe "someTxnId"
                it.endpoint.type shouldBe "m.unknown"
                it.requestBody shouldBe UnknownMessageEventContent(
                    JsonObject(mapOf("dino" to JsonPrimitive("unicorn"))),
                    "m.unknown"
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSendRedactEvent() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::redactEvent)
            .whenInvokedWith(any())
            .then {
                SendEventResponse(EventId("event"))
            }
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
        verify(handlerMock).suspendFunction(handlerMock::redactEvent)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.txnId shouldBe "someTxnId"
                it.endpoint.eventId shouldBe EventId("\$eventToRedact")
                it.requestBody shouldBe RedactEvent.Request("someReason")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldCreateRoom() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::createRoom)
            .whenInvokedWith(any())
            .then {
                CreateRoom.Response(RoomId("room", "server"))
            }
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
        verify(handlerMock).suspendFunction(handlerMock::createRoom)
            .with(matching {
                it.requestBody shouldBe CreateRoom.Request(
                    visibility = DirectoryVisibility.PRIVATE,
                    roomAliasLocalPart = null,
                    name = "someRoomName",
                    topic = null,
                    invite = setOf(UserId("user1", "server")),
                    invite3Pid = setOf(
                        CreateRoom.Request.Invite3Pid(
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
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetRoomAlias() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::setRoomAlias)
            .with(matching {
                it.endpoint.roomAliasId shouldBe RoomAliasId("unicorns", "server")
                it.requestBody shouldBe SetRoomAlias.Request(RoomId("!room:server"))
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetRoomAlias() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getRoomAlias)
            .whenInvokedWith(any())
            .then {
                GetRoomAlias.Response(
                    roomId = RoomId("room", "server"),
                    servers = listOf("server1", "server2")
                )
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getRoomAlias)
            .with(matching {
                it.endpoint.roomAliasId shouldBe RoomAliasId("unicorns", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetRoomAliases() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getRoomAliases)
            .whenInvokedWith(any())
            .then {
                GetRoomAliases.Response(
                    setOf(
                        RoomAliasId("#somewhere:example.com"),
                        RoomAliasId("#another:example.com"),
                        RoomAliasId("#hat_trick:example.com")
                    )
                )
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getRoomAliases)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDeleteRoomAlias() = testApplication {
        initCut()
        val response =
            client.delete("/_matrix/client/v3/directory/room/%23unicorns%3Aserver") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::deleteRoomAlias)
            .with(matching {
                it.endpoint.roomAliasId shouldBe RoomAliasId("unicorns", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetJoinedRooms() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getJoinedRooms)
            .whenInvokedWith(any())
            .then {
                GetJoinedRooms.Response(
                    setOf(
                        RoomId("room1", "server"), RoomId("room2", "server")
                    )
                )
            }
        val response =
            client.get("/_matrix/client/v3/joined_rooms") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {"joined_rooms":["!room1:server","!room2:server"]}
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getJoinedRooms)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldInviteUser() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::inviteUser)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe InviteUser.Request(UserId("@user:server"), null)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldKickUser() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::kickUser)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe KickUser.Request(UserId("@user:server"), null)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldBanUser() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::banUser)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe BanUser.Request(UserId("@user:server"), null)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldUnbanUser() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::unbanUser)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe UnbanUser.Request(UserId("@user:server"), null)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldJoinRoom() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::joinRoom)
            .whenInvokedWith(any())
            .then {
                JoinRoom.Response(RoomId("room", "server"))
            }
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
        verify(handlerMock).suspendFunction(handlerMock::joinRoom)
            .with(matching {
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
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldKnockRoom() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::knockRoom)
            .whenInvokedWith(any())
            .then {
                KnockRoom.Response(RoomId("room", "server"))
            }
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
        verify(handlerMock).suspendFunction(handlerMock::knockRoom)
            .with(matching {
                it.endpoint.roomIdOrRoomAliasId shouldBe "!room:server"
                it.endpoint.serverNames shouldBe setOf("server1.com", "server2.com")
                it.requestBody shouldBe KnockRoom.Request("reason")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldLeaveRoom() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::leaveRoom)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe LeaveRoom.Request("reason")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldForgetRoom() = testApplication {
        initCut()
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/forget") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::forgetRoom)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetReceipt() = testApplication {
        initCut()
        val response =
            client.post("/_matrix/client/v3/rooms/%21room%3Aserver/receipt/m.read/%24event") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::setReceipt)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.receiptType shouldBe SetReceipt.ReceiptType.READ
                it.endpoint.eventId shouldBe EventId("\$event")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetReadMarkers() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::setReadMarkers)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe SetReadMarkers.Request(
                    fullyRead = EventId("$1event"),
                    read = EventId("$2event")
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetAccountData() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getAccountData)
            .whenInvokedWith(any())
            .then {
                FullyReadEventContent(EventId("$1event"))
            }
        val response =
            client.get("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_read") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"event_id":"$1event"}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::getAccountData)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.type shouldBe "m.fully_read"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetAccountDataWithKey() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getAccountData)
            .whenInvokedWith(any())
            .then {
                FullyReadEventContent(EventId("$1event"))
            }
        val response =
            client.get("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/rooms/%21room%3Aserver/account_data/m.fully_read-readkey") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"event_id":"$1event"}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::getAccountData)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.type shouldBe "m.fully_read-readkey"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetAccountData() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::setAccountData)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.type shouldBe "m.fully_read"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe FullyReadEventContent(EventId("$1event"))
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetAccountDataWithKey() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::setAccountData)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.type shouldBe "m.fully_read-readkey"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe FullyReadEventContent(EventId("$1event"))
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetTyping() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::setTyping)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe SetTyping.Request(true, 10000)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetDirectoryVisibility() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getDirectoryVisibility)
            .whenInvokedWith(any())
            .then {
                GetDirectoryVisibility.Response(DirectoryVisibility.PUBLIC)
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getDirectoryVisibility)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetDirectoryVisibility() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::setDirectoryVisibility)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.requestBody shouldBe SetDirectoryVisibility.Request(DirectoryVisibility.PUBLIC)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetPublicRooms() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getPublicRooms)
            .whenInvokedWith(any())
            .then {
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
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getPublicRooms)
            .with(matching {
                it.endpoint.limit shouldBe 5
                it.endpoint.server shouldBe "example"
                it.endpoint.since shouldBe "since"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetPublicRoomsWithFilter() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getPublicRoomsWithFilter)
            .whenInvokedWith(any())
            .then {
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
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getPublicRoomsWithFilter)
            .with(matching {
                it.endpoint.server shouldBe "example"
                it.requestBody shouldBe GetPublicRoomsWithFilter.Request(
                    filter = GetPublicRoomsWithFilter.Request.Filter("foo"),
                    includeAllNetworks = false,
                    limit = 10,
                    thirdPartyInstanceId = "irc"
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetTags() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getTags)
            .whenInvokedWith(any())
            .then {
                TagEventContent(
                    mapOf(
                        "m.favourite" to TagEventContent.Tag(0.1),
                        "u.Customers" to TagEventContent.Tag(),
                        "u.Work" to TagEventContent.Tag(0.7)
                    )
                )
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getTags)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.userId shouldBe UserId("user", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetTag() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::setTag)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.userId shouldBe UserId("user", "server")
                it.endpoint.tag shouldBe "m.dino"
                it.requestBody shouldBe TagEventContent.Tag(0.25)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDeleteTag() = testApplication {
        initCut()
        val response =
            client.delete("/_matrix/client/v3/user/%40user%3Aserver/rooms/%21room%3Aserver/tags/m%2Edino") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::deleteTag)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.userId shouldBe UserId("user", "server")
                it.endpoint.tag shouldBe "m.dino"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetEventContext() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getEventContext)
            .whenInvokedWith(any())
            .then {
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
            }
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
                        "w": 394,
                        "mimetype": "image/jpeg",
                        "size": 31037
                      },
                      "url": "mxc://example.org/JWEIFJgwEIhweiWJE",
                      "msgtype": "m.image"
                    },
                    "event_id": "${'$'}f3h4d129462ha:example.com",
                    "sender": "@example:example.org",
                    "room_id": "!636q39766251:example.com",
                    "origin_server_ts": 1432735824653,
                    "unsigned": {
                      "age": 1234
                    },
                    "type": "m.room.message"
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
                        "url": "mxc://example.org/FHyPlCeYUSFFxlgbQYZmoEoe",
                        "msgtype": "m.file"
                      },
                      "event_id": "${'$'}143273582443PhrSn:example.org",
                      "sender": "@example:example.org",
                      "room_id": "!636q39766251:example.com",
                      "origin_server_ts": 1432735824653,
                      "unsigned": {
                        "age": 1234
                      },
                      "type": "m.room.message"
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
                      "sender": "@example:example.org",
                      "room_id": "!636q39766251:example.com",
                      "origin_server_ts": 1432735824653,
                      "unsigned": {
                        "age": 1234
                      },
                      "type": "m.room.message"
                    }
                  ],
                  "state": [
                    {
                      "content": {
                        "creator": "@example:example.org",
                        "m.federate": true,
                        "room_version": "1",
                        "predecessor": {
                          "room_id": "!oldroom:example.org",
                          "event_id": "${'$'}something:example.org"
                        },
                        "type": null
                      },
                      "event_id": "${'$'}143273582443PhrSn:example.org",
                      "sender": "@example:example.org",
                      "room_id": "!636q39766251:example.com",
                      "origin_server_ts": 1432735824653,
                      "unsigned": {
                        "age": 1234
                      },
                      "state_key": "",
                      "type": "m.room.create"
                    },
                    {
                      "content": {
                        "avatar_url": "mxc://example.org/SEsfnsuifSDFSSEF",
                        "displayname": "Alice Margatroid",
                        "membership": "join",
                        "reason": "Looking for support"
                      },
                      "event_id": "${'$'}143273582443PhrSn:example.org",
                      "sender": "@example:example.org",
                      "room_id": "!636q39766251:example.com",
                      "origin_server_ts": 1432735824653,
                      "unsigned": {
                        "age": 1234
                      },
                      "state_key": "@alice:example.org",
                      "type": "m.room.member"
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getEventContext)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.eventId shouldBe EventId("event")
                it.endpoint.filter shouldBe "filter"
                it.endpoint.limit shouldBe 10
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldReportEvent() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::reportEvent)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.eventId shouldBe EventId("\$eventToRedact")
                it.requestBody shouldBe ReportEvent.Request("someReason", -100)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldUpgradeRoom() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::upgradeRoom)
            .whenInvokedWith(any())
            .then {
                UpgradeRoom.Response(RoomId("nextRoom", "server"))
            }
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
        verify(handlerMock).suspendFunction(handlerMock::upgradeRoom)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe UpgradeRoom.Request("2")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetHierarchy() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getHierarchy)
            .whenInvokedWith(any())
            .then {
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
            }
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
                         "sender": "@alice:example.org",
                         "origin_server_ts": 1629413349153,
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
        verify(handlerMock).suspendFunction(handlerMock::getHierarchy)
            .with(matching {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.from shouldBe "from"
                it.endpoint.limit shouldBe 10
                it.endpoint.maxDepth shouldBe 4
                it.endpoint.suggestedOnly shouldBe true
                true
            })
            .wasInvoked()
    }
}

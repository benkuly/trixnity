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
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
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
                it.endpoint.dir shouldBe GetEvents.Direction.FORWARD
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
                    visibility = CreateRoom.Request.Visibility.PRIVATE,
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
}

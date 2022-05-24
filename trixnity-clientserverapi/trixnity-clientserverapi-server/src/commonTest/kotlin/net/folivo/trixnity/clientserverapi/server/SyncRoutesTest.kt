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
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.*
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.*
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.InvitedRoom.InviteState
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.Ephemeral
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.UnknownGlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.Presence.ONLINE
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class SyncRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: SyncApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                routing {
                    syncApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @Test
    fun shouldSync() = testApplication {
        initCut()
        everySuspending { handlerMock.sync(isAny()) }
            .returns(
                Sync.Response(
                    nextBatch = "s72595_4483_1934",
                    presence = Presence(
                        listOf(
                            EphemeralEvent(
                                content = PresenceEventContent(
                                    avatarUrl = "mxc://localhost:wefuiwegh8742w",
                                    lastActiveAgo = 2478593,
                                    presence = ONLINE,
                                    isCurrentlyActive = false,
                                    statusMessage = "Making cupcakes"
                                ),
                                sender = UserId("@example:localhost")
                            )
                        )
                    ),
                    accountData = GlobalAccountData(
                        listOf(
                            GlobalAccountDataEvent(
                                content = UnknownGlobalAccountDataEventContent(
                                    JsonObject(mapOf("custom_config_key" to JsonPrimitive("custom_config_value"))),
                                    eventType = "org.example.custom.config"
                                ),
                            ),
                            GlobalAccountDataEvent(
                                content = DirectEventContent(
                                    mapOf(
                                        UserId("@bob:example.com") to setOf(
                                            RoomId("!abcdefgh:example.com"),
                                            RoomId("!hgfedcba:example.com")
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    room = Rooms(
                        join = mapOf(
                            RoomId("!726s6s6q:example.com") to JoinedRoom(
                                summary = RoomSummary(
                                    heroes = listOf(UserId("@alice:example.com"), UserId("@bob:example.com")),
                                    joinedMemberCount = 2,
                                    invitedMemberCount = 0
                                ),
                                state = Rooms.State(
                                    listOf(
                                        StateEvent(
                                            content = MemberEventContent(
                                                membership = JOIN,
                                                avatarUrl = "mxc://example.org/SEsfnsuifSDFSSEF",
                                                displayName = "Alice Margatroid"
                                            ),
                                            id = EventId("$143273582443PhrSn:example.org"),
                                            sender = UserId("@example:example.org"),
                                            originTimestamp = 1432735824653,
                                            unsigned = UnsignedStateEventData(
                                                age = 1234
                                            ),
                                            stateKey = "@alice:example.org",
                                            roomId = RoomId("!726s6s6q:example.com")
                                        )
                                    )
                                ),
                                timeline = Rooms.Timeline(
                                    listOf(
                                        StateEvent(
                                            content = MemberEventContent(
                                                membership = JOIN,
                                                avatarUrl = "mxc://example.org/SEsfnsuifSDFSSEF",
                                                displayName = "Alice Margatroid"
                                            ),
                                            id = EventId("$143273582443PhrSn:example.org"),
                                            sender = UserId("@example:example.org"),
                                            originTimestamp = 1432735824653,
                                            unsigned = UnsignedStateEventData(
                                                age = 1234
                                            ),
                                            stateKey = "@alice:example.org",
                                            roomId = RoomId("!726s6s6q:example.com")
                                        ),
                                        MessageEvent(
                                            content = TextMessageEventContent(
                                                body = "This is an example text message",
                                                format = "org.matrix.custom.html",
                                                formattedBody = "<b>This is an example text message</b>"
                                            ),
                                            id = EventId("$143273582443PhrSn:example.org"),
                                            sender = UserId("@example:example.org"),
                                            originTimestamp = 1432735824653,
                                            unsigned = UnsignedMessageEventData(
                                                age = 1234
                                            ),
                                            roomId = RoomId("!726s6s6q:example.com")
                                        )
                                    ),
                                    limited = true,
                                    previousBatch = "t34-23535_0_0"
                                ),
                                ephemeral = Ephemeral(
                                    listOf(
                                        EphemeralEvent(
                                            content = TypingEventContent(
                                                listOf(UserId("@alice:matrix.org"), UserId("@bob:matrix.org"))
                                            ),
                                            roomId = RoomId("!726s6s6q:example.com")
                                        )
                                    )
                                ),
                                accountData = RoomAccountData(
                                    listOf(
                                        RoomAccountDataEvent(
                                            content = UnknownRoomAccountDataEventContent(
                                                JsonObject(mapOf("custom_config_key" to JsonPrimitive("custom_config_value"))),
                                                eventType = "org.example.custom.config"
                                            ),
                                            roomId = RoomId("!726s6s6q:example.com")
                                        ),
                                    )
                                )
                            )
                        ),
                        invite = mapOf(
                            RoomId("!696r7674:example.com") to InvitedRoom(
                                inviteState = InviteState(
                                    listOf(
                                        StrippedStateEvent(
                                            content = NameEventContent("My Room Name"),
                                            sender = UserId("@alice:example.com"),
                                            roomId = RoomId("!696r7674:example.com"),
                                            stateKey = ""
                                        ),
                                        StrippedStateEvent(
                                            content = MemberEventContent(membership = INVITE),
                                            sender = UserId("@alice:example.com"),
                                            roomId = RoomId("!696r7674:example.com"),
                                            stateKey = "@bob:example.com"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/sync?filter=someFilter&full_state=true&set_presence=online&since=someSince&timeout=1234") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "next_batch":"s72595_4483_1934",
                  "rooms":{
                    "join":{
                      "!726s6s6q:example.com":{
                        "summary":{
                          "m.heroes":[
                            "@alice:example.com",
                            "@bob:example.com"
                          ],
                          "m.joined_member_count":2,
                          "m.invited_member_count":0
                        },
                        "state":{
                          "events":[
                            {
                              "content":{
                                "avatar_url":"mxc://example.org/SEsfnsuifSDFSSEF",
                                "displayname":"Alice Margatroid",
                                "membership":"join"
                              },
                              "event_id":"${'$'}143273582443PhrSn:example.org",
                              "sender":"@example:example.org",
                              "room_id":"!726s6s6q:example.com",
                              "origin_server_ts":1432735824653,
                              "unsigned":{
                                "age":1234
                              },
                              "state_key":"@alice:example.org",
                              "type":"m.room.member"
                            }
                          ]
                        },
                        "timeline":{
                          "events":[
                            {
                              "content":{
                                "avatar_url":"mxc://example.org/SEsfnsuifSDFSSEF",
                                "displayname":"Alice Margatroid",
                                "membership":"join"
                              },
                              "event_id":"${'$'}143273582443PhrSn:example.org",
                              "sender":"@example:example.org",
                              "room_id":"!726s6s6q:example.com",
                              "origin_server_ts":1432735824653,
                              "unsigned":{
                                "age":1234
                              },
                              "state_key":"@alice:example.org",
                              "type":"m.room.member"
                            },
                            {
                              "content":{
                                "body":"This is an example text message",
                                "format":"org.matrix.custom.html",
                                "formatted_body":"<b>This is an example text message</b>",
                                "msgtype":"m.text"
                              },
                              "event_id":"${'$'}143273582443PhrSn:example.org",
                              "sender":"@example:example.org",
                              "room_id":"!726s6s6q:example.com",
                              "origin_server_ts":1432735824653,
                              "unsigned":{
                                "age":1234
                              },
                              "type":"m.room.message"
                            }
                          ],
                          "limited":true,
                          "prev_batch":"t34-23535_0_0"
                        },
                        "ephemeral":{
                          "events":[
                            {
                              "content":{
                                "user_ids":[
                                  "@alice:matrix.org",
                                  "@bob:matrix.org"
                                ]
                              },
                              "room_id":"!726s6s6q:example.com",
                              "type":"m.typing"
                            }
                          ]
                        },
                        "account_data":{
                          "events":[
                            {
                              "content":{
                                "custom_config_key":"custom_config_value"
                              },
                              "room_id":"!726s6s6q:example.com",
                              "type":"org.example.custom.config"
                            }
                          ]
                        }
                      }
                    },
                    "invite":{
                      "!696r7674:example.com":{
                        "invite_state":{
                          "events":[
                            {
                              "content":{
                                "name":"My Room Name"
                              },
                              "sender":"@alice:example.com",
                              "room_id":"!696r7674:example.com",
                              "state_key":"",
                              "type":"m.room.name"
                            },
                            {
                              "content":{
                                "membership":"invite"
                              },
                              "sender":"@alice:example.com",
                              "room_id":"!696r7674:example.com",
                              "state_key":"@bob:example.com",
                              "type":"m.room.member"
                            }
                          ]
                        }
                      }
                    }
                  },
                  "presence":{
                    "events":[
                      {
                        "content":{
                          "presence":"online",
                          "avatar_url":"mxc://localhost:wefuiwegh8742w",
                          "last_active_ago":2478593,
                          "currently_active":false,
                          "status_msg":"Making cupcakes"
                        },
                        "sender":"@example:localhost",
                        "type":"m.presence"
                      }
                    ]
                  },
                  "account_data":{
                    "events":[
                      {
                        "content":{
                          "custom_config_key":"custom_config_value"
                        },
                        "type":"org.example.custom.config"
                      },
                      {
                        "content":{
                          "@bob:example.com":[
                            "!abcdefgh:example.com",
                            "!hgfedcba:example.com"
                          ]
                        },
                        "type":"m.direct"
                      }
                    ]
                  }
                }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.sync(assert {
                it.endpoint.filter shouldBe "someFilter"
                it.endpoint.fullState shouldBe true
                it.endpoint.setPresence shouldBe ONLINE
                it.endpoint.since shouldBe "someSince"
                it.endpoint.timeout shouldBe 1234
            })
        }
    }
}

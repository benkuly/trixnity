package net.folivo.trixnity.client.rest.api.sync

import co.touchlab.stately.concurrency.AtomicInt
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import net.folivo.trixnity.client.rest.MatrixRestClient
import net.folivo.trixnity.client.rest.MatrixRestClientProperties
import net.folivo.trixnity.client.rest.MatrixRestClientProperties.HomeServerProperties
import net.folivo.trixnity.client.rest.api.sync.Presence.ONLINE
import net.folivo.trixnity.client.rest.api.sync.SyncResponse.*
import net.folivo.trixnity.client.rest.api.sync.SyncResponse.Presence
import net.folivo.trixnity.client.rest.runBlockingTest
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent
import net.folivo.trixnity.core.serialization.createJson
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncApiClientTest {
    private val json = createJson()

    @BeforeTest
    fun reset() {
        InMemorySyncBatchTokenService.reset()
    }

    @Test
    fun shouldSyncOnce() = runBlockingTest {
        val matrixRestClient = MatrixRestClient(
            properties = MatrixRestClientProperties(HomeServerProperties("matrix.host"), "token"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/r0/sync?filter=someFilter&full_state=true&set_presence=online&since=someSince&timeout=1234",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """
                        {
                          "next_batch": "s72595_4483_1934",
                          "presence": {
                            "events": [
                              {
                                "content": {
                                  "avatar_url": "mxc://localhost:wefuiwegh8742w",
                                  "last_active_ago": 2478593,
                                  "presence": "online",
                                  "currently_active": false,
                                  "status_msg": "Making cupcakes"
                                },
                                "type": "m.presence",
                                "sender": "@example:localhost"
                              }
                            ]
                          },
                          "account_data": {
                            "events": [
                              {
                                "type": "org.example.custom.config",
                                "content": {
                                  "custom_config_key": "custom_config_value"
                                }
                              }
                            ]
                          },
                          "rooms": {
                            "join": {
                              "!726s6s6q:example.com": {
                                "summary": {
                                  "m.heroes": [
                                    "@alice:example.com",
                                    "@bob:example.com"
                                  ],
                                  "m.joined_member_count": 2,
                                  "m.invited_member_count": 0
                                },
                                "state": {
                                  "events": [
                                    {
                                      "content": {
                                        "membership": "join",
                                        "avatar_url": "mxc://example.org/SEsfnsuifSDFSSEF",
                                        "displayname": "Alice Margatroid"
                                      },
                                      "type": "m.room.member",
                                      "event_id": "$143273582443PhrSn:example.org",
                                      "sender": "@example:example.org",
                                      "origin_server_ts": 1432735824653,
                                      "unsigned": {
                                        "age": 1234
                                      },
                                      "state_key": "@alice:example.org"
                                    }
                                  ]
                                },
                                "timeline": {
                                  "events": [
                                    {
                                      "content": {
                                        "membership": "join",
                                        "avatar_url": "mxc://example.org/SEsfnsuifSDFSSEF",
                                        "displayname": "Alice Margatroid"
                                      },
                                      "type": "m.room.member",
                                      "event_id": "$143273582443PhrSn:example.org",
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
                                      "sender": "@example:example.org",
                                      "origin_server_ts": 1432735824653,
                                      "unsigned": {
                                        "age": 1234
                                      }
                                    }
                                  ],
                                  "limited": true,
                                  "prev_batch": "t34-23535_0_0"
                                },
                                "ephemeral": {
                                  "events": [
                                    {
                                      "content": {
                                        "user_ids": [
                                          "@alice:matrix.org",
                                          "@bob:example.com"
                                        ]
                                      },
                                      "type": "m.typing"
                                    }
                                  ]
                                },
                                "account_data": {
                                  "events": [
                                    {
                                      "content": {
                                        "tags": {
                                          "u.work": {
                                            "order": 0.9
                                          }
                                        }
                                      },
                                      "type": "m.tag"
                                    },
                                    {
                                      "type": "org.example.custom.room.config",
                                      "content": {
                                        "custom_config_key": "custom_config_value"
                                      }
                                    }
                                  ]
                                }
                              }
                            },
                            "invite": {
                              "!696r7674:example.com": {
                                "invite_state": {
                                  "events": [
                                    {
                                      "sender": "@alice:example.com",
                                      "type": "m.room.name",
                                      "state_key": "",
                                      "content": {
                                        "name": "My Room Name"
                                      }
                                    },
                                    {
                                      "sender": "@alice:example.com",
                                      "type": "m.room.member",
                                      "state_key": "@bob:example.com",
                                      "content": {
                                        "membership": "invite"
                                      }
                                    }
                                  ]
                                }
                              }
                            },
                            "leave": {}
                          }
                        }
                    """.trimIndent(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.sync.syncOnce(
            filter = "someFilter",
            fullState = true,
            setPresence = ONLINE,
            since = "someSince",
            timeout = 1234
        )
        assertEquals("s72595_4483_1934", result.nextBatch)
        assertEquals(1, result.presence?.events?.size)
        assertEquals(1, result.accountData?.events?.size)
        assertEquals(1, result.room?.join?.size)
        assertEquals(1, result.room?.invite?.size)
        assertEquals(0, result.room?.leave?.size)
    }

    @Test
    fun shouldSyncLoop() = runBlockingTest {
        val response1 = SyncResponse(
            nextBatch = "nextBatch1",
            accountData = AccountData(emptyList()),
            deviceLists = DeviceLists(emptyList(), emptyList()),
            deviceOneTimeKeysCount = emptyMap(),
            presence = Presence(emptyList()),
            room = Rooms(emptyMap(), emptyMap(), emptyMap()),
            toDevice = ToDevice(emptyList())
        )
        val response2 = SyncResponse(
            nextBatch = "nextBatch2",
            accountData = AccountData(emptyList()),
            deviceLists = DeviceLists(emptyList(), emptyList()),
            deviceOneTimeKeysCount = emptyMap(),
            presence = Presence(emptyList()),
            room = Rooms(emptyMap(), emptyMap(), emptyMap()),
            toDevice = ToDevice(emptyList())
        )
        val requestCount = AtomicInt(1)
        val matrixRestClient = MatrixRestClient(

            properties = MatrixRestClientProperties(HomeServerProperties("matrix.host"), "token"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        if (requestCount.get() == 1) {
                            assertEquals(
                                "/_matrix/client/r0/sync?filter=someFilter&full_state=false&set_presence=online&timeout=30000",
                                request.url.fullPath
                            )
                            assertEquals(HttpMethod.Get, request.method)
                            requestCount.incrementAndGet()
                            respond(
                                json.encodeToString(response1),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        } else {
                            assertEquals(
                                "/_matrix/client/r0/sync?filter=someFilter&full_state=false&set_presence=online&since=nextBatch1&timeout=30000",
                                request.url.fullPath
                            )
                            assertEquals(HttpMethod.Get, request.method)
                            requestCount.incrementAndGet()
                            respond(
                                json.encodeToString(response2),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                }
            })

        val result = matrixRestClient.sync.syncLoop(
            filter = "someFilter",
            setPresence = ONLINE
        ).take(2).toList()

        assertEquals(3, requestCount.get())
        assertEquals(response1, result[0])
        assertEquals(response2, result[1])
        assertEquals("nextBatch2", InMemorySyncBatchTokenService.getBatchToken())
    }

    @Test
    fun shouldRetrySyncLoopOnError() = runBlockingTest {
        val response1 = SyncResponse(
            nextBatch = "nextBatch1",
            accountData = AccountData(emptyList()),
            deviceLists = DeviceLists(emptyList(), emptyList()),
            deviceOneTimeKeysCount = emptyMap(),
            presence = Presence(emptyList()),
            room = Rooms(emptyMap(), emptyMap(), emptyMap()),
            toDevice = ToDevice(emptyList())
        )
        val response2 = SyncResponse(
            nextBatch = "nextBatch2",
            accountData = AccountData(emptyList()),
            deviceLists = DeviceLists(emptyList(), emptyList()),
            deviceOneTimeKeysCount = emptyMap(),
            presence = Presence(emptyList()),
            room = Rooms(emptyMap(), emptyMap(), emptyMap()),
            toDevice = ToDevice(emptyList())
        )
        val requestCount = AtomicInt(1)
        val matrixRestClient = MatrixRestClient(
            properties = MatrixRestClientProperties(HomeServerProperties("matrix.host"), "token"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        when (requestCount.get()) {
                            1 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&full_state=false&set_presence=online&timeout=30000",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.incrementAndGet()
                                respond(
                                    json.encodeToString(response1),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            2 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&full_state=false&set_presence=online&since=nextBatch1&timeout=30000",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.incrementAndGet()
                                respond(
                                    "",
                                    HttpStatusCode.NotFound,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            else -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&full_state=false&set_presence=online&since=nextBatch1&timeout=30000",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.incrementAndGet()
                                respond(
                                    json.encodeToString(response2),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }
                    }
                }
            })

        val result = matrixRestClient.sync.syncLoop(
            filter = "someFilter",
            setPresence = ONLINE
        ).take(2).toList()

        assertEquals(4, requestCount.get())
        assertEquals(response1, result[0])
        assertEquals(response2, result[1])
        assertEquals("nextBatch2", InMemorySyncBatchTokenService.getBatchToken())
    }

    @Test
    fun shouldEmitEvents() = runBlockingTest {
        val response = SyncResponse(
            nextBatch = "nextBatch1",
            accountData = AccountData(emptyList()),
            deviceLists = DeviceLists(emptyList(), emptyList()),
            deviceOneTimeKeysCount = emptyMap(),
            presence = Presence(emptyList()), //TODO
            room = Rooms(
                join = mapOf(
                    MatrixId.RoomId("room1", "Server") to Rooms.JoinedRoom(
                        timeline = Rooms.Timeline(
                            listOf(
                                Event.RoomEvent(
                                    MessageEventContent.TextMessageEventContent("hi"),
                                    MatrixId.EventId("event1", "server"),
                                    MatrixId.UserId("user", "server"),
                                    MatrixId.RoomId("room1", "server"),
                                    1234L
                                )
                            )
                        ),
                        state = Rooms.State(
                            listOf(
                                Event.StateEvent(
                                    MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                                    MatrixId.EventId("event2", "server"),
                                    MatrixId.UserId("user", "server"),
                                    MatrixId.RoomId("room1", "server"),
                                    1235L,
                                    stateKey = MatrixId.UserId("joinedUser", "server").toString()
                                )
                            )
                        ),
                        ephemeral = Rooms.JoinedRoom.Ephemeral(
                            listOf() // TODO
                        )
                    )
                ),
                leave = mapOf(
                    MatrixId.RoomId("room2", "Server") to Rooms.LeftRoom(
                        timeline = Rooms.Timeline(
                            listOf(
                                Event.RoomEvent(
                                    MessageEventContent.NoticeMessageEventContent("hi"),
                                    MatrixId.EventId("event4", "server"),
                                    MatrixId.UserId("user", "server"),
                                    MatrixId.RoomId("room2", "server"),
                                    1234L
                                )
                            )
                        ),
                        state = Rooms.State(
                            listOf(
                                Event.StateEvent(
                                    MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                                    MatrixId.EventId("event5", "server"),
                                    MatrixId.UserId("user", "server"),
                                    MatrixId.RoomId("room2", "server"),
                                    1235L,
                                    stateKey = MatrixId.UserId("joinedUser", "server").toString()
                                )
                            )
                        )
                    )
                ),
                invite = mapOf(
                    MatrixId.RoomId("room3", "Server") to Rooms.InvitedRoom(
                        Rooms.InvitedRoom.InviteState(
                            listOf(
                                Event.StrippedStateEvent(
                                    MemberEventContent(membership = MemberEventContent.Membership.INVITE),
                                    MatrixId.UserId("user", "server"),
                                    MatrixId.RoomId("room3", "server"),
                                    stateKey = MatrixId.UserId("joinedUser", "server").toString()
                                )
                            )
                        )
                    )
                )
            ),
            toDevice = ToDevice(emptyList()) // TODO
        )
        val inChannel = Channel<SyncResponse>()

        val matrixRestClient = MatrixRestClient(
            properties = MatrixRestClientProperties(HomeServerProperties("matrix.host"), "token"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler {
                        respond(
                            json.encodeToString(inChannel.receive()),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })

        val allEventsFlow = matrixRestClient.sync.allEvents()
        val allEvents = GlobalScope.async {
            allEventsFlow.take(5).toList()
        }
        val messageEventsFlow = matrixRestClient.sync.events<MessageEventContent>()
        val messageEvents = GlobalScope.async {
            messageEventsFlow.take(2).toList()
        }
        val memberEventsFlow = matrixRestClient.sync.events<MemberEventContent>()
        val memberEvents = GlobalScope.async {
            memberEventsFlow.take(3).toList()
        }

        GlobalScope.launch {
            matrixRestClient.sync.start()
        }

        inChannel.send(response)

        assertEquals(5, allEvents.await().count())
        assertEquals(
            listOf("event1", "event4"),
            allEvents.await().filterIsInstance<Event.RoomEvent<*>>().map { it.id.localpart })
        assertEquals(
            listOf("event2", "event5"),
            allEvents.await().filterIsInstance<Event.StateEvent<*>>().map { it.id.localpart })
        assertEquals(
            listOf("room3"),
            allEvents.await().filterIsInstance<Event.StrippedStateEvent<*>>().map { it.roomId.localpart })
        assertEquals(2, messageEvents.await().count())
        assertEquals(3, memberEvents.await().count())
        matrixRestClient.sync.stop()
    }

    @Test
    fun shouldDealWithMultipleStartsAndStops() = runBlockingTest {
        val response = SyncResponse(
            nextBatch = "nextBatch1",
            accountData = AccountData(emptyList()),
            deviceLists = DeviceLists(emptyList(), emptyList()),
            deviceOneTimeKeysCount = emptyMap(),
            presence = Presence(emptyList()),
            room = Rooms(
                mapOf(
                    MatrixId.RoomId("room", "Server") to Rooms.JoinedRoom(
                        timeline = Rooms.Timeline(
                            listOf(
                                Event.RoomEvent(
                                    MessageEventContent.TextMessageEventContent("hi"),
                                    MatrixId.EventId("event", "server"),
                                    MatrixId.UserId("user", "server"),
                                    MatrixId.RoomId("room", "server"),
                                    1234L
                                )
                            )
                        )
                    )
                ), emptyMap(), emptyMap()
            ),
            toDevice = ToDevice(emptyList())
        )
        val inChannel = Channel<SyncResponse>()

        val matrixRestClient = MatrixRestClient(
            properties = MatrixRestClientProperties(HomeServerProperties("matrix.host"), "token"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler {
                        respond(
                            json.encodeToString(inChannel.receive()),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })

        val events = GlobalScope.async {
            matrixRestClient.sync.events<EventContent>().take(2).toList()
        }

        GlobalScope.launch {
            matrixRestClient.sync.start()
            matrixRestClient.sync.start()
        }

        inChannel.send(response)
        inChannel.send(response)

        assertEquals(2, events.await().count())
        matrixRestClient.sync.stop()
        matrixRestClient.sync.stop()
    }
}
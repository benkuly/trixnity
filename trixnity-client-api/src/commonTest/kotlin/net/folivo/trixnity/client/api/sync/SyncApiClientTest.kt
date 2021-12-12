package net.folivo.trixnity.client.api.sync

import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.runBlockingTest
import net.folivo.trixnity.client.api.sync.SyncApiClient.SyncState.*
import net.folivo.trixnity.client.api.sync.SyncResponse.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.*

class SyncApiClientTest {
    private val json = createMatrixJson()

    data class RequestCounter(var value: Int)

    lateinit var scope: CoroutineScope

    private val serverResponse1 = SyncResponse(
        nextBatch = "nextBatch1",
        accountData = GlobalAccountData(emptyList()),
        deviceLists = DeviceLists(emptySet(), emptySet()),
        deviceOneTimeKeysCount = emptyMap(),
        presence = Presence(emptyList()),
        room = Rooms(emptyMap(), emptyMap(), emptyMap()),
        toDevice = ToDevice(emptyList())
    )
    private val serverResponse2 = SyncResponse(
        nextBatch = "nextBatch2",
        accountData = GlobalAccountData(emptyList()),
        deviceLists = DeviceLists(emptySet(), emptySet()),
        deviceOneTimeKeysCount = emptyMap(),
        presence = Presence(emptyList()),
        room = Rooms(emptyMap(), emptyMap(), emptyMap()),
        toDevice = ToDevice(emptyList())
    )
    private val serverResponse3 = SyncResponse(
        nextBatch = "nextBatch3",
        accountData = GlobalAccountData(listOf(GlobalAccountDataEvent(DirectEventContent(mappings = emptyMap())))),
        deviceLists = DeviceLists(emptySet(), emptySet()),
        deviceOneTimeKeysCount = emptyMap(),
        presence = Presence(emptyList()),
        room = Rooms(emptyMap(), emptyMap(), emptyMap()),
        toDevice = ToDevice(emptyList())
    )

    @BeforeTest
    fun beforeTest() {
        scope = CoroutineScope(Dispatchers.Default)
    }

    @AfterTest
    fun afterTest() {
        scope.cancel()
    }

    @Test
    fun shouldSyncOnce() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
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
                              },
                              {
                                "content": {
                                  "@bob:example.com": [
                                    "!abcdefgh:example.com",
                                    "!hgfedcba:example.com"
                                  ]
                                },
                                "type": "m.direct"
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
            setPresence = PresenceEventContent.Presence.ONLINE,
            since = "someSince",
            timeout = 1234
        )
        assertEquals("s72595_4483_1934", result.nextBatch)
        assertEquals(1, result.presence?.events?.size)
        assertEquals(2, result.accountData?.events?.size)
        assertEquals(1, result.room?.join?.size)
        assertEquals(1, result.room?.invite?.size)
        assertEquals(0, result.room?.leave?.size)
    }

    @Test
    fun shouldSyncOnceAndHandleLongTimeout() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 100
                }
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/r0/sync?filter=someFilter&full_state=true&set_presence=online&since=someSince&timeout=200",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Get, request.method)
                        delay(300)
                        respond(
                            """
                              {
                                "next_batch": "s72595_4483_1934"
                              }
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.sync.syncOnce(
            filter = "someFilter",
            fullState = true,
            setPresence = PresenceEventContent.Presence.ONLINE,
            since = "someSince",
            timeout = 200
        )
    }

    @Test
    fun shouldSyncLoop() = runBlockingTest {
        val requestCount = RequestCounter(1)
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        when (requestCount.value) {
                            1 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&set_presence=online",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                respond(
                                    json.encodeToString(serverResponse1),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            2 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=30000",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                respond(
                                    json.encodeToString(serverResponse2),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            else -> {
                                respond(
                                    json.encodeToString(serverResponse2),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }
                    }
                }
            })

        val currentBatchToken = MutableStateFlow<String?>(null)
        val result = matrixRestClient.sync.syncLoop(
            filter = "someFilter",
            setPresence = PresenceEventContent.Presence.ONLINE,
            currentBatchToken = currentBatchToken
        ).take(3).toList()

        assertEquals(3, requestCount.value)
        assertEquals(serverResponse1, result[0])
        assertEquals(serverResponse2, result[1])
        assertEquals("nextBatch2", currentBatchToken.value)
    }

    @Test
    fun shouldSyncLoopWithTimeoutStateAndInitialSyncState() = runBlockingTest {
        val requestCount = RequestCounter(1)
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        when (requestCount.value) {
                            1 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&set_presence=online",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                respond(
                                    json.encodeToString(serverResponse1),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            2 -> {
                                requestCount.value++
                                delay(6000)
                                respond(
                                    json.encodeToString(serverResponse2),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            else -> {
                                respond(
                                    json.encodeToString(serverResponse2),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }
                    }
                }
            })

        val stateResult = matrixRestClient.sync.currentSyncState.shareIn(scope, SharingStarted.Eagerly, 20)

        stateResult.first { it == STOPPED }

        val currentBatchToken = MutableStateFlow<String?>(null)
        matrixRestClient.sync.start(
            filter = "someFilter",
            setPresence = PresenceEventContent.Presence.ONLINE,
            currentBatchToken = currentBatchToken,
            scope = scope,
            timeout = 300
        )
        stateResult.first { it == TIMEOUT }
        assertEquals(listOf(STOPPED, INITIAL_SYNC, RUNNING, TIMEOUT), stateResult.replayCache)
        matrixRestClient.sync.stop(wait = true)
        delay(100) // wait for STOPPED
        assertEquals(listOf(STOPPED, INITIAL_SYNC, RUNNING, TIMEOUT, STOPPING, STOPPED), stateResult.replayCache)
    }

    @Test
    fun shouldSyncLoopWithoutInitialSyncState() = runBlockingTest {
        val requestCount = RequestCounter(1)
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        when (requestCount.value) {
                            1 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&set_presence=online&since=ananas&timeout=100",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                respond(
                                    json.encodeToString(serverResponse1),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            else -> {
                                respond(
                                    json.encodeToString(serverResponse2),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }
                    }
                }
            })

        val stateResult = matrixRestClient.sync.currentSyncState.shareIn(scope, SharingStarted.Eagerly, 10)
        stateResult.first { it == STOPPED }
        val currentBatchToken = MutableStateFlow<String?>("ananas")
        matrixRestClient.sync.start(
            filter = "someFilter",
            setPresence = PresenceEventContent.Presence.ONLINE,
            currentBatchToken = currentBatchToken,
            scope = scope,
            timeout = 100
        )
        stateResult.first { it == RUNNING }
        assertEquals(listOf(STOPPED, STARTED, RUNNING), stateResult.replayCache)
    }

    @Test
    fun shouldSyncLoopWithErrorState() = runBlockingTest {
        val requestCount = RequestCounter(1)
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        when (requestCount.value) {
                            1 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                respond(
                                    json.encodeToString(serverResponse1),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            else -> {
                                respond(
                                    json.encodeToString(serverResponse2),
                                    HttpStatusCode.BadRequest,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }
                    }
                }
            })

        val stateResult = matrixRestClient.sync.currentSyncState.shareIn(scope, SharingStarted.Eagerly, 10)

        val currentBatchToken = MutableStateFlow<String?>(null)
        matrixRestClient.sync.start(
            filter = null,
            setPresence = null,
            currentBatchToken = currentBatchToken,
            asUserId = null,
            scope = scope
        )
        stateResult.first { it == ERROR }
        assertContains(stateResult.replayCache, ERROR)
    }

    @Test
    fun shouldSyncLoopAndHandleTimeout() = runBlockingTest {
        val requestCount = RequestCounter(1)
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 100
                }
                engine {
                    addHandler { request ->
                        when (requestCount.value) {
                            1 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                delay(6000)
                                respond(
                                    json.encodeToString(serverResponse1),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            else -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync", // no timeout since first request fails
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                delay(100)
                                respond(
                                    json.encodeToString(serverResponse1),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }
                    }
                }
            })

        matrixRestClient.sync.syncLoop(timeout = 100).take(1).toList()
        requestCount.value shouldBe 3 // is 3 because flow will stop, when he tries to emit
    }

    @Test
    fun shouldRetrySyncLoopOnError() = runBlockingTest {
        val requestCount = RequestCounter(1)
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        when (requestCount.value) {
                            1 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&set_presence=online",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                respond(
                                    json.encodeToString(serverResponse1),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            2 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=30000",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                respond(
                                    "",
                                    HttpStatusCode.NotFound,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            3 -> {
                                assertEquals(
                                    "/_matrix/client/r0/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=30000",
                                    request.url.fullPath
                                )
                                assertEquals(HttpMethod.Get, request.method)
                                requestCount.value++
                                respond(
                                    json.encodeToString(serverResponse2),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                            else -> {
                                respond(
                                    json.encodeToString(serverResponse2),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }
                    }
                }
            })

        val currentBatchToken = MutableStateFlow<String?>(null)

        val result = matrixRestClient.sync.syncLoop(
            filter = "someFilter",
            setPresence = PresenceEventContent.Presence.ONLINE,
            currentBatchToken = currentBatchToken,
        ).take(3).toList()

        assertEquals(4, requestCount.value)
        assertEquals(serverResponse1, result[0])
        assertEquals(serverResponse2, result[1])
        assertEquals("nextBatch2", currentBatchToken.value)
    }

    @Test
    fun shouldEmitEvents() = runBlockingTest {
        val response = SyncResponse(
            nextBatch = "nextBatch1",
            accountData = GlobalAccountData(
                listOf(
                    GlobalAccountDataEvent(
                        DirectEventContent(
                            mapOf(
                                UserId("alice", "server") to setOf(RoomId("room1", "server"))
                            )
                        )
                    )
                )
            ),
            deviceLists = DeviceLists(emptySet(), emptySet()),
            deviceOneTimeKeysCount = emptyMap(),
            presence = Presence(
                listOf(
                    Event.EphemeralEvent(
                        PresenceEventContent(PresenceEventContent.Presence.ONLINE),
                        sender = UserId("dino", "server")
                    )
                )
            ),
            room = Rooms(
                join = mapOf(
                    RoomId("room1", "Server") to Rooms.JoinedRoom(
                        timeline = Rooms.Timeline(
                            listOf(
                                Event.MessageEvent(
                                    RoomMessageEventContent.TextMessageEventContent("hi"),
                                    EventId("event1"),
                                    UserId("user", "server"),
                                    RoomId("room1", "server"),
                                    1234L
                                )
                            )
                        ),
                        state = Rooms.State(
                            listOf(
                                Event.StateEvent(
                                    MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                                    EventId("event2"),
                                    UserId("user", "server"),
                                    RoomId("room1", "server"),
                                    1235L,
                                    stateKey = UserId("joinedUser", "server").toString()
                                )
                            )
                        ),
                        ephemeral = Rooms.JoinedRoom.Ephemeral(emptyList()), //TODO
                        accountData = Rooms.RoomAccountData(
                            listOf(
                                Event.RoomAccountDataEvent(
                                    FullyReadEventContent(EventId("event1")),
                                    RoomId("room1", "server")
                                ),
                                Event.RoomAccountDataEvent(
                                    UnknownRoomAccountDataEventContent(
                                        JsonObject(mapOf("cool" to JsonPrimitive("trixnity"))),
                                        "org.example.mynamespace"
                                    ),
                                    RoomId("room1", "server")
                                )
                            )
                        )
                    )
                ),
                leave = mapOf(
                    RoomId("room2", "Server") to Rooms.LeftRoom(
                        timeline = Rooms.Timeline(
                            listOf(
                                Event.MessageEvent(
                                    RoomMessageEventContent.NoticeMessageEventContent("hi"),
                                    EventId("event4"),
                                    UserId("user", "server"),
                                    RoomId("room2", "server"),
                                    1234L
                                )
                            )
                        ),
                        state = Rooms.State(
                            listOf(
                                Event.StateEvent(
                                    MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                                    EventId("event5"),
                                    UserId("user", "server"),
                                    RoomId("room2", "server"),
                                    1235L,
                                    stateKey = UserId("joinedUser", "server").toString()
                                )
                            )
                        )
                    )
                ),
                invite = mapOf(
                    RoomId("room3", "Server") to Rooms.InvitedRoom(
                        Rooms.InvitedRoom.InviteState(
                            listOf(
                                Event.StrippedStateEvent(
                                    MemberEventContent(membership = MemberEventContent.Membership.INVITE),
                                    UserId("user", "server"),
                                    RoomId("room3", "server"),
                                    stateKey = UserId("joinedUser", "server").toString()
                                )
                            )
                        )
                    )
                )
            ),
            toDevice = ToDevice(
                listOf(
                    ToDeviceEvent(
                        RoomKeyEventContent(RoomId("room", "server"), "se", "sk", Megolm),
                        UserId("dino", "server")
                    )
                )
            )
        )
        val inChannel = Channel<SyncResponse>()

        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
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

        coroutineScope {
            val allEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribeAllEvents { allEventsCount.update { it + 1 } }

            val messageEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribe<RoomMessageEventContent> { messageEventsCount.update { it + 1 } }

            val memberEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribe<MemberEventContent> { memberEventsCount.update { it + 1 } }

            val presenceEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribe<PresenceEventContent> { presenceEventsCount.update { it + 1 } }

            val roomKeyEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribe<RoomKeyEventContent> { roomKeyEventsCount.update { it + 1 } }

            val globalAccountDataEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribe<GlobalAccountDataEventContent> { globalAccountDataEventsCount.update { it + 1 } }

            val roomAccountDataEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribe<RoomAccountDataEventContent> { roomAccountDataEventsCount.update { it + 1 } }

            val currentSyncBatchToken = MutableStateFlow<String?>(null)
            matrixRestClient.sync.start(scope = scope, currentBatchToken = currentSyncBatchToken)

            inChannel.send(response)

            currentSyncBatchToken.first { it != null }

            assertEquals(10, allEventsCount.value)
            assertEquals(2, messageEventsCount.value)
            assertEquals(3, memberEventsCount.value)
            assertEquals(1, presenceEventsCount.value)
            assertEquals(1, roomKeyEventsCount.value)
            assertEquals(1, globalAccountDataEventsCount.value)
            assertEquals(2, roomAccountDataEventsCount.value)
        }
    }

    @Test
    fun shouldDealWithMultipleStartsAndStops() = runBlockingTest {
        val response = SyncResponse(
            nextBatch = "nextBatch1",
            accountData = GlobalAccountData(emptyList()),
            deviceLists = DeviceLists(emptySet(), emptySet()),
            deviceOneTimeKeysCount = emptyMap(),
            presence = Presence(emptyList()),
            room = Rooms(
                join = mapOf(
                    RoomId("room", "Server") to Rooms.JoinedRoom(
                        timeline = Rooms.Timeline(
                            listOf(
                                Event.MessageEvent(
                                    RoomMessageEventContent.TextMessageEventContent("hi"),
                                    EventId("event"),
                                    UserId("user", "server"),
                                    RoomId("room", "server"),
                                    1234L
                                )
                            )
                        )
                    )
                ), knock = null, invite = null, leave = null
            ),
            toDevice = ToDevice(emptyList())
        )
        val inChannel = Channel<SyncResponse>()

        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
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

        val allEventsCount = MutableStateFlow(0)
        matrixRestClient.sync.subscribeAllEvents { allEventsCount.update { it + 1 } }

        scope.launch {
            matrixRestClient.sync.start(scope = this)
            matrixRestClient.sync.start(scope = this)
        }

        inChannel.send(response)
        inChannel.send(response)

        allEventsCount.first { it == 2 }
        matrixRestClient.sync.stop()
        matrixRestClient.sync.stop()
    }
}
package net.folivo.trixnity.clientserverapi.client

import io.kotest.assertions.retry
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.clientserverapi.client.SyncState.*
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncApiClientTest {
    private val json = createMatrixEventJson()

    private val serverResponse1 = Response(
        nextBatch = "nextBatch1",
        accountData = Response.GlobalAccountData(emptyList()),
        deviceLists = Response.DeviceLists(emptySet(), emptySet()),
        oneTimeKeysCount = emptyMap(),
        presence = Response.Presence(emptyList()),
        room = Response.Rooms(emptyMap(), emptyMap(), emptyMap()),
        toDevice = Response.ToDevice(emptyList())
    )
    private val serverResponse2 = Response(
        nextBatch = "nextBatch2",
        accountData = Response.GlobalAccountData(emptyList()),
        deviceLists = Response.DeviceLists(emptySet(), emptySet()),
        oneTimeKeysCount = emptyMap(),
        presence = Response.Presence(emptyList()),
        room = Response.Rooms(emptyMap(), emptyMap(), emptyMap()),
        toDevice = Response.ToDevice(emptyList())
    )

    @Test
    fun shouldSyncOnce() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/sync?filter=someFilter&full_state=true&set_presence=online&since=someSince&timeout=1234",
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
                                        "age": 1234,
                                        "m.relations": {
                                          "m.thread": {
                                            "latest_event": {
                                              "event_id": "${'$'}1threadMessage",
                                              "origin_server_ts": 1632491098485,
                                              "sender": "@alice:example.org",
                                              "type": "m.room.message",
                                              "content": {
                                                "msgtype": "m.text",
                                                "body": "Woo! Threads!"
                                              }
                                            },
                                            "count": 7,
                                            "current_user_participated": true
                                          }
                                        },
                                        "redacted_because": {
                                          "content": {
                                            "reason": "spam"
                                          },
                                          "event_id": "${'$'}1redactedBecause",
                                          "origin_server_ts": 1632491098485,
                                          "redacts": "${'$'}143273582443PhrSn:example.org",
                                          "sender": "@moderator:example.org",
                                          "type": "m.room.redaction",
                                          "unsigned": {
                                            "age": 1257
                                          }
                                        }
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
        )
        val result = matrixRestClient.sync.sync(
            filter = "someFilter",
            fullState = true,
            setPresence = Presence.ONLINE,
            since = "someSince",
            timeout = 1234
        ).getOrThrow()
        assertEquals("s72595_4483_1934", result.nextBatch)
        assertEquals(1, result.presence?.events?.size)
        assertEquals(2, result.accountData?.events?.size)
        assertEquals(1, result.room?.join?.size)
        result.room?.join?.get(RoomId("!726s6s6q:example.com"))?.timeline?.events?.lastOrNull()
            .shouldNotBeNull()
            .shouldBeInstanceOf<Event.MessageEvent<*>>()
            .unsigned.also {
                it?.redactedBecause.shouldNotBeNull().shouldBeInstanceOf<Event.MessageEvent<*>>()
                it?.aggregations?.thread?.latestEvent.shouldNotBeNull().shouldBeInstanceOf<Event.MessageEvent<*>>()
            }
        assertEquals(1, result.room?.invite?.size)
        assertEquals(0, result.room?.leave?.size)
    }

    @Test
    fun shouldSyncOnceAndHandleLongTimeout() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/sync?filter=someFilter&full_state=true&set_presence=online&since=someSince&timeout=200",
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
            })
        matrixRestClient.sync.sync(
            filter = "someFilter",
            fullState = true,
            setPresence = Presence.ONLINE,
            since = "someSince",
            timeout = 200
        )
    }

    @Test
    fun shouldSyncLoop() = runTest {
        val requestCount = MutableStateFlow(0)
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.value) {
                        0 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=some&timeout=0",
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

                        1 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=30000",
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
                            delay(10_000)
                            respond(
                                json.encodeToString(serverResponse2),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                }
            }
        )

        val currentBatchToken = MutableStateFlow<String?>("some")
        val syncResponses = MutableSharedFlow<Response>(replay = 5)
        matrixRestClient.sync.subscribeSyncResponse { syncResponses.emit(it) }
        val job = launch {
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                currentBatchToken = currentBatchToken,
                scope = this,
                timeout = 30_000
            )
        }
        syncResponses.take(3).collect()
        job.cancelAndJoin()

        assertEquals(2, requestCount.value)
        assertEquals(serverResponse1, syncResponses.replayCache[0])
        assertEquals(serverResponse2, syncResponses.replayCache[1])
        assertEquals("nextBatch2", currentBatchToken.value)
    }

    @Test
    fun shouldIntialSyncLoop() = runTest {
        val requestCount = MutableStateFlow(0)
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.value) {
                        0 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&timeout=0",
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

                        1 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=30000",
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
                            delay(10_000)
                            respond(
                                json.encodeToString(serverResponse2),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                }
            }
        )

        val currentBatchToken = MutableStateFlow<String?>(null)
        val syncResponses = MutableSharedFlow<Response>(replay = 5)
        matrixRestClient.sync.subscribeSyncResponse { syncResponses.emit(it) }
        val job = launch {
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                currentBatchToken = currentBatchToken,
                scope = this,
                timeout = 30_000
            )
        }
        syncResponses.take(3).collect()
        job.cancelAndJoin()

        assertEquals(2, requestCount.value)
        assertEquals(serverResponse1, syncResponses.replayCache[0])
        assertEquals(serverResponse2, syncResponses.replayCache[1])
        assertEquals("nextBatch2", currentBatchToken.value)
    }

    @Test
    fun shouldSyncLoopWithTimeoutStateAndInitialSyncState() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&timeout=0",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        json.encodeToString(serverResponse1),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
                addHandler {
                    delay(100)
                    throw HttpRequestTimeoutException(HttpRequestBuilder())
                }
                addHandler {
                    respond(
                        json.encodeToString(serverResponse2),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        )

        val stateResult = mutableListOf<SyncState>()
        val collector = launch {
            matrixRestClient.sync.currentSyncState.toCollection(stateResult)
        }
        matrixRestClient.sync.subscribeDeviceLists {
            matrixRestClient.sync.currentSyncState.first { it == INITIAL_SYNC }
        }
        matrixRestClient.sync.currentSyncState.first { it == STOPPED }
        val currentBatchToken = MutableStateFlow<String?>(null)
        val syncJob = launch {
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                currentBatchToken = currentBatchToken,
                scope = this,
                timeout = 300
            )
        }
        matrixRestClient.sync.currentSyncState.first { it == TIMEOUT }
        retry(10, 500.milliseconds, 50.milliseconds) {
            assertEquals(listOf(STOPPED, INITIAL_SYNC, RUNNING, TIMEOUT), stateResult)
        }
        matrixRestClient.sync.stop(wait = true)
        matrixRestClient.sync.currentSyncState.first { it == STOPPED }
        retry(10, 500.milliseconds, 50.milliseconds) {
            assertEquals(listOf(STOPPED, INITIAL_SYNC, RUNNING, TIMEOUT, STOPPING, STOPPED), stateResult)
        }
        collector.cancel()
        syncJob.cancelAndJoin()
    }

    @Test
    fun shouldSyncLoopWithoutInitialSyncState() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=ananas&timeout=0",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        json.encodeToString(serverResponse1),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=100",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        json.encodeToString(serverResponse2),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
                addHandler {
                    delay(50)
                    respond(
                        json.encodeToString(serverResponse2),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        )

        val stateResult = mutableListOf<SyncState>()
        val collector = launch {
            matrixRestClient.sync.currentSyncState.toCollection(stateResult)
        }
        matrixRestClient.sync.currentSyncState.first { it == STOPPED }
        matrixRestClient.sync.subscribeDeviceLists {
            matrixRestClient.sync.currentSyncState.first { it == STARTED }
        }
        val currentBatchToken = MutableStateFlow<String?>("ananas")
        val sync = launch {
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                currentBatchToken = currentBatchToken,
                scope = this,
                timeout = 100
            )
        }
        matrixRestClient.sync.currentSyncState.first { it == RUNNING }
        retry(10, 500.milliseconds, 50.milliseconds) {
            assertEquals(listOf(STOPPED, STARTED, RUNNING), stateResult)
        }
        sync.cancel()
        collector.cancel()
    }

    @Test
    fun shouldSyncLoopWithErrorState() = runTest {
        val requestCount = MutableStateFlow(1)
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.value) {
                        1 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?timeout=0",
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
                                "{}",
                                HttpStatusCode.BadRequest,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                }
            }
        )

        val stateResult = mutableListOf<SyncState>()
        val collector = launch {
            matrixRestClient.sync.currentSyncState.toCollection(stateResult)
        }
        val currentBatchToken = MutableStateFlow<String?>(null)
        val sync = launch {
            matrixRestClient.sync.start(
                filter = null,
                setPresence = null,
                currentBatchToken = currentBatchToken,
                asUserId = null,
                scope = this
            )
        }
        matrixRestClient.sync.currentSyncState.first { it == ERROR }
        retry(10, 500.milliseconds, 50.milliseconds) {
            assertContains(stateResult, ERROR)
        }
        sync.cancel()
        collector.cancel()
    }

    @Test
    fun shouldSyncLoopAndHandleTimeout() = runTest {
        val requestCount = MutableStateFlow(0)
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.value) {
                        0 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?since=a&timeout=0",
                                request.url.fullPath
                            )
                            assertEquals(HttpMethod.Get, request.method)
                            requestCount.value++
                            delay(100)
                            throw HttpRequestTimeoutException(HttpRequestBuilder())
                        }

                        else -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?since=a&timeout=0",
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
            })
        val job = launch {
            matrixRestClient.sync.start(timeout = 100, currentBatchToken = MutableStateFlow("a"), scope = this)
        }
        requestCount.first { it == 2 } shouldBe 2
        job.cancelAndJoin()
    }

    @Test
    fun shouldRetrySyncLoopOnError() = runTest {
        val requestCount = MutableStateFlow(0)
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.value) {
                        0 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&timeout=0",
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

                        1 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=0",
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

                        2 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=0",
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
        )

        val currentBatchToken = MutableStateFlow<String?>(null)
        val syncResponses = MutableSharedFlow<Response>(replay = 5)
        matrixRestClient.sync.subscribeSyncResponse { syncResponses.emit(it) }
        val job = launch {
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                currentBatchToken = currentBatchToken,
                scope = this,
                timeout = 30_000
            )
        }
        syncResponses.take(3).collect()
        job.cancelAndJoin()

        assertEquals(3, requestCount.value)
        assertEquals(serverResponse1, syncResponses.replayCache[0])
        assertEquals(serverResponse2, syncResponses.replayCache[1])
        assertEquals("nextBatch2", currentBatchToken.value)
    }

    @Test
    fun shouldRetrySyncLoopOnSubscriberError() = runTest {
        val requestCount = MutableStateFlow(0)
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.value) {
                        0, 1 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&timeout=0",
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
        )

        val currentBatchToken = MutableStateFlow<String?>(null)
        val syncResponses = MutableSharedFlow<Response>(replay = 5)
        var subscribeCall = 0
        matrixRestClient.sync.subscribeSyncResponse {
            subscribeCall++
            when (subscribeCall) {
                1 -> throw RuntimeException("dino")
                else -> syncResponses.emit(it)
            }
        }
        val job = launch {
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                currentBatchToken = currentBatchToken,
                scope = this,
                timeout = 0
            )
        }
        syncResponses.take(3).collect()
        job.cancelAndJoin()

        assertEquals(2, requestCount.value)
        assertEquals(serverResponse1, syncResponses.replayCache[0])
        assertEquals(serverResponse2, syncResponses.replayCache[1])
        assertEquals("nextBatch2", currentBatchToken.value)
    }

    @Test
    fun shouldEmitEvents() = runTest {
        val response = Response(
            nextBatch = "nextBatch1",
            accountData = Response.GlobalAccountData(
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
            deviceLists = Response.DeviceLists(emptySet(), emptySet()),
            oneTimeKeysCount = emptyMap(),
            presence = Response.Presence(
                listOf(
                    Event.EphemeralEvent(
                        PresenceEventContent(Presence.ONLINE),
                        sender = UserId("dino", "server")
                    )
                )
            ),
            room = Response.Rooms(
                join = mapOf(
                    RoomId("room1", "Server") to Response.Rooms.JoinedRoom(
                        timeline = Response.Rooms.Timeline(
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
                        state = Response.Rooms.State(
                            listOf(
                                Event.StateEvent(
                                    MemberEventContent(membership = Membership.JOIN),
                                    EventId("event2"),
                                    UserId("user", "server"),
                                    RoomId("room1", "server"),
                                    1235L,
                                    stateKey = UserId("joinedUser", "server").toString()
                                )
                            )
                        ),
                        ephemeral = Response.Rooms.JoinedRoom.Ephemeral(emptyList()), //TODO
                        accountData = Response.Rooms.RoomAccountData(
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
                    RoomId("room2", "Server") to Response.Rooms.LeftRoom(
                        timeline = Response.Rooms.Timeline(
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
                        state = Response.Rooms.State(
                            listOf(
                                Event.StateEvent(
                                    MemberEventContent(membership = Membership.JOIN),
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
                    RoomId("room3", "Server") to Response.Rooms.InvitedRoom(
                        Response.Rooms.InvitedRoom.InviteState(
                            listOf(
                                Event.StrippedStateEvent(
                                    MemberEventContent(membership = Membership.INVITE),
                                    null,
                                    UserId("user", "server"),
                                    RoomId("room3", "server"),
                                    stateKey = UserId("joinedUser", "server").toString()
                                )
                            )
                        )
                    )
                )
            ),
            toDevice = Response.ToDevice(
                listOf(
                    ToDeviceEvent(
                        RoomKeyEventContent(RoomId("room", "server"), "se", "sk", Megolm),
                        UserId("dino", "server")
                    )
                )
            )
        )
        val inChannel = Channel<Response>()

        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler {
                    respond(
                        json.encodeToString(inChannel.receive()),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        )

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
            val sync = launch {
                matrixRestClient.sync.start(scope = this, currentBatchToken = currentSyncBatchToken)
            }

            inChannel.send(response)

            currentSyncBatchToken.first { it != null }

            assertEquals(10, allEventsCount.value)
            assertEquals(2, messageEventsCount.value)
            assertEquals(3, memberEventsCount.value)
            assertEquals(1, presenceEventsCount.value)
            assertEquals(1, roomKeyEventsCount.value)
            assertEquals(1, globalAccountDataEventsCount.value)
            assertEquals(2, roomAccountDataEventsCount.value)

            sync.cancel()
        }
    }

    @Test
    fun shouldDealWithMultipleStartsAndStops() = runTest {
        val response = Response(
            nextBatch = "nextBatch1",
            accountData = Response.GlobalAccountData(emptyList()),
            deviceLists = Response.DeviceLists(emptySet(), emptySet()),
            oneTimeKeysCount = emptyMap(),
            presence = Response.Presence(emptyList()),
            room = Response.Rooms(
                join = mapOf(
                    RoomId("room", "Server") to Response.Rooms.JoinedRoom(
                        timeline = Response.Rooms.Timeline(
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
            toDevice = Response.ToDevice(emptyList())
        )
        val inChannel = Channel<Response>()

        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler {
                    respond(
                        json.encodeToString(inChannel.receive()),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        )

        val allEventsCount = MutableStateFlow(0)
        matrixRestClient.sync.subscribeAllEvents { allEventsCount.update { it + 1 } }

        launch {
            matrixRestClient.sync.start(scope = this)
        }
        launch {
            matrixRestClient.sync.start(scope = this)
        }

        inChannel.send(response)
        inChannel.send(response)

        allEventsCount.first { it == 2 }

        matrixRestClient.sync.stop()
        matrixRestClient.sync.stop()
    }

    @Test
    fun shouldAllowOnlyOneSyncAtATime() = runTest(dispatchTimeoutMs = 4_000) {
        val response = Response(
            nextBatch = "nextBatch1",
            accountData = Response.GlobalAccountData(emptyList()),
            deviceLists = Response.DeviceLists(emptySet(), emptySet()),
            oneTimeKeysCount = emptyMap(),
            presence = Response.Presence(emptyList()),
            room = Response.Rooms(
                join = mapOf(
                    RoomId("room", "Server") to Response.Rooms.JoinedRoom(
                        timeline = Response.Rooms.Timeline(
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
            toDevice = Response.ToDevice(emptyList())
        )
        val matrixClientServerApiClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory(withDefaultResponse = false) {
                addHandler {
                    respond(
                        json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })

        val allEventsCount = MutableStateFlow(0)
        launch(Dispatchers.Default) {
            matrixClientServerApiClient.sync.startOnce(timeout = 0L) {
                delay(500)
                allEventsCount.update { it + 1 }
            }
        }
        launch(Dispatchers.Default) {
            matrixClientServerApiClient.sync.startOnce(timeout = 0L) {
                delay(500)
                allEventsCount.update { it + 1 }
            }
        }

        launch(Dispatchers.Default) {
            delay(200)
            allEventsCount.value shouldBe 0
            delay(500)
            allEventsCount.value shouldBe 1
            delay(500)
            allEventsCount.value shouldBe 2
        }.join()
    }
}
package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.longs.shouldBeGreaterThan
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
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.subscribeContent
import net.folivo.trixnity.core.subscribeEachEvent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncApiClientTest : TrixnityBaseTest() {
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
    fun shouldSync() = runTest(timeout = 30.seconds) {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
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
            timeout = 1234.milliseconds
        ).getOrThrow()
        assertEquals("s72595_4483_1934", result.nextBatch)
        assertEquals(1, result.presence?.events?.size)
        assertEquals(2, result.accountData?.events?.size)
        assertEquals(1, result.room?.join?.size)
        result.room?.join?.get(RoomId("!726s6s6q:example.com"))?.timeline?.events?.lastOrNull()
            .shouldNotBeNull()
            .shouldBeInstanceOf<MessageEvent<*>>()
            .unsigned.also {
                it?.redactedBecause.shouldNotBeNull().shouldBeInstanceOf<MessageEvent<*>>()
                it.relations?.thread?.latestEvent.shouldNotBeNull().shouldBeInstanceOf<MessageEvent<*>>()
            }
        assertEquals(1, result.room?.invite?.size)
        assertEquals(0, result.room?.leave?.size)
    }

    @Test
    fun shouldSyncAndHandleLongTimeout() = runTest(timeout = 30.seconds) {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
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
            timeout = 200.milliseconds
        )
    }

    // #################################################
    // ######## sync loop ##############################
    // #################################################

    @Test
    fun `should do initial sync`() = runTest {
        val syncBatchTokenStore = SyncBatchTokenStore.inMemory()
        val requestCount = MutableStateFlow(0)
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.value) {
                        0 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&timeout=0",
                                request.url.fullPath
                            )
                            assertEquals(HttpMethod.Get, request.method)
                            requestCount.value++
                            withContext(backgroundScope.coroutineContext) {
                                delay(100.milliseconds)
                            }
                            respond(
                                json.encodeToString(serverResponse1),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        else -> {
                            delay(5_000)
                            respond(
                                json.encodeToString(serverResponse1),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                }
            },
            syncBatchTokenStore = syncBatchTokenStore,
            coroutineContext = backgroundScope.coroutineContext,
        ).use { matrixRestClient ->
            val syncResponses = MutableSharedFlow<Response>(replay = 5)
            matrixRestClient.sync.subscribe { syncResponses.emit(it.syncResponse) }
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                timeout = 30_000.milliseconds
            )
            runCurrent()
            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.INITIAL_SYNC
            delay(31.seconds)
            matrixRestClient.sync.currentSyncState.first { it == SyncState.RUNNING }
            syncResponses.take(1).collect()
            matrixRestClient.sync.stop()
            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.STOPPED

            requestCount.value shouldBe 1
            syncResponses.replayCache[0] shouldBe serverResponse1
            syncBatchTokenStore.getSyncBatchToken() shouldBe "nextBatch1"
            currentTime shouldBeGreaterThan 30_000
        }
    }

    @Test
    fun `should do sync loop`() = runTest {
        val syncBatchTokenStore = SyncBatchTokenStore.inMemory()
        val requestCount = MutableStateFlow(0)
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.value) {
                        0 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=some&timeout=0",
                                request.url.fullPath
                            )
                            assertEquals(HttpMethod.Get, request.method)
                            requestCount.value++
                            withContext(backgroundScope.coroutineContext) {
                                delay(100.milliseconds)
                            }
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
                            withContext(backgroundScope.coroutineContext) {
                                delay(30.seconds)
                            }
                            respond(
                                json.encodeToString(serverResponse2),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        else -> {
                            delay(5_000)
                            respond(
                                json.encodeToString(serverResponse2),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                }
            },
            syncBatchTokenStore = syncBatchTokenStore,
            coroutineContext = backgroundScope.coroutineContext,
        ).use { matrixRestClient ->
            syncBatchTokenStore.setSyncBatchToken("some")
            val syncResponses = MutableSharedFlow<Response>(replay = 5)
            matrixRestClient.sync.subscribe { syncResponses.emit(it.syncResponse) }
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                timeout = 30_000.milliseconds
            )
            runCurrent()
            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.STARTED
            delay(31.seconds)
            matrixRestClient.sync.currentSyncState.first { it == SyncState.RUNNING }
            syncResponses.take(2).collect()
            matrixRestClient.sync.stop()
            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.STOPPED

            requestCount.value shouldBe 2
            syncResponses.replayCache[0] shouldBe serverResponse1
            syncResponses.replayCache[1] shouldBe serverResponse2
            syncBatchTokenStore.getSyncBatchToken() shouldBe "nextBatch2"
            currentTime shouldBeGreaterThan 30_000
        }
    }

    @Test
    fun `should do sync once`() = runTest {
        val syncBatchTokenStore = SyncBatchTokenStore.inMemory()
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=some&timeout=0", // started state
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        json.encodeToString(serverResponse1),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            syncBatchTokenStore = syncBatchTokenStore,
            coroutineContext = backgroundScope.coroutineContext,
        ).use { matrixRestClient ->
            syncBatchTokenStore.setSyncBatchToken("some")

            val states = async {
                matrixRestClient.sync.currentSyncState.take(4).toList()
            }

            matrixRestClient.sync.startOnce(
                filter = "someFilter", setPresence = Presence.ONLINE, timeout = 12.seconds
            ) {
                it.syncResponse shouldBe serverResponse1
            }.getOrThrow()

            states.await() shouldBe listOf(
                SyncState.STOPPED, SyncState.STARTED, SyncState.RUNNING, SyncState.STOPPED
            )
        }
    }

    @Test
    fun `should stop sync once on cancellation`() = runTest {
        val syncBatchTokenStore = SyncBatchTokenStore.inMemory()
        val requestStarted = MutableSharedFlow<CompletableDeferred<Unit>>()
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    val complete = CompletableDeferred<Unit>()
                    requestStarted.emit(complete)
                    complete.await()

                    fail("should be cancelled before complete.await()")
                }
            },
            syncBatchTokenStore = syncBatchTokenStore,
            coroutineContext = backgroundScope.coroutineContext,
        ).use { matrixRestClient ->
            syncBatchTokenStore.setSyncBatchToken("some")

            val startOnceJob = launch {
                matrixRestClient.sync.startOnce(
                    filter = "someFilter",
                    setPresence = Presence.ONLINE,
                ) {
                    fail("startOnce should be cancelled")
                }.getOrThrow()
            }

            requestStarted.first() // wait for request to be running
            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.STARTED
            matrixRestClient.sync.testOnlySyncOnceSize() shouldBe 1

            startOnceJob.cancelAndJoin()
            matrixRestClient.sync.testOnlySyncOnceSize() shouldBe 0

            matrixRestClient.sync.currentSyncState.first { it == SyncState.STOPPED }
        }
    }

    @Test
    fun `should stop sync loop on stop`() = runTest {
        val syncBatchTokenStore = SyncBatchTokenStore.inMemory()
        val requestStarted = MutableSharedFlow<CompletableDeferred<Unit>>()
        val requestCount = MutableStateFlow(0)
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.getAndUpdate { it + 1 }) {
                        0 -> {
                            val complete = CompletableDeferred<Unit>()
                            requestStarted.emit(complete)
                            complete.await()

                            respond(
                                json.encodeToString(serverResponse1),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        else -> {
                            val complete = CompletableDeferred<Unit>()
                            requestStarted.emit(complete)
                            complete.await()

                            fail("should be cancelled before complete.await()")
                        }
                    }
                }
            },
            syncBatchTokenStore = syncBatchTokenStore,
            coroutineContext = backgroundScope.coroutineContext,
        ).use { matrixRestClient ->
            syncBatchTokenStore.setSyncBatchToken("some")
            matrixRestClient.sync.start()

            matrixRestClient.sync.currentSyncState.first { it == SyncState.STARTED }
            requestStarted.first().complete(Unit)
            matrixRestClient.sync.currentSyncState.first { it == SyncState.RUNNING }

            // wait for second request but don't complete it
            requestStarted.first()

            requestCount.value shouldBe 2
            matrixRestClient.sync.stop()
            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.STOPPED
        }
    }

    @Test
    fun `should stop sync loop on sync once`() = runTest {
        val syncBatchTokenStore = SyncBatchTokenStore.inMemory()
        val requestCount = MutableStateFlow(0)
        val requestStarted = MutableSharedFlow<CompletableDeferred<Unit>>()
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    when (val count = requestCount.getAndUpdate { it + 1 }) {
                        0 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=some&timeout=0",
                                request.url.fullPath
                            )
                            assertEquals(HttpMethod.Get, request.method)

                            val complete = CompletableDeferred<Unit>()
                            requestStarted.emit(complete)
                            complete.await()

                            fail("first request should be cancelled because of syncOnce")
                        }

                        1 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=secondFilter&since=some&timeout=0", request.url.fullPath
                            )
                            assertEquals(HttpMethod.Get, request.method)
                            respond(
                                json.encodeToString(serverResponse1),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        2 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=30000",
                                request.url.fullPath
                            )
                            assertEquals(HttpMethod.Get, request.method)

                            val complete = CompletableDeferred<Unit>()
                            requestStarted.emit(complete)
                            complete.await()

                            respond(
                                json.encodeToString(serverResponse2),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        else -> {
                            fail("there should be no more requests: $count")
                        }
                    }
                }
            },
            syncBatchTokenStore = syncBatchTokenStore,
            coroutineContext = backgroundScope.coroutineContext,
        ).use { matrixRestClient ->
            syncBatchTokenStore.setSyncBatchToken("some")
            val syncResponses = MutableSharedFlow<Response>(replay = 5)
            matrixRestClient.sync.subscribe { syncResponses.emit(it.syncResponse) }
            matrixRestClient.sync.start(
                filter = "someFilter", setPresence = Presence.ONLINE, timeout = 30_000.milliseconds
            )
            matrixRestClient.sync.currentSyncState.first { it == SyncState.STARTED }
            requestStarted.first() // request handler is now waiting for
            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.STARTED // first request not finished yet

            matrixRestClient.sync.startOnce(filter = "secondFilter") { syncResponses ->
                syncResponses.syncResponse shouldBe serverResponse1
            }.getOrThrow()

            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.RUNNING
            matrixRestClient.sync.testOnlySyncOnceSize() shouldBe 0

            requestStarted.first().complete(Unit)

            syncResponses.take(2).collect()
            matrixRestClient.sync.stop()
            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.STOPPED

            requestCount.value shouldBe 3
            syncResponses.replayCache[0] shouldBe serverResponse1
            syncResponses.replayCache[1] shouldBe serverResponse2
            syncBatchTokenStore.getSyncBatchToken() shouldBe "nextBatch2"
        }
    }

    @Test
    fun `should sync loop with timeout state and initial sync state`() = runTest {
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
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
            },
        ).use { matrixRestClient ->
            val stateResult = MutableSharedFlow<SyncState>(replay = 5)

            launch {
                stateResult.emitAll(matrixRestClient.sync.currentSyncState.take(5))
            }

            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                timeout = 300.milliseconds
            )

            stateResult.take(4).toList() shouldBe
                    listOf(SyncState.STOPPED, SyncState.INITIAL_SYNC, SyncState.RUNNING, SyncState.TIMEOUT)

            matrixRestClient.sync.stop()

            stateResult.take(5).toList() shouldBe
                    listOf(
                        SyncState.STOPPED,
                        SyncState.INITIAL_SYNC,
                        SyncState.RUNNING,
                        SyncState.TIMEOUT,
                        SyncState.STOPPED
                    )
        }
    }

    @Test
    fun `should sync loop without initial sync state`() = runTest {
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            syncBatchTokenStore = SyncBatchTokenStore.inMemory().apply { setSyncBatchToken("ananas") },
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
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
        ).use { matrixRestClient ->

            val stateResult = MutableSharedFlow<SyncState>(replay = 3)

            launch {
                stateResult.emitAll(matrixRestClient.sync.currentSyncState.take(3))
            }

            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                timeout = 100.milliseconds
            )

            stateResult.take(3).toList() shouldBe
                    listOf(SyncState.STOPPED, SyncState.STARTED, SyncState.RUNNING)
        }
    }

    @Test
    fun `should sync loop with error state`() = runTest(timeout = 2.minutes) {
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler {
                    respond(
                        "Bad Request",
                        HttpStatusCode.BadRequest,
                    )
                }

                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/sync?timeout=0",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        json.encodeToString(serverResponse1),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        ).use { matrixRestClient ->
            val stateResult = MutableSharedFlow<SyncState>(replay = 6)

            launch {
                stateResult.emitAll(matrixRestClient.sync.currentSyncState.take(6))
            }

            matrixRestClient.sync.start(
                filter = null,
                setPresence = null,
            )

            stateResult.take(5).toList() shouldBe listOf(
                SyncState.STOPPED, SyncState.INITIAL_SYNC,
                SyncState.ERROR, SyncState.INITIAL_SYNC, SyncState.RUNNING
            )

            matrixRestClient.sync.stop()

            stateResult.take(6).toList() shouldBe listOf(
                SyncState.STOPPED, SyncState.INITIAL_SYNC,
                SyncState.ERROR, SyncState.INITIAL_SYNC, SyncState.RUNNING, SyncState.STOPPED
            )
        }
    }

    @Test
    fun `should sync loop and handle timeout`() = runTest {
        val requestCount = MutableStateFlow(0)
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    when (requestCount.value) {
                        0 -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?timeout=0",
                                request.url.fullPath
                            )
                            assertEquals(HttpMethod.Get, request.method)
                            requestCount.value++
                            delay(100)
                            throw HttpRequestTimeoutException(HttpRequestBuilder())
                        }

                        else -> {
                            assertEquals(
                                "/_matrix/client/v3/sync?timeout=0",
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
        ).use { matrixRestClient ->
            matrixRestClient.sync.start(
                timeout = 100.milliseconds,
            )

            requestCount.first { it == 2 } shouldBe 2
        }

    }


    @Test
    fun `should retry sync loop on error`() = runTest {
        val syncBatchTokenStore = SyncBatchTokenStore.inMemory()
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            syncBatchTokenStore = syncBatchTokenStore,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
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

                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=30000",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        "",
                        HttpStatusCode.NotFound,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }

                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=nextBatch1&timeout=0",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        json.encodeToString(serverResponse2),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }

                addHandler { request ->
                    respond(
                        json.encodeToString(serverResponse2),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        ).use { matrixRestClient ->
            val syncResponses = MutableSharedFlow<Response>(replay = 5)
            matrixRestClient.sync.subscribe { syncResponses.emit(it.syncResponse) }

            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                timeout = 30_000.milliseconds
            )

            syncResponses.take(3).collect()

            assertEquals(serverResponse1, syncResponses.replayCache[0])
            assertEquals(serverResponse2, syncResponses.replayCache[1])
            assertEquals("nextBatch2", syncBatchTokenStore.getSyncBatchToken())
        }
    }

    @Test
    fun `should retry sync loop on subscriber error`() = runTest {
        val syncBatchTokenStore = SyncBatchTokenStore.inMemory()
        val requestCount = MutableStateFlow(0)
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            syncBatchTokenStore = syncBatchTokenStore,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
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
        ).use { matrixRestClient ->
            val syncResponses = MutableSharedFlow<Response>(replay = 5)
            var subscribeCall = 0
            matrixRestClient.sync.subscribe {
                subscribeCall++
                when (subscribeCall) {
                    1 -> throw RuntimeException("dino")
                    else -> syncResponses.emit(it.syncResponse)
                }
            }

            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                timeout = 0.milliseconds
            )

            syncResponses.take(3).collect()

            matrixRestClient.sync.stop()

            assertEquals(2, requestCount.value)
            assertEquals(serverResponse1, syncResponses.replayCache[0])
            assertEquals(serverResponse2, syncResponses.replayCache[1])
            assertEquals("nextBatch2", syncBatchTokenStore.getSyncBatchToken())
        }
    }

    @Test
    fun `should emit events`() = runTest {
        val response = Response(
            nextBatch = "nextBatch1",
            accountData = Response.GlobalAccountData(
                listOf(
                    ClientEvent.GlobalAccountDataEvent(
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
                    ClientEvent.EphemeralEvent(
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
                                MessageEvent(
                                    RoomMessageEventContent.TextBased.Text("hi"),
                                    EventId("event1"),
                                    UserId("user", "server"),
                                    RoomId("room1", "server"),
                                    1234L
                                )
                            )
                        ),
                        state = Response.Rooms.State(
                            listOf(
                                ClientEvent.RoomEvent.StateEvent(
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
                                ClientEvent.RoomAccountDataEvent(
                                    FullyReadEventContent(EventId("event1")),
                                    RoomId("room1", "server")
                                ),
                                ClientEvent.RoomAccountDataEvent(
                                    UnknownEventContent(
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
                                MessageEvent(
                                    RoomMessageEventContent.TextBased.Notice("hi"),
                                    EventId("event4"),
                                    UserId("user", "server"),
                                    RoomId("room2", "server"),
                                    1234L
                                )
                            )
                        ),
                        state = Response.Rooms.State(
                            listOf(
                                ClientEvent.RoomEvent.StateEvent(
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
                                ClientEvent.StrippedStateEvent(
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
                    ClientEvent.ToDeviceEvent(
                        RoomKeyEventContent(RoomId("room", "server"), "se", "sk", EncryptionAlgorithm.Megolm),
                        UserId("dino", "server")
                    )
                )
            )
        )
        val inChannel = Channel<Response>()

        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler {
                    respond(
                        json.encodeToString(inChannel.receive()),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        ).use { matrixRestClient ->
            val allEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribeEachEvent { allEventsCount.update { it + 1 } }

            val messageEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribeContent<RoomMessageEventContent> { messageEventsCount.update { it + 1 } }

            val memberEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribeContent<MemberEventContent> { memberEventsCount.update { it + 1 } }

            val presenceEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribeContent<PresenceEventContent> { presenceEventsCount.update { it + 1 } }

            val roomKeyEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribeContent<RoomKeyEventContent> { roomKeyEventsCount.update { it + 1 } }

            val globalAccountDataEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribeContent<GlobalAccountDataEventContent> { globalAccountDataEventsCount.update { it + 1 } }

            val roomAccountDataEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribeContent<RoomAccountDataEventContent> { roomAccountDataEventsCount.update { it + 1 } }

            matrixRestClient.sync.start()

            inChannel.send(response)

            matrixRestClient.sync.currentSyncState.first { it == SyncState.RUNNING }

            assertEquals(10, allEventsCount.value)
            assertEquals(2, messageEventsCount.value)
            assertEquals(3, memberEventsCount.value)
            assertEquals(1, presenceEventsCount.value)
            assertEquals(1, roomKeyEventsCount.value)
            assertEquals(1, globalAccountDataEventsCount.value)
            assertEquals(1, roomAccountDataEventsCount.value)

            matrixRestClient.sync.stop()
        }
    }

    @Test
    fun `should deal with multiple starts and stops`() = runTest {
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
                                MessageEvent(
                                    RoomMessageEventContent.TextBased.Text("hi"),
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

        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler {
                    respond(
                        json.encodeToString(inChannel.receive()),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        ).use { matrixRestClient ->
            val allEventsCount = MutableStateFlow(0)
            matrixRestClient.sync.subscribeEachEvent { allEventsCount.update { it + 1 } }

            matrixRestClient.sync.start()
            matrixRestClient.sync.start()

            inChannel.send(response)
            inChannel.send(response)

            allEventsCount.first { it == 2 }

            matrixRestClient.sync.stop()
            matrixRestClient.sync.stop()
        }
    }

    @Test
    fun `should allow only one sync at a time`() = runTest {
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
                                MessageEvent(
                                    RoomMessageEventContent.TextBased.Text("hi"),
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

        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler {
                    respond(
                        json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        ).use { matrixRestClient ->
            val allEventsCount = MutableStateFlow(0)

            launch {
                matrixRestClient.sync.startOnce(
                    timeout = 0.milliseconds
                ) {
                    delay(500)
                    allEventsCount.update { it + 1 }
                }
            }

            launch {
                matrixRestClient.sync.startOnce(
                    timeout = 0.milliseconds
                ) {
                    delay(500)
                    allEventsCount.update { it + 1 }
                }
            }

            launch {
                allEventsCount.first { it == 0 }
                currentTime shouldBe 0
                allEventsCount.first { it == 1 }
                currentTime shouldBe 500
                allEventsCount.first { it == 2 }
                currentTime shouldBe 3000
            }.join()
        }
    }


    @Test
    fun `should stop sync loop`() = runTest {
        val requestCount = MutableStateFlow(0)
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            syncBatchTokenStore = SyncBatchTokenStore.inMemory().apply { setSyncBatchToken("some") },
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    requestCount.value++

                    assertEquals(
                        "/_matrix/client/v3/sync?filter=someFilter&set_presence=online&since=some&timeout=0",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        json.encodeToString(serverResponse1),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
        ).use { matrixRestClient ->
            val syncResponses = MutableSharedFlow<Response>(replay = 5)
            matrixRestClient.sync.subscribe { syncResponses.emit(it.syncResponse) }
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                timeout = 30_000.milliseconds
            )
            syncResponses.first()
            matrixRestClient.sync.stop()
            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.STOPPED
            requestCount.value shouldBe 1
        }
    }

    @Test
    fun `should allow multiple stop sync loop`() = runTest {
        val requestCount = MutableStateFlow(0)
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            syncBatchTokenStore = SyncBatchTokenStore.inMemory().apply { setSyncBatchToken("some") },
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
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
                addHandler { request ->
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
            }
        ).use { matrixRestClient ->
            matrixRestClient.sync.start(
                filter = "someFilter",
                setPresence = Presence.ONLINE,
                timeout = 30_000.milliseconds
            )

            requestCount.first { it == 2 }

            val stopAndWait1 = launch { matrixRestClient.sync.stop() }
            val stopAndWait2 = launch { matrixRestClient.sync.stop() }

            stopAndWait1.join()
            stopAndWait2.join()

            matrixRestClient.sync.currentSyncState.value shouldBe SyncState.STOPPED
        }
    }

    @Test
    fun `should cancel sync once subscriber when stopped`() = runTest {
        val barrier = CompletableDeferred<Unit>()
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            syncBatchTokenStore = SyncBatchTokenStore.inMemory().apply { setSyncBatchToken("some") },
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    barrier.complete(Unit)

                    delay(Duration.INFINITE)

                    respondBadRequest()
                }
            }
        ).use { matrixRestClient ->
            val startOnceJob = launch {
                matrixRestClient.sync.startOnce { }
            }

            barrier.await()

            matrixRestClient.sync.stop()

            startOnceJob.isCancelled shouldBe true
        }
    }

    @Test
    fun `should restart sync when settings change`() = runTest {
        val firstEndpoint = CompletableDeferred<Unit>()
        val secondEndpoint = CompletableDeferred<Unit>()
        MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            coroutineContext = backgroundScope.coroutineContext,
            syncBatchTokenStore = SyncBatchTokenStore.inMemory().apply { setSyncBatchToken("some") },
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/_matrix/client/v3/sync?set_presence=online&since=some&timeout=0" -> {
                            firstEndpoint.complete(Unit)
                            delay(Duration.INFINITE)
                        }

                        "/_matrix/client/v3/sync?set_presence=offline&since=some&timeout=0" -> {
                            secondEndpoint.complete(Unit)
                            return@addHandler respond(
                                json.encodeToString(serverResponse1),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }

                    respondBadRequest()
                }
            }
        ).use { matrixRestClient ->
            val states = async { matrixRestClient.sync.currentSyncState.take(3).toList() }
            matrixRestClient.sync.start(setPresence = Presence.ONLINE)
            firstEndpoint.await()
            matrixRestClient.sync.start(setPresence = Presence.OFFLINE)
            secondEndpoint.await()

            states.await() shouldBe listOf(SyncState.STOPPED, SyncState.STARTED, SyncState.RUNNING)
        }
    }
}
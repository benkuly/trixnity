package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.thread
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    fun `should do initial sync`() = runTestWithCoroutineScope { coroutineScope ->
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
                            withContext(coroutineScope.coroutineContext) {
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
            syncCoroutineScope = coroutineScope,
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
    fun `should do sync loop`() = runTestWithCoroutineScope { coroutineScope ->
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
                            withContext(coroutineScope.coroutineContext) {
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
                            withContext(coroutineScope.coroutineContext) {
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
            syncCoroutineScope = coroutineScope,
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
    fun `should do sync once`() = runTestWithCoroutineScope { coroutineScope ->
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
            syncCoroutineScope = coroutineScope,
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
    fun `should stop sync once on cancellation`() = runTestWithCoroutineScope { coroutineScope ->
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
            syncCoroutineScope = coroutineScope,
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
    fun `should stop sync loop on stop`() = runTestWithCoroutineScope { coroutineScope ->
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
            syncCoroutineScope = coroutineScope,
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
    fun `should stop sync loop on sync once`() = runTestWithCoroutineScope { coroutineScope ->
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
            syncCoroutineScope = coroutineScope,
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
}
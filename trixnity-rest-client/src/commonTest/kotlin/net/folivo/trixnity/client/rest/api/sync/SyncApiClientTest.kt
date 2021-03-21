package net.folivo.trixnity.client.rest.api.sync

import co.touchlab.stately.concurrency.AtomicInt
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.matrix.restclient.api.sync.SyncResponse
import net.folivo.matrix.restclient.api.sync.SyncResponse.*
import net.folivo.matrix.restclient.api.sync.SyncResponse.Presence
import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.MatrixClientProperties
import net.folivo.trixnity.client.rest.MatrixClientProperties.MatrixHomeServerProperties
import net.folivo.trixnity.client.rest.api.sync.Presence.ONLINE
import net.folivo.trixnity.client.rest.runBlockingTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncApiClientTest {
    private val json = Json {
        encodeDefaults = true
    }

    @BeforeTest
    fun reset() {
        InMemorySyncBatchTokenService.reset()
    }

    @Test
    fun shouldSyncOnce() = runBlockingTest {
        val response = SyncResponse(
            nextBatch = "nextBatch",
            accountData = AccountData(emptyList()),
            deviceLists = DeviceLists(emptyList(), emptyList()),
            deviceOneTimeKeysCount = emptyMap(),
            presence = Presence(emptyList()),
            room = Rooms(emptyMap(), emptyMap(), emptyMap()),
            toDevice = ToDevice(emptyList())
        )
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(
                    "/_matrix/client/r0/sync?filter=someFilter&full_state=true&set_presence=online&since=someSince&timeout=1234",
                    request.url.fullPath
                )
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
        val result = matrixClient.sync.syncOnce(
            filter = "someFilter",
            fullState = true,
            setPresence = ONLINE,
            since = "someSince",
            timeout = 1234
        )
        assertEquals(response, result)
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
        val matrixClient = MatrixClient(

            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
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

        val result = matrixClient.sync.syncLoop(
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
        val matrixClient = MatrixClient(

            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
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
        
        val result = matrixClient.sync.syncLoop(
            filter = "someFilter",
            setPresence = ONLINE
        ).take(2).toList()

        assertEquals(4, requestCount.get())
        assertEquals(response1, result[0])
        assertEquals(response2, result[1])
        assertEquals("nextBatch2", InMemorySyncBatchTokenService.getBatchToken())
    }
}
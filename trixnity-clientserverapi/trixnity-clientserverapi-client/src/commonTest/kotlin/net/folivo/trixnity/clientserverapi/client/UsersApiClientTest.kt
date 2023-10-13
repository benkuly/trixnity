package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.clientserverapi.model.users.Filters
import net.folivo.trixnity.clientserverapi.model.users.GetProfile
import net.folivo.trixnity.clientserverapi.model.users.SearchUsers
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UsersApiClientTest {

    @Test
    fun shouldSetDisplayName() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/profile/@user:server/displayname", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
                        """{"displayname":"someDisplayName"}""",
                        request.body.toByteArray().decodeToString()
                    )
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.users.setDisplayName(UserId("user", "server"), "someDisplayName")
    }

    @Test
    fun shouldGetDisplayName() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/profile/@user:server/displayname", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{"displayname":"someDisplayName"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        assertEquals("someDisplayName", matrixRestClient.users.getDisplayName(UserId("user", "server")).getOrThrow())
    }

    @Test
    fun shouldSetAvatarUrl() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/profile/@user:server/avatar_url", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
                        """{"avatar_url":"mxc://localhost/123456"}""",
                        request.body.toByteArray().decodeToString()
                    )
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.users.setAvatarUrl(UserId("user", "server"), "mxc://localhost/123456")
    }

    @Test
    fun shouldGetAvatarUrl() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/profile/@user:server/avatar_url", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{"avatar_url":"mxc://localhost/123456"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        assertEquals(
            "mxc://localhost/123456",
            matrixRestClient.users.getAvatarUrl(UserId("user", "server")).getOrThrow()
        )
    }

    @Test
    fun shouldGetProfile() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/profile/@user:server", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{"avatar_url":"mxc://localhost/123456","displayname":"someDisplayName"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        assertEquals(
            GetProfile.Response("someDisplayName", "mxc://localhost/123456"),
            matrixRestClient.users.getProfile(UserId("user", "server")).getOrThrow()
        )
    }

    @Test
    fun shouldSetNullForMissingProfileValues() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/profile/@user:server", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        assertEquals(
            GetProfile.Response(null, null),
            matrixRestClient.users.getProfile(UserId("user", "server")).getOrThrow()
        )
    }

    @Test
    fun shouldSetPresence() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/presence/@user:server/status", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                                {
                                  "presence":"online",
                                  "status_msg":"I am here."
                                }
                            """.trimToFlatJson()
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.users.setPresence(
            UserId("@user:server"), Presence.ONLINE, "I am here."
        ).getOrThrow()
    }

    @Test
    fun shouldGetPresence() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/presence/@user:server/status", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                                {
                                  "presence": "unavailable",
                                  "last_active_ago": 420845
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.users.getPresence(UserId("@user:server")).getOrThrow()
        assertEquals(PresenceEventContent(Presence.UNAVAILABLE, lastActiveAgo = 420845), result)
    }

    @Test
    fun shouldSendToDevice() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/sendToDevice/m.room_key/txnId", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                                {
                                  "messages":{
                                    "@alice:example.com":{
                                      "TLLBEANAAG":{
                                        "algorithm":"m.megolm.v1.aes-sha2",
                                        "room_id":"!Cuyf34gef24t:localhost",
                                        "session_id":"X3lUlvLELLYxeTx4yOVu6UDpasGEVO0Jbu+QFnm0cKQ",
                                        "session_key":"AgAAAADxKHa9uFxcXzwYoNueL5Xqi69IkD4sni8LlfJL7qNBEY..."
                                      }
                                    }
                                  }
                                }
                            """.trimToFlatJson()
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.users.sendToDevice(
            mapOf(
                UserId("@alice:example.com") to mapOf(
                    "TLLBEANAAG" to RoomKeyEventContent(
                        roomId = RoomId("!Cuyf34gef24t:localhost"),
                        sessionId = "X3lUlvLELLYxeTx4yOVu6UDpasGEVO0Jbu+QFnm0cKQ",
                        sessionKey = "AgAAAADxKHa9uFxcXzwYoNueL5Xqi69IkD4sni8LlfJL7qNBEY...",
                        algorithm = Megolm
                    )
                )
            ),
            transactionId = "txnId"
        ).getOrThrow()
    }

    @Test
    fun shouldSetFilter() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/user/@dino:server/filter", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                                {
                                    "room":{
                                        "state":{
                                            "lazy_load_members":true
                                        }
                                    }
                                }
                            """.trimToFlatJson()
                    respond(
                        """{"filter_id":"0"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val response = matrixRestClient.users.setFilter(
            UserId("dino", "server"),
            Filters(room = Filters.RoomFilter(state = Filters.RoomFilter.StateFilter(lazyLoadMembers = true)))
        ).getOrThrow()
        assertEquals("0", response)
    }

    @Test
    fun shouldGetFilter() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/user/@dino:server/filter/0", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                                {
                                    "room":{
                                        "state":{
                                            "lazy_load_members":true
                                        }
                                    }
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val response = matrixRestClient.users.getFilter(UserId("dino", "server"), "0").getOrThrow()
        assertEquals(
            Filters(room = Filters.RoomFilter(state = Filters.RoomFilter.StateFilter(lazyLoadMembers = true))),
            response
        )
    }

    @Test
    fun shouldGetAccountData() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@alice:example.com/account_data/m.direct",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{"@bob:server":["!someRoom:server"]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.users.getAccountData<DirectEventContent>(UserId("alice", "example.com")).getOrThrow()
            .shouldBe(
                DirectEventContent(
                    mapOf(
                        UserId("bob", "server") to setOf(RoomId("someRoom", "server"))
                    )
                )
            )
    }

    @Test
    fun shouldGetAccountDataWithKey() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@alice:example.com/account_data/m.secret_storage.key.key1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """{"name":"name","algorithm":"m.secret_storage.v1.aes-hmac-sha2"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.users.getAccountData<SecretKeyEventContent>(
            UserId("alice", "example.com"), key = "key1"
        ).getOrThrow().shouldBe(SecretKeyEventContent.AesHmacSha2Key("name"))
    }

    @Test
    fun shouldSetAccountData() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@alice:example.com/account_data/m.direct",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
                        """{"@bob:server":["!someRoom:server"]}""",
                        request.body.toByteArray().decodeToString()
                    )
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.users.setAccountData(
            DirectEventContent(
                mapOf(
                    UserId("bob", "server") to setOf(RoomId("someRoom", "server"))
                )
            ),
            UserId("alice", "example.com")
        ).getOrThrow()
    }

    @Test
    fun shouldSetAccountDataWithKey() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user/@alice:example.com/account_data/m.secret_storage.key.key1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
                        """{"algorithm":"m.secret_storage.v1.aes-hmac-sha2","name":"name"}""",
                        request.body.toByteArray().decodeToString()
                    )
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.users.setAccountData(
            SecretKeyEventContent.AesHmacSha2Key("name"),
            UserId("alice", "example.com"),
            key = "key1"
        ).getOrThrow()
    }

    @Test
    fun shouldSearchUsers() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/user_directory/search",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """{"search_term":"bob","limit":20}""",
                        request.body.toByteArray().decodeToString()
                    )
                    assertEquals(
                        "de",
                        request.headers["Accept-Language"]
                    )
                    respond(
                        """{"limited":true,"results":[{"display_name":"bob","avatar_url":"mxc://localhost/123456","user_id":"@bob:localhost"}]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.users.searchUsers("bob", "de", 20).getOrThrow() shouldBe
                SearchUsers.Response(
                    limited = true,
                    results = listOf(
                        SearchUsers.Response.SearchUser(
                            "mxc://localhost/123456",
                            "bob",
                            UserId("@bob:localhost")
                        )
                    )
                )
    }
}
package net.folivo.trixnity.client.api.users

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.runBlockingTest
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import kotlin.test.Test
import kotlin.test.assertEquals

class UsersApiClientTest {

    @Test
    fun shouldSetDisplayName() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/profile/%40user%3Aserver/displayname", request.url.fullPath)
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
                }
            })
        matrixRestClient.users.setDisplayName(UserId("user", "server"), "someDisplayName")
    }

    @Test
    fun shouldGetDisplayName() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/profile/%40user%3Aserver/displayname", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """{"displayname":"someDisplayName"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        assertEquals("someDisplayName", matrixRestClient.users.getDisplayName(UserId("user", "server")))
    }

    @Test
    fun shouldSetAvatarUrl() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/profile/%40user%3Aserver/avatar_url", request.url.fullPath)
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
                }
            })
        matrixRestClient.users.setAvatarUrl(UserId("user", "server"), "mxc://localhost/123456")
    }

    @Test
    fun shouldGetAvatarUrl() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/profile/%40user%3Aserver/avatar_url", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """{"avatar_url":"mxc://localhost/123456"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        assertEquals("mxc://localhost/123456", matrixRestClient.users.getAvatarUrl(UserId("user", "server")))
    }

    @Test
    fun shouldGetProfile() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/profile/%40user%3Aserver", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """{"avatar_url":"mxc://localhost/123456","displayname":"someDisplayName"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        assertEquals(
            GetProfileResponse("someDisplayName", "mxc://localhost/123456"),
            matrixRestClient.users.getProfile(UserId("user", "server"))
        )
    }

    @Test
    fun shouldSetNullForMissingProfileValues() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/profile/%40user%3Aserver", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """{}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        assertEquals(
            GetProfileResponse(null, null),
            matrixRestClient.users.getProfile(UserId("user", "server"))
        )
    }

    @Test
    fun shouldGetWhoami() = runBlockingTest {
        val response = WhoAmIResponse(UserId("user", "server"), "ABCDEF")
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/account/whoami", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            Json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.users.whoAmI()
        assertEquals(UserId("user", "server"), result)
    }

    @Test
    fun shouldSetPresence() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/presence/%40user%3Aserver/status", request.url.fullPath)
                        assertEquals(HttpMethod.Put, request.method)
                        request.body.toByteArray().decodeToString().shouldEqualJson(
                            """
                                {
                                  "presence": "online",
                                  "status_msg": "I am here."
                                }
                            """.trimIndent()
                        )
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.users.setPresence(
            UserId("@user:server"), PresenceEventContent.Presence.ONLINE, "I am here."
        )
    }

    @Test
    fun shouldGetPresence() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/presence/%40user%3Aserver/status", request.url.fullPath)
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
                }
            })
        val result = matrixRestClient.users.getPresence(UserId("@user:server"))
        assertEquals(PresenceEventContent(PresenceEventContent.Presence.UNAVAILABLE, lastActiveAgo = 420845), result)
    }

    @Test
    fun shouldSendToDevice() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/sendToDevice/m.room_key/tnxId", request.url.fullPath)
                        assertEquals(HttpMethod.Put, request.method)
                        request.body.toByteArray().decodeToString().shouldEqualJson(
                            """
                                {
                                  "messages": {
                                    "@alice:example.com": {
                                      "TLLBEANAAG": {
                                        "algorithm": "m.megolm.v1.aes-sha2",
                                        "room_id": "!Cuyf34gef24t:localhost",
                                        "session_id": "X3lUlvLELLYxeTx4yOVu6UDpasGEVO0Jbu+QFnm0cKQ",
                                        "session_key": "AgAAAADxKHa9uFxcXzwYoNueL5Xqi69IkD4sni8LlfJL7qNBEY..."
                                      }
                                    }
                                  }
                                }
                            """.trimIndent()
                        )
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
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
            transactionId = "tnxId"
        )
    }

    @Test
    fun shouldSetFilter() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/user/%40dino%3Aserver/filter", request.url.fullPath)
                        assertEquals(HttpMethod.Post, request.method)
                        request.body.toByteArray().decodeToString().shouldEqualJson(
                            """
                                {
                                    "room":{
                                        "state":{
                                            "lazy_load_members":true
                                        }
                                    }
                                }
                            """.trimIndent()
                        )
                        respond(
                            """{"filter_id":"0"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val response = matrixRestClient.users.setFilter(
            UserId("dino", "server"),
            Filters(room = RoomFilter(state = RoomFilter.StateFilter(lazyLoadMembers = true)))
        )
        assertEquals("0", response)
    }

    @Test
    fun shouldGetFilter() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/user/%40dino%3Aserver/filter/0", request.url.fullPath)
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
                }
            })
        val response = matrixRestClient.users.getFilter(UserId("dino", "server"), "0")
        assertEquals(Filters(room = RoomFilter(state = RoomFilter.StateFilter(lazyLoadMembers = true))), response)
    }

    @Test
    fun shouldGetAccountData() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/r0/user/%40alice%3Aexample%2Ecom/account_data/m.direct",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """{"@bob:server":["!someRoom:server"]}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.users.getAccountData<DirectEventContent>(UserId("alice", "example.com"))
            .shouldBe(
                DirectEventContent(
                    mapOf(
                        UserId("bob", "server") to setOf(RoomId("someRoom", "server"))
                    )
                )
            )
    }

    @Test
    fun shouldSetAccountData() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/r0/user/%40alice%3Aexample%2Ecom/account_data/m.direct",
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
                }
            })
        matrixRestClient.users.setAccountData(
            DirectEventContent(
                mapOf(
                    UserId("bob", "server") to setOf(RoomId("someRoom", "server"))
                )
            ),
            UserId("alice", "example.com")
        )
    }

    @Test
    fun shouldSearchUsers() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/r0/user_directory/search",
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
                }
            })
        matrixRestClient.users.searchUsers("bob", "de", 20) shouldBe
                SearchUsersResponse(
                    limited = true,
                    results = listOf(SearchUser("mxc://localhost/123456", "bob", UserId("@bob:localhost")))
                )
    }
}
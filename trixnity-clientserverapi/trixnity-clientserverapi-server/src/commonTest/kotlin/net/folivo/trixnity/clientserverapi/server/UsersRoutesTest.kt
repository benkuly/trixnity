package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.users.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.Presence.ONLINE
import net.folivo.trixnity.core.model.events.m.Presence.UNAVAILABLE
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class UsersRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: UsersApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                usersApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @Test
    fun shouldSetDisplayName() = testApplication {
        initCut()
        everySuspending { handlerMock.setDisplayName(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/profile/@user:server/displayname") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"displayname":"someDisplayName"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setDisplayName(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.requestBody shouldBe SetDisplayName.Request("someDisplayName")
            })
        }
    }

    @Test
    fun shouldGetDisplayName() = testApplication {
        initCut()
        everySuspending { handlerMock.getDisplayName(isAny()) }
            .returns(GetDisplayName.Response("someDisplayName"))
        val response = client.get("/_matrix/client/v3/profile/@user:server/displayname")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"displayname":"someDisplayName"}"""
        }
        verifyWithSuspend {
            handlerMock.getDisplayName(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
            })
        }
    }

    @Test
    fun shouldSetAvatarUrl() = testApplication {
        initCut()
        everySuspending { handlerMock.setAvatarUrl(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/profile/@user:server/avatar_url") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"avatar_url":"mxc://localhost/123456"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setAvatarUrl(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.requestBody shouldBe SetAvatarUrl.Request("mxc://localhost/123456")
            })
        }
    }

    @Test
    fun shouldGetAvatarUrl() = testApplication {
        initCut()
        everySuspending { handlerMock.getAvatarUrl(isAny()) }
            .returns(GetAvatarUrl.Response("mxc://localhost/123456"))
        val response = client.get("/_matrix/client/v3/profile/@user:server/avatar_url")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"avatar_url":"mxc://localhost/123456"}"""
        }
        verifyWithSuspend {
            handlerMock.getAvatarUrl(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
            })
        }
    }

    @Test
    fun shouldGetProfile() = testApplication {
        initCut()
        everySuspending { handlerMock.getProfile(isAny()) }
            .returns(GetProfile.Response("someDisplayName", "mxc://localhost/123456"))
        val response = client.get("/_matrix/client/v3/profile/@user:server")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"displayname":"someDisplayName","avatar_url":"mxc://localhost/123456"}"""
        }
        verifyWithSuspend {
            handlerMock.getProfile(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
            })
        }
    }

    @Test
    fun shouldSetPresence() = testApplication {
        initCut()
        everySuspending { handlerMock.setPresence(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/presence/@user:server/status") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "presence":"online",
                      "status_msg":"I am here."
                    }
                """.trimIndent()
                )
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setPresence(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.requestBody shouldBe SetPresence.Request(ONLINE, "I am here.")
            })
        }
    }

    @Test
    fun shouldGetPresence() = testApplication {
        initCut()
        everySuspending { handlerMock.getPresence(isAny()) }
            .returns(PresenceEventContent(UNAVAILABLE, lastActiveAgo = 420845))
        val response = client.get("/_matrix/client/v3/presence/@user:server/status") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "presence": "unavailable",
                  "last_active_ago": 420845
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getPresence(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
            })
        }
    }

    @Test
    fun shouldSendToDevice() = testApplication {
        initCut()
        everySuspending { handlerMock.sendToDevice(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/sendToDevice/m.room_key/txnId") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                   {
                      "messages":{
                        "@alice:example.com":{
                          "TLLBEANAAG":{
                            "room_id":"!Cuyf34gef24t:localhost",
                            "session_id":"X3lUlvLELLYxeTx4yOVu6UDpasGEVO0Jbu+QFnm0cKQ",
                            "session_key":"AgAAAADxKHa9uFxcXzwYoNueL5Xqi69IkD4sni8LlfJL7qNBEY...",
                            "algorithm":"m.megolm.v1.aes-sha2"
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
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.sendToDevice(assert {
                it.endpoint.txnId shouldBe "txnId"
                it.requestBody shouldBe SendToDevice.Request(
                    mapOf(
                        UserId("@alice:example.com") to mapOf(
                            "TLLBEANAAG" to RoomKeyEventContent(
                                roomId = RoomId("!Cuyf34gef24t:localhost"),
                                sessionId = "X3lUlvLELLYxeTx4yOVu6UDpasGEVO0Jbu+QFnm0cKQ",
                                sessionKey = "AgAAAADxKHa9uFxcXzwYoNueL5Xqi69IkD4sni8LlfJL7qNBEY...",
                                algorithm = EncryptionAlgorithm.Megolm
                            )
                        )
                    )
                )
            })
        }
    }

    @Test
    fun shouldSetFilter() = testApplication {
        initCut()
        everySuspending { handlerMock.setFilter(isAny()) }
            .returns(SetFilter.Response("0"))
        val response =
            client.post("/_matrix/client/v3/user/@user:server/filter") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody(
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
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"filter_id":"0"}"""
        }
        verifyWithSuspend {
            handlerMock.setFilter(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.requestBody shouldBe Filters(
                    room = Filters.RoomFilter(
                        state = Filters.RoomFilter.StateFilter(
                            lazyLoadMembers = true
                        )
                    )
                )
            })
        }
    }

    @Test
    fun shouldGetFilter() = testApplication {
        initCut()
        everySuspending { handlerMock.getFilter(isAny()) }
            .returns(
                Filters(
                    room = Filters.RoomFilter(
                        state = Filters.RoomFilter.StateFilter(
                            lazyLoadMembers = true
                        )
                    )
                )
            )
        val response = client.get("/_matrix/client/v3/user/@user:server/filter/0") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                    "room":{
                        "state":{
                            "lazy_load_members":true
                        }
                    }
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getFilter(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.endpoint.filterId shouldBe "0"
            })
        }
    }

    @Test
    fun shouldGetAccountData() = testApplication {
        initCut()
        everySuspending { handlerMock.getAccountData(isAny()) }
            .returns(
                DirectEventContent(
                    mapOf(UserId("bob", "server") to setOf(RoomId("someRoom", "server")))
                )
            )
        val response =
            client.get("/_matrix/client/v3/user/@alice:example.com/account_data/m.direct") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"@bob:server":["!someRoom:server"]}"""
        }
        verifyWithSuspend {
            handlerMock.getAccountData(assert {
                it.endpoint.type shouldBe "m.direct"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
            })
        }
    }

    @Test
    fun shouldGetAccountDataWithKey() = testApplication {
        initCut()
        everySuspending { handlerMock.getAccountData(isAny()) }
            .returns(SecretKeyEventContent.AesHmacSha2Key("name"))
        val response =
            client.get("/_matrix/client/v3/user/@alice:example.com/account_data/m.secret_storage.key.key1") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"algorithm":"m.secret_storage.v1.aes-hmac-sha2","name":"name"}"""
        }
        verifyWithSuspend {
            handlerMock.getAccountData(assert {
                it.endpoint.type shouldBe "m.secret_storage.key.key1"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
            })
        }
    }

    @Test
    fun shouldSetAccountData() = testApplication {
        initCut()
        everySuspending { handlerMock.setAccountData(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/user/@alice:example.com/account_data/m.direct") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"@bob:server":["!someRoom:server"]}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setAccountData(assert {
                it.endpoint.type shouldBe "m.direct"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe DirectEventContent(
                    mapOf(
                        UserId("bob", "server") to setOf(RoomId("someRoom", "server"))
                    )
                )
            })
        }
    }

    @Test
    fun shouldSetAccountDataWithKey() = testApplication {
        initCut()
        everySuspending { handlerMock.setAccountData(isAny()) }
            .returns(Unit)
        val response =
            client.put("/_matrix/client/v3/user/@alice:example.com/account_data/m.secret_storage.key.key1") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"name","algorithm":"m.secret_storage.v1.aes-hmac-sha2"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.setAccountData(assert {
                it.endpoint.type shouldBe "m.secret_storage.key.key1"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe SecretKeyEventContent.AesHmacSha2Key("name")
            })
        }
    }

    @Test
    fun shouldSearchUsers() = testApplication {
        initCut()
        everySuspending { handlerMock.searchUsers(isAny()) }
            .returns(
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
            )
        val response =
            client.post("/_matrix/client/v3/user_directory/search") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                header(HttpHeaders.AcceptLanguage, "de")
                setBody("""{"search_term":"bob","limit":20}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"limited":true,"results":[{"avatar_url":"mxc://localhost/123456","display_name":"bob","user_id":"@bob:localhost"}]}"""
        }
        verifyWithSuspend {
            handlerMock.searchUsers(assert {
                it.requestBody shouldBe SearchUsers.Request("bob", 20)
                it.call.request.headers[HttpHeaders.AcceptLanguage] shouldBe "de"
            })
        }
    }
}

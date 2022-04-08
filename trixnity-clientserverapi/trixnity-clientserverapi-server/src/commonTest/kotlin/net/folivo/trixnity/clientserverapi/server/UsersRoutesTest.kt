package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import io.mockative.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.users.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent.Presence.ONLINE
import net.folivo.trixnity.core.model.events.m.PresenceEventContent.Presence.UNAVAILABLE
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsersRoutesTest {
    private val json = createMatrixJson()
    private val mapping = createEventContentSerializerMappings()

    @OptIn(ConfigurationApi::class)
    @Mock
    val handlerMock = configure(mock(classOf<UsersApiHandler>())) { stubsUnitByDefault = true }

    private fun ApplicationTestBuilder.initCut() {
        application {
            install(Authentication) {
                matrixAccessTokenAuth {
                    authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
                }
            }
            matrixApiServer(json) {
                routing {
                    usersApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @AfterTest
    fun afterTest() {
        verify(handlerMock).hasNoUnmetExpectations()
        verify(handlerMock).hasNoUnverifiedExpectations()
    }

    @Test
    fun shouldSetDisplayName() = testApplication {
        initCut()
        val response =
            client.put("/_matrix/client/v3/profile/%40user%3Aserver/displayname") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"displayname":"someDisplayName"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::setDisplayName)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.requestBody shouldBe SetDisplayName.Request("someDisplayName")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetDisplayName() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getDisplayName)
            .whenInvokedWith(any())
            .then {
                GetDisplayName.Response("someDisplayName")
            }
        val response = client.get("/_matrix/client/v3/profile/%40user%3Aserver/displayname")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"displayname":"someDisplayName"}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::getDisplayName)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetAvatarUrl() = testApplication {
        initCut()
        val response =
            client.put("/_matrix/client/v3/profile/%40user%3Aserver/avatar_url") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"avatar_url":"mxc://localhost/123456"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::setAvatarUrl)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.requestBody shouldBe SetAvatarUrl.Request("mxc://localhost/123456")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetAvatarUrl() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getAvatarUrl)
            .whenInvokedWith(any())
            .then {
                GetAvatarUrl.Response("mxc://localhost/123456")
            }
        val response = client.get("/_matrix/client/v3/profile/%40user%3Aserver/avatar_url")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"avatar_url":"mxc://localhost/123456"}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::getAvatarUrl)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetProfile() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getProfile)
            .whenInvokedWith(any())
            .then {
                GetProfile.Response("someDisplayName", "mxc://localhost/123456")
            }
        val response = client.get("/_matrix/client/v3/profile/%40user%3Aserver")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"displayname":"someDisplayName","avatar_url":"mxc://localhost/123456"}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::getProfile)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetPresence() = testApplication {
        initCut()
        val response =
            client.put("/_matrix/client/v3/presence/%40user%3Aserver/status") {
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
        verify(handlerMock).suspendFunction(handlerMock::setPresence)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.requestBody shouldBe SetPresence.Request(ONLINE, "I am here.")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetPresence() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getPresence)
            .whenInvokedWith(any())
            .then {
                PresenceEventContent(UNAVAILABLE, lastActiveAgo = 420845)
            }
        val response = client.get("/_matrix/client/v3/presence/%40user%3Aserver/status") { bearerAuth("token") }
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
        verify(handlerMock).suspendFunction(handlerMock::getPresence)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSendToDevice() = testApplication {
        initCut()
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
        verify(handlerMock).suspendFunction(handlerMock::sendToDevice)
            .with(matching {
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
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetFilter() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::setFilter)
            .whenInvokedWith(any())
            .then {
                SetFilter.Response("0")
            }
        val response =
            client.post("/_matrix/client/v3/user/%40user%3Aserver/filter") {
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
        verify(handlerMock).suspendFunction(handlerMock::setFilter)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.requestBody shouldBe Filters(
                    room = Filters.RoomFilter(
                        state = Filters.RoomFilter.StateFilter(
                            lazyLoadMembers = true
                        )
                    )
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetFilter() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getFilter)
            .whenInvokedWith(any())
            .then {
                Filters(
                    room = Filters.RoomFilter(
                        state = Filters.RoomFilter.StateFilter(
                            lazyLoadMembers = true
                        )
                    )
                )
            }
        val response = client.get("/_matrix/client/v3/user/%40user%3Aserver/filter/0") { bearerAuth("token") }
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
        verify(handlerMock).suspendFunction(handlerMock::getFilter)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                it.endpoint.filterId shouldBe "0"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetAccountData() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getAccountData)
            .whenInvokedWith(any())
            .then {
                DirectEventContent(
                    mapOf(
                        UserId("bob", "server") to setOf(RoomId("someRoom", "server"))
                    )
                )
            }
        val response =
            client.get("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/account_data/m.direct") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"@bob:server":["!someRoom:server"]}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::getAccountData)
            .with(matching {
                it.endpoint.type shouldBe "m.direct"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetAccountDataWithKey() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getAccountData)
            .whenInvokedWith(any())
            .then {
                SecretKeyEventContent.AesHmacSha2Key("name")
            }
        val response =
            client.get("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/account_data/m.secret_storage.key.key1") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"name":"name","algorithm":"m.secret_storage.v1.aes-hmac-sha2"}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::getAccountData)
            .with(matching {
                it.endpoint.type shouldBe "m.secret_storage.key.key1"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetAccountData() = testApplication {
        initCut()
        val response =
            client.put("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/account_data/m.direct") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"@bob:server":["!someRoom:server"]}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::setAccountData)
            .with(matching {
                it.endpoint.type shouldBe "m.direct"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe DirectEventContent(
                    mapOf(
                        UserId("bob", "server") to setOf(RoomId("someRoom", "server"))
                    )
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetAccountDataWithKey() = testApplication {
        initCut()
        val response =
            client.put("/_matrix/client/v3/user/%40alice%3Aexample%2Ecom/account_data/m.secret_storage.key.key1") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"name","algorithm":"m.secret_storage.v1.aes-hmac-sha2"}""")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::setAccountData)
            .with(matching {
                it.endpoint.type shouldBe "m.secret_storage.key.key1"
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.requestBody shouldBe SecretKeyEventContent.AesHmacSha2Key("name")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSearchUsers() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::searchUsers)
            .whenInvokedWith(any())
            .then {
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
        verify(handlerMock).suspendFunction(handlerMock::searchUsers)
            .with(matching {
                it.requestBody shouldBe SearchUsers.Request("bob", 20)
                it.call.request.headers[HttpHeaders.AcceptLanguage] shouldBe "de"
                true
            })
            .wasInvoked()
    }
}

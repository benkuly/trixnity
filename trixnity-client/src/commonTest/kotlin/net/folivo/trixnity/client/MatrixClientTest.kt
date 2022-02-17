package net.folivo.trixnity.client

import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import net.folivo.trixnity.client.MatrixClient.LoginState.*
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.InMemoryStoreFactory
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.e
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.sync.SyncResponse
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class MatrixClientTest : ShouldSpec({
    timeout = 30_000

    val json = createMatrixJson()

    val serverResponse = SyncResponse(
        nextBatch = "nextBatch",
        accountData = SyncResponse.GlobalAccountData(listOf(Event.GlobalAccountDataEvent(DirectEventContent(mappings = emptyMap())))),
        deviceLists = SyncResponse.DeviceLists(emptySet(), emptySet()),
        deviceOneTimeKeysCount = emptyMap(),
        presence = SyncResponse.Presence(emptyList()),
        room = SyncResponse.Rooms(emptyMap(), emptyMap(), emptyMap()),
        toDevice = SyncResponse.ToDevice(emptyList())
    )
    val userId = UserId("user", "localhost")

    lateinit var scope: CoroutineScope
    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
    }
    afterTest {
        scope.cancel()
        clearAllMocks()
    }

    context(MatrixClient::startSync.name) {
        should("write the last successfully processed batch token in the DB") {
            val inMemoryStore = InMemoryStore(scope)
            val cut = spyk(MatrixClient.loginWith(
                baseUrl = Url("http://matrix.home"),
                storeFactory = InMemoryStoreFactory(inMemoryStore),
                baseHttpClient = HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            when (request.url.fullPath) {
                                "/_matrix/client/v3/keys/upload" -> {
                                    assertEquals(HttpMethod.Post, request.method)
                                    respond(
                                        """{"one_time_key_counts":{"ed25519":1}}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }
                                "/_matrix/client/v3/user/${userId.e()}/filter" -> {
                                    assertEquals(HttpMethod.Post, request.method)
                                    respond(
                                        """{"filter_id":"someFilter"}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online" -> {
                                    assertEquals(HttpMethod.Get, request.method)
                                    respond(
                                        json.encodeToString(serverResponse),
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }
                                else -> {
                                    respond(
                                        "",
                                        HttpStatusCode.BadRequest
                                    )
                                }
                            }
                        }
                    }
                },
                scope = scope,
                getLoginInfo = {
                    Result.success(
                        MatrixClient.Companion.LoginInfo(
                            userId,
                            "deviceId",
                            "accessToken",
                            "displayName",
                            "mxc://localhost/123456"
                        )
                    )
                }
            ).getOrThrow())

            val userServiceMock: UserService = spyk(cut.user)
            every { cut.user } returns userServiceMock
            coEvery { userServiceMock.setGlobalAccountData(any()) } throws RuntimeException("Oh no!")

            cut.startSync().getOrThrow()
            until(1_000.milliseconds, 50.milliseconds.fixed()) {
                inMemoryStore.account.syncBatchToken.value == null
            }
        }
    }

    context(MatrixClient::displayName.name) {
        should("get the display name and avatar URL from the profile API when initially logging in") {
            val inMemoryStore = InMemoryStore(scope)
            MatrixClient.login(
                baseUrl = Url("http://matrix.home"),
                identifier = IdentifierType.User(userId.full),
                passwordOrToken = "p4ssw0rd!",
                storeFactory = InMemoryStoreFactory(inMemoryStore),
                baseHttpClient = HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            when (request.url.fullPath) {
                                "/_matrix/client/v3/login" -> {
                                    respond(
                                        """{"user_id":"${userId.full}","access_token":"abcdef","device_id":"deviceId"}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString(),
                                        )
                                    )
                                }
                                "/_matrix/client/v3/profile/${userId.e()}" -> {
                                    respond(
                                        """{"displayname":"bob","avatar_url":"mxc://localhost/123456"}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString(),
                                        )
                                    )
                                }
                                "/_matrix/client/v3/keys/upload" -> {
                                    assertEquals(HttpMethod.Post, request.method)
                                    respond(
                                        """{"one_time_key_counts":{"ed25519":1}}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }
                                "/_matrix/client/v3/user/${userId.e()}/filter" -> {
                                    assertEquals(HttpMethod.Post, request.method)
                                    respond(
                                        """{"filter_id":"someFilter"}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }
                                else -> {
                                    respond(
                                        "",
                                        HttpStatusCode.BadRequest
                                    )
                                }
                            }
                        }
                    }
                },
                scope = scope,
            ).fold(
                onSuccess = { matrixClient ->
                    matrixClient.displayName.value shouldBe "bob"
                    matrixClient.avatarUrl.value shouldBe "mxc://localhost/123456"
                },
                onFailure = {
                    fail(it.message)
                }
            )
        }
        should("use the display name and avatar URL from the store when matrixClient is retrieved from the store and update when room user updates") {
            val inMemoryStore = InMemoryStore(scope).apply { init() }
            delay(50) // wait for init
            inMemoryStore.account.olmPickleKey.value = ""
            inMemoryStore.account.accessToken.value = "abcdef"
            inMemoryStore.account.userId.value = userId
            inMemoryStore.account.deviceId.value = "deviceId"
            inMemoryStore.account.baseUrl.value = Url("http://localhost")
            inMemoryStore.account.filterId.value = "someFilter"
            inMemoryStore.account.displayName.value = "bob"
            inMemoryStore.account.avatarUrl.value = "mxc://localhost/123456"

            val cut = MatrixClient.fromStore(
                storeFactory = InMemoryStoreFactory(inMemoryStore),
                baseHttpClient = HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            when (request.url.fullPath) {
                                "/_matrix/client/v3/sync?filter=someFilter&set_presence=online" -> {
                                    assertEquals(HttpMethod.Get, request.method)
                                    val roomId = RoomId("room1", "localhost")
                                    respond(
                                        json.encodeToString(
                                            serverResponse.copy(
                                                room = SyncResponse.Rooms(
                                                    join = mapOf(
                                                        roomId to SyncResponse.Rooms.JoinedRoom(
                                                            timeline = SyncResponse.Rooms.Timeline(
                                                                events = listOf(
                                                                    Event.StateEvent(
                                                                        MemberEventContent(membership = JOIN),
                                                                        sender = userId,
                                                                        id = EventId("event1"),
                                                                        roomId = roomId,
                                                                        originTimestamp = 0L,
                                                                        stateKey = userId.full,
                                                                    )
                                                                ),
                                                                previousBatch = "prevBatch"
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        ),
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }
                                "/_matrix/client/v3/sync?filter=someFilter" -> {
                                    assertEquals(HttpMethod.Get, request.method)
                                    val roomId = RoomId("room1", "localhost")
                                    respond(
                                        json.encodeToString(
                                            serverResponse.copy(
                                                room = SyncResponse.Rooms(
                                                    join = mapOf(
                                                        roomId to SyncResponse.Rooms.JoinedRoom(
                                                            timeline = SyncResponse.Rooms.Timeline(
                                                                events = listOf(
                                                                    Event.StateEvent(
                                                                        MemberEventContent(
                                                                            membership = JOIN,
                                                                            displayName = "bob", // display name in the room != global display name
                                                                            avatarUrl = "mxc://localhost/123456"
                                                                        ),
                                                                        sender = userId,
                                                                        id = EventId("event2"),
                                                                        roomId = roomId,
                                                                        originTimestamp = 1L,
                                                                        stateKey = userId.full,
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        ),
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }
                                "/_matrix/client/v3/profile/${userId.e()}" -> {
                                    respond(
                                        """{"displayname":"bobby","avatar_url":"mxc://localhost/abcdef"}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString(),
                                        )
                                    )
                                }
                                else -> {
                                    respond(
                                        json.encodeToString(
                                            serverResponse.copy(
                                            )
                                        ),
                                        HttpStatusCode.BadRequest
                                    )
                                }
                            }
                        }
                    }
                },
                scope = scope,
            ).getOrThrow()
            assertNotNull(cut)

            cut.displayName.first { it != null } shouldBe "bob"
            cut.avatarUrl.first { it != null } shouldBe "mxc://localhost/123456"

            cut.startSync().getOrThrow()

            cut.displayName.first { it == "bobby" } shouldBe "bobby"
            cut.avatarUrl.first { it == "mxc://localhost/abcdef" } shouldBe "mxc://localhost/abcdef"
        }
    }
    context(MatrixClient::loginState.name) {
        val inMemoryStore = InMemoryStore(scope).apply { init() }
        delay(50) // wait for init
        inMemoryStore.account.olmPickleKey.value = ""
        inMemoryStore.account.accessToken.value = "abcdef"
        inMemoryStore.account.userId.value = userId
        inMemoryStore.account.deviceId.value = "deviceId"
        inMemoryStore.account.baseUrl.value = Url("http://localhost")
        inMemoryStore.account.filterId.value = "someFilter"
        inMemoryStore.account.displayName.value = "bob"
        inMemoryStore.account.avatarUrl.value = "mxc://localhost/123456"
        val cut = MatrixClient.fromStore(
            storeFactory = InMemoryStoreFactory(inMemoryStore),
            baseHttpClient = HttpClient(MockEngine) {
                engine { addHandler { respond("", HttpStatusCode.BadRequest) } }
            },
            scope = scope
        ).getOrThrow()!!
        should("$LOGGED_IN when access token is not null") {
            inMemoryStore.account.accessToken.value = "access"
            inMemoryStore.account.syncBatchToken.value = "sync"
            cut.loginState.first { it == LOGGED_IN }
        }
        should("$LOGGED_OUT_SOFT when access token is null, but sync batch token not") {
            inMemoryStore.account.accessToken.value = null
            inMemoryStore.account.syncBatchToken.value = "sync"
            cut.loginState.first { it == LOGGED_OUT_SOFT }
        }
        should("$LOGGED_OUT when access token and sync batch token are null") {
            inMemoryStore.account.accessToken.value = null
            inMemoryStore.account.syncBatchToken.value = null
            cut.loginState.first { it == LOGGED_OUT }
        }
    }
    context(MatrixClient::logout.name) {
        lateinit var apiMock: MatrixClientServerApiClient
        lateinit var inMemoryStore: InMemoryStore
        lateinit var cut: MatrixClient
        beforeTest {
            inMemoryStore = InMemoryStore(scope).apply { init() }
            delay(50) // wait for init
            inMemoryStore.account.olmPickleKey.value = ""
            inMemoryStore.account.accessToken.value = "abcdef"
            inMemoryStore.account.userId.value = userId
            inMemoryStore.account.deviceId.value = "deviceId"
            inMemoryStore.account.baseUrl.value = Url("http://localhost")
            inMemoryStore.account.filterId.value = "someFilter"
            inMemoryStore.account.displayName.value = "bob"
            inMemoryStore.account.avatarUrl.value = "mxc://localhost/123456"
            apiMock = mockk<MatrixClientServerApiClient> {
                coEvery { authentication.logout() } returns Result.success(Unit)
                coEvery { sync.stop(any()) } just Runs
            }
            cut = spyk(
                MatrixClient.fromStore(
                    storeFactory = InMemoryStoreFactory(inMemoryStore),
                    baseHttpClient = HttpClient(MockEngine) {
                        engine { addHandler { respond("{}", HttpStatusCode.OK) } }
                    },
                    scope = scope
                ).getOrThrow()!!
            ) {
                coEvery { api } returns apiMock
            }
        }
        should("delete All when $LOGGED_OUT_SOFT") {
            inMemoryStore.account.accessToken.value = null
            inMemoryStore.account.syncBatchToken.value = "sync"
            cut.loginState.first { it == LOGGED_OUT_SOFT }
            cut.logout().getOrThrow()
            coVerify {
                apiMock.sync.stop(true)
            }
            inMemoryStore.account.userId.value shouldBe null
        }
        should("call api and delete all") {
            cut.logout().getOrThrow()
            coVerify {
                apiMock.authentication.logout()
                apiMock.sync.stop(true)
            }
            inMemoryStore.account.userId.value shouldBe null
        }
    }
})
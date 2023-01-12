package net.folivo.trixnity.client

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.MatrixClient.LoginState.*
import net.folivo.trixnity.client.media.InMemoryMediaStore
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.Logout
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.testutils.mockEngineFactory
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.fail

class MatrixClientTest : ShouldSpec({
    timeout = 30_000

    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()

    val serverResponse = Sync.Response(
        nextBatch = "nextBatch",
        accountData = Sync.Response.GlobalAccountData(listOf(Event.GlobalAccountDataEvent(DirectEventContent(mappings = emptyMap())))),
        deviceLists = Sync.Response.DeviceLists(emptySet(), emptySet()),
        oneTimeKeysCount = emptyMap(),
        presence = Sync.Response.Presence(emptyList()),
        room = Sync.Response.Rooms(emptyMap(), emptyMap(), emptyMap()),
        toDevice = Sync.Response.ToDevice(emptyList())
    )
    val userId = UserId("user", "localhost")

    lateinit var scope: CoroutineScope
    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
    }
    afterTest {
        scope.cancel()
    }

    context(MatrixClientImpl::displayName.name) {
        should("get the display name and avatar URL from the profile API when initially logging in") {
            val olmAccountRepository = InMemoryOlmAccountRepository().apply {
                save(1, freeAfter(OlmAccount.create()) { it.pickle("") })
            }
            val repositoriesModule = createInMemoryRepositoriesModule().apply {
                includes(
                    module {
                        single<OlmAccountRepository> { olmAccountRepository }
                    })
            }
            MatrixClient.login(
                baseUrl = Url("http://matrix.home"),
                identifier = IdentifierType.User(userId.full),
                passwordOrToken = "p4ssw0rd!",
                repositoriesModule = repositoriesModule,
                mediaStore = InMemoryMediaStore(),
                configuration = {
                    httpClientFactory = {
                        HttpClient(MockEngine) {
                            it()
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

                                        "/_matrix/client/v3/profile/${userId.e().full}" -> {
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

                                        "/_matrix/client/v3/user/${userId.e().full}/filter" -> {
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
            val accountRepository = InMemoryAccountRepository().apply {
                save(
                    1, Account(
                        olmPickleKey = "",
                        accessToken = "abcdef",
                        userId = userId,
                        deviceId = "deviceId",
                        baseUrl = "http://localhost",
                        filterId = "someFilter",
                        backgroundFilterId = "backgroundFilter",
                        displayName = "bob",
                        avatarUrl = "mxc://localhost/123456",
                        syncBatchToken = null,
                    )
                )
            }
            val olmAccountRepository = InMemoryOlmAccountRepository().apply {
                save(1, freeAfter(OlmAccount.create()) { it.pickle("") })
            }
            val repositoriesModule = createInMemoryRepositoriesModule().apply {
                includes(
                    module {
                        single<AccountRepository> { accountRepository }
                        single<OlmAccountRepository> { olmAccountRepository }
                    })
            }
            val cut = MatrixClient.fromStore(
                repositoriesModule = repositoriesModule,
                mediaStore = InMemoryMediaStore(),
                configuration = {
                    httpClientFactory = {
                        HttpClient(MockEngine) {
                            it()
                            engine {
                                addHandler { request ->
                                    val path = request.url.fullPath
                                    when {
                                        path.startsWith("/_matrix/client/v3/sync?filter=someFilter&set_presence=online") -> {
                                            assertEquals(HttpMethod.Get, request.method)
                                            val roomId = RoomId("room1", "localhost")
                                            respond(
                                                json.encodeToString(
                                                    serverResponse.copy(
                                                        room = Sync.Response.Rooms(
                                                            join = mapOf(
                                                                roomId to Sync.Response.Rooms.JoinedRoom(
                                                                    timeline = Sync.Response.Rooms.Timeline(
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

                                        path.startsWith("/_matrix/client/v3/sync?filter=someFilter") -> {
                                            assertEquals(HttpMethod.Get, request.method)
                                            val roomId = RoomId("room1", "localhost")
                                            respond(
                                                json.encodeToString(
                                                    serverResponse.copy(
                                                        room = Sync.Response.Rooms(
                                                            join = mapOf(
                                                                roomId to Sync.Response.Rooms.JoinedRoom(
                                                                    timeline = Sync.Response.Rooms.Timeline(
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

                                        path == "/_matrix/client/v3/profile/${userId.e().full}" -> {
                                            respond(
                                                """{"displayname":"bobby","avatar_url":"mxc://localhost/abcdef"}""",
                                                HttpStatusCode.OK,
                                                headersOf(
                                                    HttpHeaders.ContentType,
                                                    ContentType.Application.Json.toString(),
                                                )
                                            )
                                        }

                                        path == "/_matrix/client/v3/keys/upload" -> {
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

                                        else -> {
                                            throw IllegalStateException(path)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                scope = scope,
            ).getOrThrow().shouldNotBeNull()

            cut.displayName.first { it != null } shouldBe "bob"
            cut.avatarUrl.first { it != null } shouldBe "mxc://localhost/123456"

            cut.startSync()

            cut.displayName.first { it == "bobby" } shouldBe "bobby"
            cut.avatarUrl.first { it == "mxc://localhost/abcdef" } shouldBe "mxc://localhost/abcdef"
        }
    }
    context(MatrixClientImpl::loginState.name) {
        lateinit var cut: MatrixClient
        beforeTest {
            val account = Account(
                olmPickleKey = "",
                accessToken = "abcdef",
                userId = userId,
                deviceId = "deviceId",
                baseUrl = "http://localhost",
                filterId = "someFilter",
                backgroundFilterId = "backgroundFilter",
                displayName = "bob",
                avatarUrl = "mxc://localhost/123456",
                syncBatchToken = "sync",
            )
            val accountRepository = InMemoryAccountRepository().apply {
                save(1, account)
            }
            val olmAccountRepository = InMemoryOlmAccountRepository().apply {
                save(1, freeAfter(OlmAccount.create()) { it.pickle("") })
            }
            val repositoriesModule = createInMemoryRepositoriesModule().apply {
                includes(
                    module {
                        single<AccountRepository> { accountRepository }
                        single<OlmAccountRepository> { olmAccountRepository }
                    })
            }
            cut = MatrixClient.fromStore(
                repositoriesModule = repositoriesModule,
                mediaStore = InMemoryMediaStore(),
                configuration = {
                    httpClientFactory = {
                        HttpClient(MockEngine) {
                            it()
                            engine { addHandler { respond("", HttpStatusCode.BadRequest) } }
                        }
                    }
                },
                scope = scope
            ).getOrThrow().shouldNotBeNull()
        }
        should("$LOGGED_IN when access token is not null") {
            cut.loginState.first { it == LOGGED_IN }
        }
        should("$LOGGED_OUT_SOFT when access token is null, but sync batch token not") {
            val accountStore = cut.di.get<AccountStore>()
            accountStore.accessToken.value = null
            cut.loginState.first { it == LOGGED_OUT_SOFT }
        }
        should("$LOGGED_OUT when access token and sync batch token are null") {
            val accountStore = cut.di.get<AccountStore>()
            accountStore.accessToken.value = null
            accountStore.syncBatchToken.value = null
            cut.loginState.first { it == LOGGED_OUT }
        }
    }
    context(MatrixClientImpl::logout.name) {
        lateinit var repositoriesModule: Module
        beforeTest {
            val accountRepository = InMemoryAccountRepository().apply {
                save(
                    1, Account(
                        olmPickleKey = "",
                        accessToken = "abcdef",
                        userId = userId,
                        deviceId = "deviceId",
                        baseUrl = "http://localhost",
                        filterId = "someFilter",
                        backgroundFilterId = "backgroundFilter",
                        displayName = "bob",
                        avatarUrl = "mxc://localhost/123456",
                        syncBatchToken = null,
                    )
                )
            }
            val olmAccountRepository = InMemoryOlmAccountRepository().apply {
                save(1, freeAfter(OlmAccount.create()) { it.pickle("") })
            }
            repositoriesModule = createInMemoryRepositoriesModule().apply {
                includes(
                    module {
                        single<AccountRepository> { accountRepository }
                        single<OlmAccountRepository> { olmAccountRepository }
                    })
            }
        }
        should("delete All when $LOGGED_OUT_SOFT") {
            var logoutCalled = false
            val cut = MatrixClient.fromStore(
                repositoriesModule = repositoriesModule,
                mediaStore = InMemoryMediaStore(),
                configuration = {
                    httpClientFactory = mockEngineFactory {
                        matrixJsonEndpoint(json, mappings, Logout()) {
                            logoutCalled = true
                        }
                    }
                },
                scope = scope
            ).getOrThrow().shouldNotBeNull()
            val accountStore = cut.di.get<AccountStore>()
            accountStore.accessToken.value = null
            accountStore.syncBatchToken.value = "sync"
            cut.loginState.first { it == LOGGED_OUT_SOFT }
            cut.logout().getOrThrow()

            logoutCalled shouldBe false
            cut.userId
            accountStore.userId.value shouldBe null
        }
        should("call api and delete all") {
            var logoutCalled = false
            val cut = MatrixClient.fromStore(
                repositoriesModule = repositoriesModule,
                mediaStore = InMemoryMediaStore(),
                configuration = {
                    httpClientFactory = mockEngineFactory {
                        matrixJsonEndpoint(json, mappings, Logout()) {
                            logoutCalled = true
                        }
                    }
                },
                scope = scope
            ).getOrThrow().shouldNotBeNull()

            cut.loginState.first { it == LOGGED_IN }
            cut.logout().getOrThrow()

            logoutCalled shouldBe true
            val accountStore = cut.di.get<AccountStore>()
            accountStore.userId.value shouldBe null
        }
    }
})
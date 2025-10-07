package net.folivo.trixnity.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import net.folivo.trixnity.client.MatrixClient.LoginState.*
import net.folivo.trixnity.client.media.createInMemoryMediaStoreModule
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.Logout
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.RoomMap.Companion.roomMapOf
import net.folivo.trixnity.clientserverapi.model.sync.SyncResponseSerializer
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.libolm.LibOlmCryptoDriver
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.testutils.scopedMockEngine
import net.folivo.trixnity.testutils.scopedMockEngineWithEndpoints
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import net.folivo.trixnity.crypto.driver.olm.Account as OlmAccount

class MatrixClientTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = LibOlmCryptoDriver

    private val json = createMatrixEventJson()
    private val mappings = createDefaultEventContentSerializerMappings()
    private val syncResponseSerializer = SyncResponseSerializer(json, mappings)

    private val serverResponse = Sync.Response(
        nextBatch = "nextBatch",
        accountData = Sync.Response.GlobalAccountData(
            listOf(
                GlobalAccountDataEvent(DirectEventContent(mappings = emptyMap()))
            )
        ),
        deviceLists = Sync.Response.DeviceLists(emptySet(), emptySet()),
        oneTimeKeysCount = emptyMap(),
        presence = Sync.Response.Presence(emptyList()),
        room = Sync.Response.Rooms(roomMapOf(), roomMapOf(), roomMapOf()),
        toDevice = Sync.Response.ToDevice(emptyList())
    )
    private val userId = UserId("user", "localhost")

    private val accountPickle = driver.olm.account().use(OlmAccount::pickle)

    @Test
    fun `MatrixClientImpl displayName » get the display name and avatar URL from the profile API when initially logging in`() =
        runTest {
            val olmAccountRepository = InMemoryOlmAccountRepository().apply {
                save(1, accountPickle)
            }
            val repositoriesModule = createInMemoryRepositoriesModule().apply {
                includes(
                    module {
                        single<OlmAccountRepository> { olmAccountRepository }
                    })
            }
            val cut = MatrixClient.login(
                baseUrl = Url("http://matrix.home"),
                identifier = IdentifierType.User(userId.full),
                password = "p4ssw0rd!",
                repositoriesModule = repositoriesModule,
                mediaStoreModule = createInMemoryMediaStoreModule(),
                coroutineContext = backgroundScope.coroutineContext,
                configuration = {
                    httpClientEngine = scopedMockEngine(false) {
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

                                "/_matrix/client/v3/profile/${userId.full}" -> {
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

                                "/_matrix/client/v3/user/${userId.full}/filter" -> {
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

                                "/_matrix/client/versions" -> {
                                    respond(
                                        """{}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }

                                "/_matrix/client/v3/capabilities" -> {
                                    respond(
                                        """{"capabilities":{}}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }

                                "/_matrix/media/v3/config" -> {
                                    respond(
                                        """{"m.upload.size":24}""",
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
            ).onSuccess { matrixClient ->
                matrixClient.displayName.value shouldBe "bob"
                matrixClient.avatarUrl.value shouldBe "mxc://localhost/123456"
            }
                .onFailure {
                    fail(it.message, it)
                }

            cut.getOrNull()?.close()
        }

    @Test
    fun `MatrixClientImpl displayName » use the display name and avatar URL from the store when matrixClient is retrieved from the store and update when room user updates`() =
        runTest {
            val accountRepository = InMemoryAccountRepository().apply {
                save(
                    1, Account(
                        olmPickleKey = null,
                        accessToken = "abcdef",
                        refreshToken = "ghijk",
                        userId = userId,
                        deviceId = "deviceId",
                        baseUrl = "http://localhost",
                        filterId = "someFilter",
                        backgroundFilterId = "someFilter",
                        displayName = "bob",
                        avatarUrl = "mxc://localhost/123456",
                        syncBatchToken = null,
                    )
                )
            }
            val olmAccountRepository = InMemoryOlmAccountRepository().apply {
                save(1, accountPickle)
            }
            val repositoriesModule = createInMemoryRepositoriesModule().apply {
                includes(
                    module {
                        single<AccountRepository> { accountRepository }
                        single<OlmAccountRepository> { olmAccountRepository }
                    })
            }
            val log = KotlinLogging.logger("MatrixClientImplTest")
            val cut = MatrixClient.fromStore(
                repositoriesModule = repositoriesModule,
                mediaStoreModule = createInMemoryMediaStoreModule(),
                coroutineContext = backgroundScope.coroutineContext,
                configuration = {
                    httpClientEngine = backgroundScope.scopedMockEngine(false) {
                        addHandler { request ->
                            val path = request.url.fullPath
                            log.debug { "path: $path" }
                            when {
                                path.startsWith("/_matrix/client/v3/sync?filter=someFilter") -> {
                                    assertEquals(HttpMethod.Get, request.method)
                                    val roomId = RoomId("!room1:localhost")
                                    respond(
                                        json.encodeToString(
                                            syncResponseSerializer,
                                            serverResponse.copy(
                                                room = Sync.Response.Rooms(
                                                    join = roomMapOf(
                                                        roomId to Sync.Response.Rooms.JoinedRoom(
                                                            timeline = Sync.Response.Rooms.Timeline(
                                                                events = listOf(
                                                                    StateEvent(
                                                                        CreateEventContent(),
                                                                        sender = userId,
                                                                        id = EventId("event1"),
                                                                        roomId = roomId,
                                                                        originTimestamp = 1L,
                                                                        stateKey = "",
                                                                    ),
                                                                    StateEvent(
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

                                path == "/_matrix/client/v3/profile/${userId.full}" -> {
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

                                path == "/_matrix/client/versions" -> {
                                    respond(
                                        """{}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }

                                path == "/_matrix/client/v3/capabilities" -> {
                                    respond(
                                        """{"capabilities":{}}""",
                                        HttpStatusCode.OK,
                                        headersOf(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Json.toString()
                                        )
                                    )
                                }

                                path == "/_matrix/media/v3/config" -> {
                                    respond(
                                        """{"m.upload.size":24}""",
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
                },
            ).getOrThrow().shouldNotBeNull()

            cut.displayName.first { it != null } shouldBe "bob"
            cut.avatarUrl.first { it != null } shouldBe "mxc://localhost/123456"

            cut.syncOnce().getOrThrow()

            cut.displayName.first { it == "bobby" } shouldBe "bobby"
            cut.avatarUrl.first { it == "mxc://localhost/abcdef" } shouldBe "mxc://localhost/abcdef"
            cut.close()
        }


    @Test
    fun `MatrixClientImpl loginState » be LOGGED_IN when access token is not null`() = runTest {
        loginStateSetup().use { cut -> cut.loginState.first { it == LOGGED_IN } }
    }

    @Test
    fun `MatrixClientImpl loginState » be LOGGED_OUT_SOFT when access token is null but sync batch token not`() =
        runTest {
            loginStateSetup().use { cut ->
                val accountStore = cut.di.get<AccountStore>()
                accountStore.updateAccount { it?.copy(accessToken = null) }
                cut.loginState.first { it == LOGGED_OUT_SOFT }
            }
        }

    @Test
    fun `MatrixClientImpl loginState » be LOGGED_OUT when access token and sync batch token are null`() = runTest {
        loginStateSetup().use { cut ->
            val accountStore = cut.di.get<AccountStore>()
            accountStore.updateAccount { it?.copy(accessToken = null, syncBatchToken = null) }
            cut.loginState.first { it == LOGGED_OUT }
        }
    }

    @Test
    fun `MatrixClientImpl loginState » be LOCKED when locked`() = runTest {
        loginStateSetup().use { cut ->
            val accountStore = cut.di.get<AccountStore>()
            accountStore.updateAccount { it?.copy(isLocked = true) }
            cut.loginState.first { it == LOCKED }
        }
    }

    @Test
    fun `MatrixClientImpl loginState » be LOGGED_IN when not locked anymore`() = runTest {
        loginStateSetup().use { cut ->
            val accountStore = cut.di.get<AccountStore>()
            accountStore.updateAccount { it?.copy(isLocked = true) }
            cut.loginState.first { it == LOCKED }
            delay(200.milliseconds) // give it a moment to listen to sync
            cut.syncOnce().getOrThrow()
            delay(200.milliseconds) // give it a moment to listen to sync
            cut.loginState.first { it == LOGGED_IN }
        }
    }


    @Test
    fun `MatrixClientImpl logout » delete All when LOGGED_OUT_SOFT`() = runTest {
        var logoutCalled = false
        val cut = MatrixClient.fromStore(
            repositoriesModule = repositoriesModule(),
            mediaStoreModule = createInMemoryMediaStoreModule(),
            coroutineContext = backgroundScope.coroutineContext,
            configuration = {
                httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                    matrixJsonEndpoint(Logout()) {
                        logoutCalled = true
                    }
                }
            },
        ).getOrThrow().shouldNotBeNull()

        cut.use {
            val accountStore = cut.di.get<AccountStore>()
            accountStore.updateAccount { it?.copy(accessToken = null, syncBatchToken = "sync") }
            cut.loginState.first { it == LOGGED_OUT_SOFT }
            cut.logout().getOrThrow()

            logoutCalled shouldBe false
            cut.userId
            accountStore.getAccount()?.userId shouldBe null
        }
    }

    @Test
    fun `MatrixClientImpl logout » call api and delete all`() = runTest {
        var logoutCalled = false
        val cut = MatrixClient.fromStore(
            repositoriesModule = repositoriesModule(),
            mediaStoreModule = createInMemoryMediaStoreModule(),
            coroutineContext = backgroundScope.coroutineContext,
            configuration = {
                httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                    matrixJsonEndpoint(Logout()) {
                        logoutCalled = true
                    }
                }
            },
        ).getOrThrow().shouldNotBeNull()

        cut.use {
            cut.loginState.first { it == LOGGED_IN }
            cut.logout().getOrThrow()

            logoutCalled shouldBe true
            val accountStore = cut.di.get<AccountStore>()
            accountStore.getAccount()?.userId shouldBe null
        }
    }

    private suspend fun TestScope.loginStateSetup(): MatrixClient {
        val account = Account(
            olmPickleKey = null,
            accessToken = "abcdef",
            refreshToken = "ghijk",
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
            save(1, accountPickle)
        }
        val repositoriesModule = createInMemoryRepositoriesModule().apply {
            includes(
                module {
                    single<AccountRepository> { accountRepository }
                    single<OlmAccountRepository> { olmAccountRepository }
                })
        }
        return MatrixClient.fromStore(
            repositoriesModule = repositoriesModule,
            mediaStoreModule = createInMemoryMediaStoreModule(),
            coroutineContext = backgroundScope.coroutineContext,
            configuration = {
                httpClientEngine = backgroundScope.scopedMockEngine(false) {
                    addHandler { request ->
                        val path = request.url.fullPath
                        when {
                            path.startsWith("/_matrix/client/v3/sync?filter=backgroundFilter&set_presence=offline") -> {
                                assertEquals(HttpMethod.Get, request.method)
                                respond(
                                    json.encodeToString(syncResponseSerializer, serverResponse),
                                    HttpStatusCode.OK,
                                    headersOf(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.Json.toString()
                                    )
                                )
                            }

                            path == "/_matrix/client/v3/profile/${userId.full}" -> {
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

                            path == "/_matrix/client/versions" -> {
                                respond(
                                    """{}""",
                                    HttpStatusCode.OK,
                                    headersOf(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.Json.toString()
                                    )
                                )
                            }

                            path == "/_matrix/client/v3/capabilities" -> {
                                respond(
                                    """{"capabilities":{}}""",
                                    HttpStatusCode.OK,
                                    headersOf(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.Json.toString()
                                    )
                                )
                            }

                            path == "/_matrix/media/v3/config" -> {
                                respond(
                                    """{"m.upload.size":24}""",
                                    HttpStatusCode.OK,
                                    headersOf(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.Json.toString()
                                    )
                                )
                            }

                            else -> {
                                respond(
                                    """{"errcode":"M_NOT_FOUND","error":"not found url ${request.url}"}""",
                                    HttpStatusCode.BadRequest
                                )
                            }
                        }
                    }
                }
            },
        ).getOrThrow().shouldNotBeNull()
    }

    private suspend fun repositoriesModule(): Module {
        val accountRepository = InMemoryAccountRepository().apply {
            save(
                1, Account(
                    olmPickleKey = null,
                    accessToken = "abcdef",
                    refreshToken = "ghijk",
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
            save(1, accountPickle)
        }
        return createInMemoryRepositoriesModule().apply {
            includes(
                module {
                    single<AccountRepository> { accountRepository }
                    single<OlmAccountRepository> { olmAccountRepository }
                }
            )
        }
    }
}
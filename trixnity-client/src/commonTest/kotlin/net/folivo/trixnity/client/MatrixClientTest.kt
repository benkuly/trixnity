package net.folivo.trixnity.client

import io.kotest.assertions.timing.eventually
import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.e
import net.folivo.trixnity.client.api.model.authentication.IdentifierType
import net.folivo.trixnity.client.api.model.sync.SyncResponse
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MatrixClientTest : ShouldSpec({
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
    }

    context(MatrixClient::startSync.name) {
        should("write the last successfully processed batch token in the DB") {
            val inMemoryStore = InMemoryStore(scope)
            val cut = spyk(MatrixClient.loginWith(
                baseUrl = Url("http://matrix.home"),
                storeFactory = object : StoreFactory {
                    override suspend fun createStore(
                        contentMappings: EventContentSerializerMappings,
                        json: Json,
                    ): Store {
                        return inMemoryStore
                    }
                },
                secureStore = object : SecureStore {
                    override val olmPickleKey: String = ""
                },
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
                            Url("mxc://localhost/123456")
                        )
                    )
                }
            ).getOrThrow())

            val userServiceMock: UserService = spyk(cut.user)
            every { cut.user } returns userServiceMock
            coEvery { userServiceMock.setGlobalAccountData(any()) } throws RuntimeException("Oh no!")

            cut.startSync()
            until(1_000.milliseconds, 50.milliseconds.fixed()) {
                inMemoryStore.account.syncBatchToken.value == null
            }
        }
    }

    context(MatrixClient::displayName.name) {
        should("get the display name and avatar URL from the profile API when initially logging in") {
            val inMemoryStore = InMemoryStore(scope).apply { init() }
            MatrixClient.login(
                baseUrl = Url("http://matrix.home"),
                identifier = IdentifierType.User(userId.full),
                password = "p4ssw0rd!",
                storeFactory = object : StoreFactory {
                    override suspend fun createStore(
                        contentMappings: EventContentSerializerMappings,
                        json: Json,
                    ): Store {
                        return inMemoryStore
                    }
                },
                secureStore = object : SecureStore {
                    override val olmPickleKey: String = ""
                },
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
                    matrixClient.avatarUrl.value shouldBe Url("mxc://localhost/123456")
                },
                onFailure = {
                    fail(it.message)
                }
            )
        }
        should("use the display name and avatar URL from the store when matrixClient is retrieved from the store and update when room user updates") {
            val inMemoryStore = InMemoryStore(scope).apply { init() }
            inMemoryStore.account.accessToken.value = "abcdef"
            inMemoryStore.account.userId.value = userId
            inMemoryStore.account.deviceId.value = "deviceId"
            inMemoryStore.account.baseUrl.value = Url("http://localhost")
            inMemoryStore.account.filterId.value = "someFilter"
            inMemoryStore.account.displayName.value = "bob"
            inMemoryStore.account.avatarUrl.value = Url("mxc://localhost/123456")

            val cut = MatrixClient.fromStore(
                storeFactory = object : StoreFactory {
                    override suspend fun createStore(
                        contentMappings: EventContentSerializerMappings,
                        json: Json,
                    ): Store {
                        return inMemoryStore
                    }
                },
                secureStore = object : SecureStore {
                    override val olmPickleKey: String = ""
                },
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
                                                                        MemberEventContent(
                                                                            membership = MemberEventContent.Membership.JOIN
                                                                        ),
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
                                                                            membership = MemberEventContent.Membership.JOIN,
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
            )

            cut?.displayName?.value shouldBe "bob"
            cut?.avatarUrl?.value shouldBe Url("mxc://localhost/123456")

            cut?.startSync()

            eventually(3.seconds) {
                cut?.displayName?.value shouldBe "bobby"
                cut?.avatarUrl?.value shouldBe Url("mxc://localhost/abcdef")
            }
        }
    }
})
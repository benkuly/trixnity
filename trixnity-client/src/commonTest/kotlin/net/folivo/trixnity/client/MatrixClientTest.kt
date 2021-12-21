package net.folivo.trixnity.client

import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
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
import net.folivo.trixnity.client.api.sync.SyncResponse
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
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
                        storeCoroutineContext: CoroutineContext,
                        loggerFactory: LoggerFactory
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
                            "accessToken"
                        )
                    )
                }
            ).getOrThrow())

            val userServiceMock: UserService = spyk(cut.user)
            every { cut.user } returns userServiceMock
            coEvery { userServiceMock.setGlobalAccountData(any()) } throws RuntimeException("Oh no!")

            cut.startSync()
            until(Duration.milliseconds(1_000), Duration.milliseconds(50).fixed()) {
                inMemoryStore.account.syncBatchToken.value == null
            }
        }
    }
})
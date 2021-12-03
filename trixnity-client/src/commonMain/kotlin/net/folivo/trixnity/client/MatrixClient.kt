package net.folivo.trixnity.client

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.authentication.IdentifierType
import net.folivo.trixnity.client.api.authentication.LoginType
import net.folivo.trixnity.client.api.createMatrixApiClientEventContentSerializerMappings
import net.folivo.trixnity.client.api.createMatrixApiClientJson
import net.folivo.trixnity.client.api.users.Filters
import net.folivo.trixnity.client.api.users.RoomFilter
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


// TODO test
class MatrixClient private constructor(
    val userId: UserId,
    val deviceId: String,
    val api: MatrixApiClient,
    private val store: Store,
    json: Json,
    secureStore: SecureStore,
    setOwnMessagesAsFullyRead: Boolean = false,
    customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
    private val scope: CoroutineScope,
    loggerFactory: LoggerFactory
) {
    private val log = newLogger(loggerFactory)

    val olm: OlmService
    val room: RoomService
    val user: UserService
    val media: MediaService
    val verification: VerificationService

    init {
        olm = OlmService(
            store = store,
            secureStore = secureStore,
            api = api,
            json = json,
            loggerFactory = loggerFactory
        )
        media = MediaService(
            api = api,
            store = store,
            loggerFactory = loggerFactory
        )
        user = UserService(
            api = api,
            store = store,
            loggerFactory = loggerFactory
        )
        room = RoomService(
            store = store,
            api = api,
            olm = olm,
            user = user,
            media = media,
            setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
            customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
            loggerFactory = loggerFactory
        )
        verification = VerificationService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            api = api,
            store = store,
            olm = olm,
            room = room,
            user = user,
            loggerFactory = loggerFactory
        )
    }

    companion object {
        suspend fun login(
            baseUrl: Url,
            identifier: IdentifierType,
            password: String,
            initialDeviceDisplayName: String? = null,
            storeFactory: StoreFactory,
            secureStore: SecureStore,
            baseHttpClient: HttpClient = HttpClient(),
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
            loggerFactory: LoggerFactory = LoggerFactory.default
        ): MatrixClient {
            val eventContentSerializerMappings = createMatrixApiClientEventContentSerializerMappings(customMappings)
            val json = createMatrixApiClientJson(eventContentSerializerMappings, loggerFactory)

            val store =
                storeFactory.createStore(eventContentSerializerMappings, json, loggerFactory = loggerFactory)
            store.init()

            val api = MatrixApiClient(
                baseUrl = baseUrl,
                baseHttpClient = baseHttpClient,
                json = json,
                eventContentSerializerMappings = eventContentSerializerMappings,
                loggerFactory = loggerFactory
            )

            val (userId, newAccessToken, deviceId) = api.authentication.login(
                identifier = identifier,
                passwordOrToken = password,
                type = LoginType.Password,
                initialDeviceDisplayName = initialDeviceDisplayName
            )
            api.accessToken.value = newAccessToken
            store.account.baseUrl.value = baseUrl
            store.account.accessToken.value = newAccessToken
            store.account.userId.value = userId
            store.account.deviceId.value = deviceId

            val matrixClient = MatrixClient(
                userId = userId,
                deviceId = deviceId,
                api = api,
                store = store,
                json = json,
                secureStore = secureStore,
                setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
                customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
                scope = scope,
                loggerFactory = loggerFactory
            )

            store.deviceKeys.update(userId) { mapOf(deviceId to matrixClient.olm.myDeviceKeys.signed) }
            api.keys.uploadKeys(deviceKeys = matrixClient.olm.myDeviceKeys)

            return matrixClient
        }

        data class LoginInfo(
            val userId: UserId,
            val deviceId: String,
            val accessToken: String,
        )

        suspend fun loginWith(
            baseUrl: Url,
            storeFactory: StoreFactory,
            secureStore: SecureStore,
            baseHttpClient: HttpClient = HttpClient(),
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
            loggerFactory: LoggerFactory = LoggerFactory.default,
            getLoginInfo: suspend (MatrixApiClient) -> LoginInfo
        ): MatrixClient {
            val eventContentSerializerMappings = createMatrixApiClientEventContentSerializerMappings(customMappings)
            val json = createMatrixApiClientJson(eventContentSerializerMappings, loggerFactory)

            val store =
                storeFactory.createStore(eventContentSerializerMappings, json, loggerFactory = loggerFactory)
            store.init()

            val api = MatrixApiClient(
                baseUrl = baseUrl,
                baseHttpClient = baseHttpClient,
                json = json,
                eventContentSerializerMappings = eventContentSerializerMappings,
                loggerFactory = loggerFactory
            )
            val loginInfo = getLoginInfo(api)

            api.accessToken.value = loginInfo.accessToken
            store.account.baseUrl.value = baseUrl
            store.account.accessToken.value = loginInfo.accessToken
            store.account.userId.value = loginInfo.userId
            store.account.deviceId.value = loginInfo.deviceId

            val matrixClient = MatrixClient(
                userId = loginInfo.userId,
                deviceId = loginInfo.deviceId,
                api = api,
                store = store,
                json = json,
                secureStore = secureStore,
                setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
                customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
                scope = scope,
                loggerFactory = loggerFactory
            )

            store.deviceKeys.update(loginInfo.userId) { mapOf(loginInfo.deviceId to matrixClient.olm.myDeviceKeys.signed) }
            api.keys.uploadKeys(deviceKeys = matrixClient.olm.myDeviceKeys)

            return matrixClient
        }

        suspend fun fromStore(
            storeFactory: StoreFactory,
            secureStore: SecureStore,
            baseHttpClient: HttpClient = HttpClient(),
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
            loggerFactory: LoggerFactory = LoggerFactory.default
        ): MatrixClient? {
            val eventContentSerializerMappings = createMatrixApiClientEventContentSerializerMappings(customMappings)
            val json = createMatrixApiClientJson(eventContentSerializerMappings, loggerFactory)

            val store =
                storeFactory.createStore(eventContentSerializerMappings, json, loggerFactory = loggerFactory)
            store.init()

            val baseUrl = store.account.baseUrl.value
            val accessToken = store.account.accessToken.value
            val userId = store.account.userId.value
            val deviceId = store.account.deviceId.value

            return if (accessToken != null && userId != null && deviceId != null && baseUrl != null) {
                val api = MatrixApiClient(
                    baseUrl = baseUrl,
                    baseHttpClient = baseHttpClient,
                    json = json,
                    eventContentSerializerMappings = eventContentSerializerMappings,
                    loggerFactory = loggerFactory
                )
                api.accessToken.value = accessToken
                MatrixClient(
                    userId = userId,
                    deviceId = deviceId,
                    api = api,
                    store = store,
                    json = json,
                    secureStore = secureStore,
                    setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
                    customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
                    scope = scope,
                    loggerFactory = loggerFactory
                )
            } else null
        }
    }

    fun isLoggedIn(scope: CoroutineScope): StateFlow<Boolean> {
        return store.account.accessToken.map { it != null }.stateIn(scope, Eagerly, false)
    }

    val syncState = api.sync.currentSyncState

    suspend fun logout() {
        api.sync.stop()
        api.authentication.logout()
        olm.free()
    }

    suspend fun stopSync() {
        api.sync.stop()
    }

    suspend fun startSync() {
        val lastSuccessfulBatchToken = MutableStateFlow(store.account.syncBatchToken.value)

        val handler = CoroutineExceptionHandler { _, exception ->
            // TODO maybe log to some sort of backend
            log.error(exception) { "There was an unexpected exception with handling sync data. Will cancel sync now. This should never happen!!!" }
            scope.launch{
                api.sync.stop(wait=true)
                store.account.syncBatchToken.value = lastSuccessfulBatchToken.value
            }
        }
        val everythingStarted = MutableStateFlow(false)
        scope.launch(handler) {
            olm.startEventHandling(this)
            room.startEventHandling(this)
            user.startEventHandling(this)
            verification.startEventHandling(this)
            everythingStarted.value = true
        }
        everythingStarted.first { it } // we wait until everything has started

        val myUserId = store.account.userId.value
        requireNotNull(myUserId)
        val filterId = store.account.filterId.value
        if (filterId == null)
            store.account.filterId.value = api.users.setFilter(
                myUserId,
                Filters(room = RoomFilter(state = RoomFilter.StateFilter(lazyLoadMembers = true)))
            )

        api.sync.start(
            filter = store.account.filterId.value,
            setPresence = PresenceEventContent.Presence.ONLINE,
            currentBatchToken = store.account.syncBatchToken,
            lastSuccessfulBatchToken = lastSuccessfulBatchToken,
            scope = scope,
        )
    }
}
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
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.KeyVerificationState
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
    val keys: KeyService

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
        keys = KeyService(
            store = store,
            api = api,
            olmSignService = olm.sign,
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
        ): MatrixClient =
            loginWith(
                baseUrl = baseUrl,
                storeFactory = storeFactory,
                secureStore = secureStore,
                baseHttpClient = baseHttpClient,
                customMappings = customMappings,
                setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
                customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
                scope = scope,
                loggerFactory = loggerFactory
            ) {
                val loginResponse = it.authentication.login(
                    identifier = identifier,
                    passwordOrToken = password,
                    type = LoginType.Password,
                    initialDeviceDisplayName = initialDeviceDisplayName
                )
                LoginInfo(
                    userId = loginResponse.userId,
                    accessToken = loginResponse.accessToken,
                    deviceId = loginResponse.deviceId
                )
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
            val (userId, deviceId, accessToken) = getLoginInfo(api)

            api.accessToken.value = accessToken
            store.account.baseUrl.value = baseUrl
            store.account.accessToken.value = accessToken
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

            store.keys.updateDeviceKeys(userId) {
                mapOf(deviceId to StoredDeviceKeys(matrixClient.olm.myDeviceKeys, Valid(true)))
            }
            matrixClient.olm.myDeviceKeys.signed.keys.forEach {
                store.keys.saveKeyVerificationState(
                    it, userId, deviceId, KeyVerificationState.Verified(it.value)
                )
            }
            api.keys.uploadKeys(deviceKeys = matrixClient.olm.myDeviceKeys)
            store.keys.outdatedKeys.update { it + userId }

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

    private val isInitialized = MutableStateFlow(false)

    suspend fun startSync() {
        if (isInitialized.getAndUpdate { true }.not()) {
            val handler = CoroutineExceptionHandler { _, exception ->
                log.error(exception) { "There was an unexpected exception. Will cancel sync now. This should never happen!!!" }
                scope.launch {
                    api.sync.stop(wait = true)
                }
            }
            val everythingStarted = MutableStateFlow(false)
            scope.launch(handler) {
                keys.start(this)
                olm.start(this)
                room.start(this)
                user.start()
                verification.start(this)
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
        }
        api.sync.start(
            filter = store.account.filterId.value,
            setPresence = PresenceEventContent.Presence.ONLINE,
            currentBatchToken = store.account.syncBatchToken,
            scope = scope,
        )
    }

    suspend fun stopSync() {
        api.sync.stop()
    }
}
package net.folivo.trixnity.client

import arrow.core.flatMap
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.createMatrixApiClientEventContentSerializerMappings
import net.folivo.trixnity.client.api.createMatrixApiClientJson
import net.folivo.trixnity.client.api.model.authentication.IdentifierType
import net.folivo.trixnity.client.api.model.authentication.LoginType
import net.folivo.trixnity.client.api.model.users.Filters
import net.folivo.trixnity.client.api.model.users.RoomFilter
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

private val log = KotlinLogging.logger {}

// TODO test
class MatrixClient private constructor(
    olmPickleKey: String,
    val userId: UserId,
    val deviceId: String,
    val api: MatrixApiClient,
    private val store: Store,
    json: Json,
    setOwnMessagesAsFullyRead: Boolean = false,
    customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
    private val scope: CoroutineScope,
) {
    val displayName: StateFlow<String?> = store.account.displayName.asStateFlow()
    val avatarUrl: StateFlow<Url?> = store.account.avatarUrl.asStateFlow()
    val olm: OlmService
    val room: RoomService
    val user: UserService
    val media: MediaService
    val verification: VerificationService
    val key: KeyService

    init {
        olm = OlmService(
            olmPickleKey = olmPickleKey,
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            json = json,
        )
        media = MediaService(
            api = api,
            store = store,
        )
        user = UserService(
            api = api,
            store = store,
        )
        room = RoomService(
            store = store,
            api = api,
            olm = olm,
            user = user,
            media = media,
            setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
            customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
        )
        key = KeyService(
            store = store,
            api = api,
            olm = olm,
        )
        verification = VerificationService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            api = api,
            store = store,
            olmService = olm,
            roomService = room,
            userService = user,
            keyService = key,
        )

    }

    companion object {
        suspend fun login(
            baseUrl: Url,
            identifier: IdentifierType,
            password: String,
            initialDeviceDisplayName: String? = null,
            storeFactory: StoreFactory,
            baseHttpClient: HttpClient = HttpClient(),
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
        ): Result<MatrixClient> =
            loginWith(
                baseUrl = baseUrl,
                storeFactory = storeFactory,
                baseHttpClient = baseHttpClient,
                customMappings = customMappings,
                setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
                customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
                scope = scope,
            ) { api ->
                api.authentication.login(
                    identifier = identifier,
                    passwordOrToken = password,
                    type = LoginType.Password,
                    initialDeviceDisplayName = initialDeviceDisplayName
                ).flatMap { login ->
                    api.users.getProfile(login.userId).map { profile ->
                        LoginInfo(
                            userId = login.userId,
                            accessToken = login.accessToken,
                            deviceId = login.deviceId,
                            displayName = profile.displayName,
                            avatarUrl = profile.avatarUrl?.let { Url(it) }
                        )
                    }
                }
            }

        data class LoginInfo(
            val userId: UserId,
            val deviceId: String,
            val accessToken: String,
            val displayName: String?,
            val avatarUrl: Url?,
        )

        suspend fun loginWith(
            baseUrl: Url,
            storeFactory: StoreFactory,
            baseHttpClient: HttpClient = HttpClient(),
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
            getLoginInfo: suspend (MatrixApiClient) -> Result<LoginInfo>
        ): Result<MatrixClient> = kotlin.runCatching {
            val eventContentSerializerMappings = createMatrixApiClientEventContentSerializerMappings(customMappings)
            val json = createMatrixApiClientJson(eventContentSerializerMappings)

            val store =
                storeFactory.createStore(eventContentSerializerMappings, json)
            store.init()

            val api = MatrixApiClient(
                baseUrl = baseUrl,
                baseHttpClient = baseHttpClient,
                json = json,
                eventContentSerializerMappings = eventContentSerializerMappings,
            )
            val (userId, deviceId, accessToken, displayName, avatarUrl) = getLoginInfo(api).getOrThrow()
            val olmPickleKey = ""

            api.accessToken.value = accessToken
            store.account.olmPickleKey.value = olmPickleKey
            store.account.baseUrl.value = baseUrl
            store.account.accessToken.value = accessToken
            store.account.userId.value = userId
            store.account.deviceId.value = deviceId
            store.account.displayName.value = displayName
            store.account.avatarUrl.value = avatarUrl

            val matrixClient = MatrixClient(
                olmPickleKey = olmPickleKey,
                userId = userId,
                deviceId = deviceId,
                api = api,
                store = store,
                json = json,
                setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
                customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
                scope = scope,
            )

            store.keys.updateDeviceKeys(userId) {
                mapOf(deviceId to StoredDeviceKeys(matrixClient.olm.ownDeviceKeys, Valid(true)))
            }
            matrixClient.olm.ownDeviceKeys.signed.keys.forEach {
                store.keys.saveKeyVerificationState(
                    it, userId, deviceId, KeyVerificationState.Verified(it.value)
                )
            }
            api.keys.uploadKeys(deviceKeys = matrixClient.olm.ownDeviceKeys)
            store.keys.outdatedKeys.update { it + userId }

            matrixClient
        }

        suspend fun fromStore(
            storeFactory: StoreFactory,
            baseHttpClient: HttpClient = HttpClient(),
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
        ): MatrixClient? {
            val eventContentSerializerMappings = createMatrixApiClientEventContentSerializerMappings(customMappings)
            val json = createMatrixApiClientJson(eventContentSerializerMappings)

            val store =
                storeFactory.createStore(eventContentSerializerMappings, json)
            store.init()

            val baseUrl = store.account.baseUrl.value
            val accessToken = store.account.accessToken.value
            val userId = store.account.userId.value
            val deviceId = store.account.deviceId.value
            val olmPickleKey = store.account.olmPickleKey.value

            return if (olmPickleKey != null && accessToken != null && userId != null && deviceId != null && baseUrl != null) {
                val api = MatrixApiClient(
                    baseUrl = baseUrl,
                    baseHttpClient = baseHttpClient,
                    json = json,
                    eventContentSerializerMappings = eventContentSerializerMappings,
                )
                api.accessToken.value = accessToken
                MatrixClient(
                    olmPickleKey = olmPickleKey,
                    userId = userId,
                    deviceId = deviceId,
                    api = api,
                    store = store,
                    json = json,
                    setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
                    customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
                    scope = scope,
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

    suspend fun startSync(): Result<Unit> = kotlin.runCatching {
        if (isInitialized.getAndUpdate { true }.not()) {
            val handler = CoroutineExceptionHandler { _, exception ->
                log.error(exception) { "There was an unexpected exception. Will cancel sync now. This should never happen!!!" }
                scope.launch {
                    api.sync.stop(wait = true)
                }
            }
            val everythingStarted = MutableStateFlow(false)
            scope.launch(handler) {
                key.start(this)
                olm.start(this)
                room.start(this)
                user.start(this)
                verification.start(this)
                everythingStarted.value = true
            }
            everythingStarted.first { it } // we wait until everything has started

            val filterId = store.account.filterId.value
            if (filterId == null) {
                log.debug { "set new filter for sync" }
                store.account.filterId.value = api.users.setFilter(
                    userId,
                    Filters(room = RoomFilter(state = RoomFilter.StateFilter(lazyLoadMembers = true)))
                ).getOrThrow()
            }
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

    suspend fun setDisplayName(displayName: String?): Result<Unit> {
        return api.users.setDisplayName(userId, displayName).map {
            store.account.displayName.value = displayName
        }
    }

    suspend fun setAvatarUrl(avatarUrl: Url?): Result<Unit> {
        return api.users.setAvatarUrl(userId, avatarUrl?.toString()).map {
            store.account.avatarUrl.value = avatarUrl
        }
    }
}
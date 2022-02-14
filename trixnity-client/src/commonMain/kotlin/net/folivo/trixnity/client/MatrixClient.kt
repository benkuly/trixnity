package net.folivo.trixnity.client

import arrow.core.flatMap
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.MatrixClient.LoginState.*
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.createMatrixApiClientEventContentSerializerMappings
import net.folivo.trixnity.client.api.createMatrixApiClientJson
import net.folivo.trixnity.client.api.model.authentication.IdentifierType
import net.folivo.trixnity.client.api.model.authentication.LoginType
import net.folivo.trixnity.client.api.model.users.Filters
import net.folivo.trixnity.client.api.model.users.RoomFilter
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.time.Duration.Companion.milliseconds

private val log = KotlinLogging.logger {}

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
    val avatarUrl: StateFlow<String?> = store.account.avatarUrl.asStateFlow()
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
        key = KeyService(
            olmPickleKey = olmPickleKey,
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            olm = olm,
        )
        room = RoomService(
            ownUserId = userId,
            store = store,
            api = api,
            olm = olm,
            key = key,
            user = user,
            media = media,
            setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
            customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
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
            passwordOrToken: String,
            loginType: LoginType = LoginType.Password,
            deviceId: String? = null,
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
                    passwordOrToken = passwordOrToken,
                    type = loginType,
                    deviceId = deviceId,
                    initialDeviceDisplayName = initialDeviceDisplayName
                ).flatMap { login ->
                    api.users.getProfile(login.userId).map { profile ->
                        LoginInfo(
                            userId = login.userId,
                            accessToken = login.accessToken,
                            deviceId = login.deviceId,
                            displayName = profile.displayName,
                            avatarUrl = profile.avatarUrl
                        )
                    }
                }
            }

        data class LoginInfo(
            val userId: UserId,
            val deviceId: String,
            val accessToken: String,
            val displayName: String?,
            val avatarUrl: String?,
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
                onLogout = { onLogout(it, store) },
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

            val selfSignedDeviceKeys = matrixClient.olm.getSelfSignedDeviceKeys()
            selfSignedDeviceKeys.signed.keys.forEach {
                store.keys.saveKeyVerificationState(
                    it, userId, deviceId, KeyVerificationState.Verified(it.value)
                )
            }
            api.keys.setDeviceKeys(deviceKeys = selfSignedDeviceKeys).getOrThrow()
            store.keys.outdatedKeys.update { it + userId }

            matrixClient
        }

        data class SoftLoginInfo(
            val identifier: IdentifierType,
            val passwordOrToken: String,
            val loginType: LoginType = LoginType.Password,
        )

        suspend fun fromStore(
            storeFactory: StoreFactory,
            baseHttpClient: HttpClient = HttpClient(),
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            onSoftLogin: (suspend () -> SoftLoginInfo)? = null,
            scope: CoroutineScope
        ): Result<MatrixClient?> = kotlin.runCatching {
            val eventContentSerializerMappings = createMatrixApiClientEventContentSerializerMappings(customMappings)
            val json = createMatrixApiClientJson(eventContentSerializerMappings)

            val store = storeFactory.createStore(eventContentSerializerMappings, json)
            store.init()

            val baseUrl = store.account.baseUrl.value
            val userId = store.account.userId.value
            val deviceId = store.account.deviceId.value
            val olmPickleKey = store.account.olmPickleKey.value

            if (olmPickleKey != null && userId != null && deviceId != null && baseUrl != null) {
                val api = MatrixApiClient(
                    baseUrl = baseUrl,
                    baseHttpClient = baseHttpClient,
                    onLogout = { onLogout(it, store) },
                    json = json,
                    eventContentSerializerMappings = eventContentSerializerMappings,
                )
                val accessToken = store.account.accessToken.value ?: onSoftLogin?.let {
                    val (identifier, passwordOrToken, loginType) = onSoftLogin()
                    api.authentication.login(identifier, passwordOrToken, loginType, deviceId).getOrThrow().accessToken
                        .also { store.account.accessToken.value = it }
                }
                if (accessToken != null) {
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
            } else null
        }

        private fun onLogout(
            soft: Boolean,
            store: Store
        ) {
            log.debug { "This device has been logged out (soft=$soft)." }
            store.account.accessToken.value = null
            if (!soft) {
                store.account.syncBatchToken.value = null
            }
        }
    }

    enum class LoginState {
        LOGGED_IN,
        LOGGED_OUT_SOFT,
        LOGGED_OUT,
    }

    val loginState: StateFlow<LoginState?> =
        combine(store.account.accessToken, store.account.syncBatchToken) { accessToken, syncBatchToken ->
            when {
                accessToken != null -> LOGGED_IN
                syncBatchToken != null -> LOGGED_OUT_SOFT
                else -> LOGGED_OUT
            }
        }.stateIn(scope, Eagerly, null)

    suspend fun logout(): Result<Unit> {
        stopSync(true)
        return if (loginState.value == LOGGED_OUT_SOFT) {
            deleteAll()
            Result.success(Unit)
        } else api.authentication.logout()
            .mapCatching {
                deleteAll()
            }
    }

    private suspend fun deleteAll() {
        stopSync(true)
        store.deleteAll()
        olm.free()
    }

    /**
     * Be aware, that most StateFlows you got before will not be updated after calling this method.
     */
    suspend fun clearCache(): Result<Unit> = kotlin.runCatching {
        stopSync(true)
        store.account.syncBatchToken.value = null
        store.deleteAllButKeepAccount()
        startSync()
    }

    val syncState = api.sync.currentSyncState

    private val isInitialized = MutableStateFlow(false)

    @OptIn(FlowPreview::class)
    suspend fun startSync(): Result<Unit> = kotlin.runCatching {
        if (isInitialized.getAndUpdate { true }.not()) {
            val handler = CoroutineExceptionHandler { _, exception ->
                log.error(exception) { "There was an unexpected exception. Will cancel sync now. This should never happen!!!" }
                scope.launch {
                    stopSync(true)
                }
            }
            val everythingStarted = MutableStateFlow(false)
            scope.launch(handler) {
                key.start(this)
                olm.start(this)
                room.start(this)
                user.start(this)
                verification.start(this)
                launch {
                    loginState.debounce(100.milliseconds).collect {
                        log.info { "login state: $it" }
                        when (it) {
                            LOGGED_OUT_SOFT -> {
                                log.info { "stop sync and delete all but account" }
                                stopSync(true)
                                store.deleteAllButKeepAccount()
                            }
                            LOGGED_OUT -> {
                                log.info { "stop sync and delete all" }
                                stopSync(true)
                                store.deleteAll()
                            }
                            else -> {}
                        }
                    }
                }
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

    suspend fun stopSync(wait: Boolean = false) {
        api.sync.stop(wait)
    }

    suspend fun setDisplayName(displayName: String?): Result<Unit> {
        return api.users.setDisplayName(userId, displayName).map {
            store.account.displayName.value = displayName
        }
    }

    suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit> {
        return api.users.setAvatarUrl(userId, avatarUrl).map {
            store.account.avatarUrl.value = avatarUrl
        }
    }
}
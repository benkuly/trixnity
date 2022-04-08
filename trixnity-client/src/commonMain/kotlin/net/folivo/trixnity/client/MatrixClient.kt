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
import net.folivo.trixnity.client.crypto.IOlmService
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyBackupService
import net.folivo.trixnity.client.key.KeySecretService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.media.IMediaService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.IRoomService
import net.folivo.trixnity.client.push.PushService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.IVerificationService
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType
import net.folivo.trixnity.clientserverapi.model.users.Filters
import net.folivo.trixnity.clientserverapi.model.sync.SyncResponse
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val log = KotlinLogging.logger {}

class MatrixClient private constructor(
    olmPickleKey: String,
    val userId: UserId,
    val deviceId: String,
    /**
     * Use this for further access to matrix client-server-API.
     */
    val api: MatrixClientServerApiClient,
    private val store: Store,
    json: Json,
    setOwnMessagesAsFullyRead: Boolean = false,
    customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
    private val scope: CoroutineScope,
) {
    val displayName: StateFlow<String?> = store.account.displayName.asStateFlow()
    val avatarUrl: StateFlow<String?> = store.account.avatarUrl.asStateFlow()
    private val _olm: OlmService
    val olm: IOlmService
    private val _room: RoomService
    val room: IRoomService
    private val _user: UserService
    val user: IUserService
    val media: IMediaService
    private val _verification: VerificationService
    val verification: IVerificationService
    private val _keyBackup: KeyBackupService
    private val _keySecret: KeySecretService
    private val _key: KeyService
    val key: KeyService
    private val _push: PushService
    val syncState = api.sync.currentSyncState

    init {
        _olm = OlmService(
            olmPickleKey = olmPickleKey,
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            json = json,
        )
        olm = _olm
        media = MediaService(
            api = api,
            store = store,
        )
        _user = UserService(
            api = api,
            store = store,
            currentSyncState = syncState,
        )
        user = _user
        _keyBackup = KeyBackupService(
            olmPickleKey = olmPickleKey,
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            olmSign = olm.sign,
            currentSyncState = syncState,
        )
        _keySecret = KeySecretService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            olmEvents = olm.event,
            keyBackup = _keyBackup,
            currentSyncState = syncState,
        )
        _key = KeyService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            olmSign = olm.sign,
            currentSyncState = syncState,
            backup = _keyBackup,
            secret = _keySecret
        )
        key = _key
        _room = RoomService(
            ownUserId = userId,
            store = store,
            api = api,
            olmEvent = olm.event,
            keyBackup = _key.backup,
            user = user,
            media = media,
            currentSyncState = syncState,
            setOwnMessagesAsFullyRead = setOwnMessagesAsFullyRead,
            customOutboxMessageMediaUploaderMappings = customOutboxMessageMediaUploaderMappings,
        )
        room = _room
        _verification = VerificationService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            api = api,
            store = store,
            olmEventService = olm.event,
            roomService = room,
            userService = user,
            keyService = _key,
            currentSyncState = syncState,
        )
        verification = _verification
        _push = PushService(
            api = api,
            room = room,
            store = store,
            json = json,
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
            httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
        ): Result<MatrixClient> =
            loginWith(
                baseUrl = baseUrl,
                storeFactory = storeFactory,
                httpClientFactory = httpClientFactory,
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
            httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
            getLoginInfo: suspend (MatrixClientServerApiClient) -> Result<LoginInfo>
        ): Result<MatrixClient> = kotlin.runCatching {
            val eventContentSerializerMappings = createEventContentSerializerMappings(customMappings)
            val json = createMatrixJson(eventContentSerializerMappings)

            val store = try {
                storeFactory.createStore(eventContentSerializerMappings, json)
            } catch (exc: Exception) {
                throw MatrixClientStoreException(exc)
            }
            store.init()

            val api = MatrixClientServerApiClient(
                baseUrl = baseUrl,
                httpClientFactory = httpClientFactory,
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
            api.keys.setKeys(deviceKeys = selfSignedDeviceKeys).getOrThrow()
            store.keys.outdatedKeys.update { it + userId }

            matrixClient
        }

        data class SoftLoginInfo(
            val identifier: IdentifierType,
            val passwordOrToken: String,
            val loginType: LoginType = LoginType.Password,
        )

        @OptIn(ExperimentalTime::class)
        suspend fun fromStore(
            storeFactory: StoreFactory,
            httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            onSoftLogin: (suspend () -> SoftLoginInfo)? = null,
            scope: CoroutineScope
        ): Result<MatrixClient?> = kotlin.runCatching {
            measureTimedValue {
                val eventContentSerializerMappings = measureTimedValue {
                    createEventContentSerializerMappings(customMappings)
                }.apply {
                    log.debug { "createMatrixClientServerApiClientEventContentSerializerMappings() took ${duration.inWholeMilliseconds}ms" }
                }.value
                val json = createMatrixJson(eventContentSerializerMappings)

                val store = try {
                    measureTimedValue {
                        storeFactory.createStore(
                            eventContentSerializerMappings,
                            json
                        )
                    }.apply {
                        log.debug { "createStore() took ${duration.inWholeMilliseconds}ms" }
                    }.value
                } catch (exc: Exception) {
                    throw MatrixClientStoreException(exc)
                }
                measureTime { store.init() }.apply { log.debug { "store.init() took ${inWholeMilliseconds}ms" } }

                val baseUrl = store.account.baseUrl.value
                val userId = store.account.userId.value
                val deviceId = store.account.deviceId.value
                val olmPickleKey = store.account.olmPickleKey.value

                if (olmPickleKey != null && userId != null && deviceId != null && baseUrl != null) {
                    val api = MatrixClientServerApiClient(
                        baseUrl = baseUrl,
                        httpClientFactory = httpClientFactory,
                        onLogout = { onLogout(it, store) },
                        json = json,
                        eventContentSerializerMappings = eventContentSerializerMappings,
                    )
                    val accessToken = store.account.accessToken.value ?: onSoftLogin?.let {
                        val (identifier, passwordOrToken, loginType) = onSoftLogin()
                        api.authentication.login(identifier, passwordOrToken, loginType, deviceId)
                            .getOrThrow().accessToken
                            .also { store.account.accessToken.value = it }
                    }
                    if (accessToken != null) {
                        api.accessToken.value = accessToken
                        measureTimedValue {
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
                        }.apply {
                            log.debug { "init MatrixClient took ${duration.inWholeMilliseconds}ms" }
                        }.value
                    } else null
                } else null
            }.apply {
                log.debug { "fromStore() took ${duration.inWholeMilliseconds}ms" }
            }.value
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
        _olm.free()
    }

    /**
     * Be aware, that most StateFlows you got before will not be updated after calling this method.
     */
    suspend fun clearCache(): Result<Unit> = kotlin.runCatching {
        stopSync(true)
        store.account.syncBatchToken.value = null
        store.deleteNonLocal()
        startSync()
    }

    suspend fun clearMediaCache(): Result<Unit> = kotlin.runCatching {
        stopSync(true)
        store.media.deleteAll()
        startSync()
    }

    private val isInitialized = MutableStateFlow(false)

    suspend fun startSync(): Result<Unit> = kotlin.runCatching {
        startMatrixClient()
        api.sync.start(
            filter = store.account.filterId.value,
            setPresence = PresenceEventContent.Presence.ONLINE,
            currentBatchToken = store.account.syncBatchToken,
            scope = scope,
        )
    }

    suspend fun syncOnce(timeout: Long = 0L): Result<Unit> = syncOnce(timeout = timeout) { }

    suspend fun <T> syncOnce(timeout: Long = 0L, runOnce: suspend (SyncResponse) -> T): Result<T> {
        startMatrixClient()
        return api.sync.startOnce(
            filter = store.account.backgroundFilterId.value,
            setPresence = PresenceEventContent.Presence.OFFLINE,
            currentBatchToken = store.account.syncBatchToken,
            timeout = timeout,
            runOnce = runOnce
        )
    }

    @OptIn(FlowPreview::class)
    private suspend fun startMatrixClient() {
        if (isInitialized.getAndUpdate { true }.not()) {
            val handler = CoroutineExceptionHandler { _, exception ->
                log.error(exception) { "There was an unexpected exception. Will cancel sync now. This should never happen!!!" }
                scope.launch {
                    stopSync(true)
                }
            }
            val everythingStarted = MutableStateFlow(false)
            scope.launch(handler) {
                _key.start(this)
                _keyBackup.start(this)
                _keySecret.start(this)
                _olm.start(this)
                _room.start(this)
                _user.start(this)
                _verification.start(this)
                _push.start(this)
                launch {
                    loginState.debounce(100.milliseconds).collect {
                        log.info { "login state: $it" }
                        when (it) {
                            LOGGED_OUT_SOFT -> {
                                log.info { "stop sync" }
                                stopSync(true)
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
                    Filters(room = Filters.RoomFilter(state = Filters.RoomFilter.StateFilter(lazyLoadMembers = true)))
                ).getOrThrow()
            }
            val backgroundFilterId = store.account.backgroundFilterId.value
            if (backgroundFilterId == null) {
                log.debug { "set new background filter for sync" }
                store.account.backgroundFilterId.value = api.users.setFilter(
                    userId,
                    Filters(
                        room = Filters.RoomFilter(
                            state = Filters.RoomFilter.StateFilter(lazyLoadMembers = true),
                            ephemeral = Filters.RoomFilter.RoomEventFilter(limit = 0)
                        ),
                        presence = Filters.EventFilter(limit = 0)
                    )
                ).getOrThrow()
            }
        }
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

class MatrixClientStoreException(cause: Throwable?) : RuntimeException(cause)
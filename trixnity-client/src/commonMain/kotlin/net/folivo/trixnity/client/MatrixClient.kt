package net.folivo.trixnity.client

import arrow.core.flatMap
import io.ktor.http.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.IMatrixClient.*
import net.folivo.trixnity.client.IMatrixClient.LoginState.*
import net.folivo.trixnity.client.crypto.IOlmService
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.IKeyService
import net.folivo.trixnity.client.key.KeyBackupService
import net.folivo.trixnity.client.key.KeySecretService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.media.IMediaService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.push.IPushService
import net.folivo.trixnity.client.push.PushService
import net.folivo.trixnity.client.room.IRoomService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.IVerificationService
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.Filters
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmUtility
import kotlin.time.Duration.Companion.milliseconds

private val log = KotlinLogging.logger {}

interface IMatrixClient {
    val userId: UserId
    val deviceId: String

    /**
     * Use this for further access to matrix client-server-API.
     */
    val api: IMatrixClientServerApiClient
    val displayName: StateFlow<String?>
    val avatarUrl: StateFlow<String?>
    val olm: IOlmService
    val room: IRoomService
    val user: IUserService
    val media: IMediaService
    val verification: IVerificationService
    val key: IKeyService
    val push: IPushService
    val syncState: StateFlow<SyncState>

    val loginState: StateFlow<LoginState?>

    enum class LoginState {
        LOGGED_IN,
        LOGGED_OUT_SOFT,
        LOGGED_OUT,
    }

    data class LoginInfo(
        val userId: UserId,
        val deviceId: String,
        val accessToken: String,
        val displayName: String?,
        val avatarUrl: String?,
    )

    data class SoftLoginInfo(
        val identifier: IdentifierType,
        val passwordOrToken: String,
        val loginType: LoginType = LoginType.Password,
    )

    suspend fun logout(): Result<Unit>

    /**
     * Be aware, that most StateFlows you got before will not be updated after calling this method.
     */
    suspend fun clearCache(): Result<Unit>

    suspend fun clearMediaCache(): Result<Unit>

    suspend fun startSync(): Result<Unit>

    suspend fun syncOnce(timeout: Long = 0L): Result<Unit>

    suspend fun <T> syncOnce(timeout: Long = 0L, runOnce: suspend (Sync.Response) -> T): Result<T>

    suspend fun stopSync(wait: Boolean = false)

    suspend fun setDisplayName(displayName: String?): Result<Unit>

    suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit>
}

class MatrixClient private constructor(
    olmPickleKey: String,
    override val userId: UserId,
    override val deviceId: String,
    /**
     * Use this for further access to matrix client-server-API.
     */
    override val api: MatrixClientServerApiClient,
    private val store: Store,
    json: Json,
    private val config: MatrixClientConfiguration,
    private val olmAccount: OlmAccount,
    private val olmUtility: OlmUtility,
    private val scope: CoroutineScope,
) : IMatrixClient {
    override val displayName: StateFlow<String?> = store.account.displayName.asStateFlow()
    override val avatarUrl: StateFlow<String?> = store.account.avatarUrl.asStateFlow()
    private val _olm: OlmService
    override val olm: IOlmService
    private val _room: RoomService
    override val room: IRoomService
    private val _user: UserService
    override val user: IUserService
    override val media: IMediaService
    private val _verification: VerificationService
    override val verification: IVerificationService
    private val _keyBackup: KeyBackupService
    private val _keySecret: KeySecretService
    private val _key: KeyService
    override val key: KeyService
    override val push: PushService
    override val syncState = api.sync.currentSyncState

    init {
        _olm = OlmService(
            olmPickleKey = olmPickleKey,
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            json = json,
            olmAccount = olmAccount,
            olmUtility = olmUtility,
            scope = scope,
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
            scope = scope,
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
            scope = scope,
        )
        _keySecret = KeySecretService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            olmEvents = olm.event,
            keyBackup = _keyBackup,
            currentSyncState = syncState,
            scope = scope,
        )
        _key = KeyService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            olmSign = olm.sign,
            currentSyncState = syncState,
            backup = _keyBackup,
            secret = _keySecret,
            scope = scope,
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
            config = config,
            scope = scope,
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
            scope = scope,
        )
        verification = _verification
        push = PushService(
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
            scope: CoroutineScope,
            configuration: MatrixClientConfiguration.() -> Unit = {}
        ): Result<IMatrixClient> =
            loginWith(
                baseUrl = baseUrl,
                storeFactory = storeFactory,
                scope = scope,
                configuration = configuration
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

        suspend fun loginWith(
            baseUrl: Url,
            storeFactory: StoreFactory,
            scope: CoroutineScope,
            configuration: MatrixClientConfiguration.() -> Unit = {},
            getLoginInfo: suspend (MatrixClientServerApiClient) -> Result<LoginInfo>
        ): Result<IMatrixClient> = kotlin.runCatching {
            val config = MatrixClientConfiguration().apply(configuration)
            val eventContentSerializerMappings = createEventContentSerializerMappings(config.customMappings)
            val json = createMatrixEventJson(eventContentSerializerMappings)

            val store = try {
                storeFactory.createStore(eventContentSerializerMappings, json)
            } catch (exc: Exception) {
                throw MatrixClientStoreException(exc)
            }
            store.init()

            val api = MatrixClientServerApiClient(
                baseUrl = baseUrl,
                httpClientFactory = config.httpClientFactory,
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
                config = config,
                olmAccount = store.olm.account.value?.let { OlmAccount.unpickle(olmPickleKey, it) }
                    ?: OlmAccount.create().also { store.olm.account.value = it.pickle(olmPickleKey) },
                olmUtility = OlmUtility.create(),
                scope = scope,
            )

            val selfSignedDeviceKeys = matrixClient.olm.getSelfSignedDeviceKeys()
            selfSignedDeviceKeys.signed.keys.forEach {
                store.keys.saveKeyVerificationState(it, KeyVerificationState.Verified(it.value))
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

        suspend fun fromStore(
            storeFactory: StoreFactory,
            onSoftLogin: (suspend () -> SoftLoginInfo)? = null,
            scope: CoroutineScope,
            configuration: MatrixClientConfiguration.() -> Unit = {}
        ): Result<IMatrixClient?> = kotlin.runCatching {
            val config = MatrixClientConfiguration().apply(configuration)
            val eventContentSerializerMappings = createEventContentSerializerMappings(config.customMappings)
            val json = createMatrixEventJson(eventContentSerializerMappings)

            val store = try {
                storeFactory.createStore(eventContentSerializerMappings, json)
            } catch (exc: Exception) {
                throw MatrixClientStoreException(exc)
            }
            store.init()

            val baseUrl = store.account.baseUrl.value
            val userId = store.account.userId.value
            val deviceId = store.account.deviceId.value
            val olmPickleKey = store.account.olmPickleKey.value

            if (olmPickleKey != null && userId != null && deviceId != null && baseUrl != null) {
                val api = MatrixClientServerApiClient(
                    baseUrl = baseUrl,
                    httpClientFactory = config.httpClientFactory,
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
                    MatrixClient(
                        olmPickleKey = olmPickleKey,
                        userId = userId,
                        deviceId = deviceId,
                        api = api,
                        store = store,
                        json = json,
                        config = config,
                        olmAccount = store.olm.account.value?.let { OlmAccount.unpickle(olmPickleKey, it) }
                            ?: OlmAccount.create().also { store.olm.account.value = it.pickle(olmPickleKey) },
                        olmUtility = OlmUtility.create(),
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

    override val loginState: StateFlow<LoginState?> =
        combine(store.account.accessToken, store.account.syncBatchToken) { accessToken, syncBatchToken ->
            when {
                accessToken != null -> LOGGED_IN
                syncBatchToken != null -> LOGGED_OUT_SOFT
                else -> LOGGED_OUT
            }
        }.stateIn(scope, Eagerly, null)

    override suspend fun logout(): Result<Unit> {
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
        olmAccount.free()
        olmUtility.free()
    }

    /**
     * Be aware, that most StateFlows you got before will not be updated after calling this method.
     */
    override suspend fun clearCache(): Result<Unit> = kotlin.runCatching {
        stopSync(true)
        store.account.syncBatchToken.value = null
        store.deleteNonLocal()
        startSync()
    }

    override suspend fun clearMediaCache(): Result<Unit> = kotlin.runCatching {
        stopSync(true)
        store.media.deleteAll()
        startSync()
    }

    private val isInitialized = MutableStateFlow(false)

    override suspend fun startSync(): Result<Unit> = kotlin.runCatching {
        startMatrixClient()
        api.sync.start(
            filter = store.account.filterId.value,
            setPresence = Presence.ONLINE,
            currentBatchToken = store.account.syncBatchToken,
            scope = scope,
        )
    }

    override suspend fun syncOnce(timeout: Long): Result<Unit> = syncOnce(timeout = timeout) { }

    override suspend fun <T> syncOnce(timeout: Long, runOnce: suspend (Sync.Response) -> T): Result<T> {
        startMatrixClient()
        return api.sync.startOnce(
            filter = store.account.backgroundFilterId.value,
            setPresence = Presence.OFFLINE,
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
            scope.launch(handler) {
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

    override suspend fun stopSync(wait: Boolean) {
        api.sync.stop(wait)
    }

    override suspend fun setDisplayName(displayName: String?): Result<Unit> {
        return api.users.setDisplayName(userId, displayName).map {
            store.account.displayName.value = displayName
        }
    }

    override suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit> {
        return api.users.setAvatarUrl(userId, avatarUrl).map {
            store.account.avatarUrl.value = avatarUrl
        }
    }
}

class MatrixClientStoreException(cause: Throwable?) : RuntimeException(cause)
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
import net.folivo.trixnity.client.MatrixClient.LoginState.*
import net.folivo.trixnity.client.crypto.ClientOlmServiceRequestHandler
import net.folivo.trixnity.client.crypto.ClientOlmServiceStore
import net.folivo.trixnity.client.crypto.ClientOneTimeKeysCountEmitter
import net.folivo.trixnity.client.crypto.ClientSignServiceStore
import net.folivo.trixnity.client.key.KeyBackupService
import net.folivo.trixnity.client.key.KeySecretService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.media.IMediaService
import net.folivo.trixnity.client.media.MediaService
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
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.Filters
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.IOlmService
import net.folivo.trixnity.crypto.olm.OlmService
import net.folivo.trixnity.crypto.sign.ISignService
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.freeAfter
import kotlin.time.Duration.Companion.milliseconds

private val log = KotlinLogging.logger {}

class MatrixClient private constructor(
    olmPickleKey: String,
    val userId: UserId,
    val deviceId: String,
    val identityKey: Key.Curve25519Key,
    val signingKey: Key.Ed25519Key,
    /**
     * Use this for further access to matrix client-server-API.
     */
    val api: MatrixClientServerApiClient,
    private val store: Store,
    json: Json,
    private val config: MatrixClientConfiguration,
    private val scope: CoroutineScope,
) {
    val displayName: StateFlow<String?> = store.account.displayName.asStateFlow()
    val avatarUrl: StateFlow<String?> = store.account.avatarUrl.asStateFlow()
    private val _olm: OlmService
    val olm: IOlmService
    private val _sign: ISignService
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
    val push: PushService
    val syncState = api.sync.currentSyncState

    init {
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
        _sign = SignService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            json = json,
            store = ClientSignServiceStore(store),
            olmPickleKey = olmPickleKey,
        )
        _keyBackup = KeyBackupService(
            olmPickleKey = olmPickleKey,
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            signService = _sign,
            currentSyncState = syncState,
            scope = scope,
        )
        _olm = OlmService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            ownEd25519Key = signingKey,
            ownCurve25519Key = identityKey,
            eventEmitter = api.sync,
            oneTimeKeysCountEmitter = ClientOneTimeKeysCountEmitter(api),
            requestHandler = ClientOlmServiceRequestHandler(api),
            signService = _sign,
            store = ClientOlmServiceStore(store, user),
            json = json,
            olmPickleKey = olmPickleKey,
        )
        olm = _olm
        olm.start()
        _keySecret = KeySecretService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            olmService = olm,
            keyBackup = _keyBackup,
            currentSyncState = syncState,
            scope = scope,
        )
        _key = KeyService(
            ownUserId = userId,
            ownDeviceId = deviceId,
            store = store,
            api = api,
            signService = _sign,
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
            olmService = olm,
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
        ): Result<MatrixClient> =
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
            scope: CoroutineScope,
            configuration: MatrixClientConfiguration.() -> Unit = {},
            getLoginInfo: suspend (MatrixClientServerApiClient) -> Result<LoginInfo>
        ): Result<MatrixClient> = kotlin.runCatching {
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


            val (signingKey, identityKey) = freeAfter(
                store.olm.account.value?.let { OlmAccount.unpickle(olmPickleKey, it) }
                    ?: OlmAccount.create().also { store.olm.account.value = it.pickle(olmPickleKey) }
            ) {
                Key.Ed25519Key(deviceId, it.identityKeys.ed25519) to
                        Key.Curve25519Key(deviceId, it.identityKeys.curve25519)
            }

            val matrixClient = MatrixClient(
                olmPickleKey = olmPickleKey,
                userId = userId,
                deviceId = deviceId,
                signingKey = signingKey,
                identityKey = identityKey,
                api = api,
                store = store,
                json = json,
                config = config,
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
        ): Result<MatrixClient?> = kotlin.runCatching {
            val config = MatrixClientConfiguration().apply(configuration)
            val eventContentSerializerMappings = createEventContentSerializerMappings(config.customMappings)
            val json = createMatrixEventJson(eventContentSerializerMappings)

            val store = try {
                storeFactory.createStore(
                    eventContentSerializerMappings,
                    json
                )
            } catch (exc: Exception) {
                throw MatrixClientStoreException(exc)
            }
            store.init()

            val baseUrl = store.account.baseUrl.value
            val userId = store.account.userId.value
            val deviceId = store.account.deviceId.value
            val olmPickleKey = store.account.olmPickleKey.value
            val olmAccount = store.olm.account.value

            if (olmPickleKey != null && userId != null && deviceId != null && baseUrl != null && olmAccount != null) {
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
                    val (signingKey, identityKey) = freeAfter(OlmAccount.unpickle(olmPickleKey, olmAccount)) {
                        Key.Ed25519Key(deviceId, it.identityKeys.ed25519) to
                                Key.Curve25519Key(deviceId, it.identityKeys.curve25519)
                    }
                    MatrixClient(
                        olmPickleKey = olmPickleKey,
                        userId = userId,
                        deviceId = deviceId,
                        signingKey = signingKey,
                        identityKey = identityKey,
                        api = api,
                        store = store,
                        json = json,
                        config = config,
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
            setPresence = Presence.ONLINE,
            currentBatchToken = store.account.syncBatchToken,
            scope = scope,
        )
    }

    suspend fun syncOnce(timeout: Long = 0L): Result<Unit> = syncOnce(timeout = timeout) { }

    suspend fun <T> syncOnce(timeout: Long = 0L, runOnce: suspend (Sync.Response) -> T): Result<T> {
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
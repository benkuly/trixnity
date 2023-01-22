package net.folivo.trixnity.client

import arrow.core.flatMap
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import net.folivo.trixnity.client.MatrixClient.*
import net.folivo.trixnity.client.MatrixClient.LoginState.*
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.Filters
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.freeAfter
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds

private val log = KotlinLogging.logger {}

interface MatrixClient {
    companion object

    val userId: UserId
    val deviceId: String
    val identityKey: Key.Curve25519Key
    val signingKey: Key.Ed25519Key

    val di: Koin

    /**
     * Use this for further access to matrix client-server-API.
     */
    val api: MatrixClientServerApiClient
    val displayName: StateFlow<String?>
    val avatarUrl: StateFlow<String?>

    val syncState: StateFlow<SyncState>
    val initialSyncDone: StateFlow<Boolean>

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
        val password: String? = null,
        val token: String? = null,
        val loginType: LoginType = LoginType.Password,
    )

    suspend fun logout(): Result<Unit>

    /**
     * Be aware, that most StateFlows you got before will not be updated after calling this method.
     */
    suspend fun clearCache(): Result<Unit>

    suspend fun clearMediaCache(): Result<Unit>

    suspend fun startSync()

    suspend fun syncOnce(timeout: Long = 0L): Result<Unit>

    suspend fun <T> syncOnce(timeout: Long = 0L, runOnce: suspend (Sync.Response) -> T): Result<T>

    suspend fun stopSync(wait: Boolean = false)

    suspend fun cancelSync(wait: Boolean = false)

    suspend fun setDisplayName(displayName: String?): Result<Unit>

    suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit>
}

@Deprecated("use login with separated password and token")
suspend fun MatrixClient.Companion.login(
    baseUrl: Url,
    identifier: IdentifierType,
    passwordOrToken: String,
    loginType: LoginType = LoginType.Password,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    scope: CoroutineScope,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): Result<MatrixClient> =
    loginWith(
        baseUrl = baseUrl,
        repositoriesModule = repositoriesModule,
        mediaStore = mediaStore,
        scope = scope,
        getLoginInfo = { api ->
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
        },
        configuration = configuration
    )

suspend fun MatrixClient.Companion.login(
    baseUrl: Url,
    identifier: IdentifierType,
    password: String? = null,
    token: String? = null,
    loginType: LoginType = LoginType.Password,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    scope: CoroutineScope,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): Result<MatrixClient> =
    loginWith(
        baseUrl = baseUrl,
        repositoriesModule = repositoriesModule,
        mediaStore = mediaStore,
        scope = scope,
        getLoginInfo = { api ->
            api.authentication.login(
                identifier = identifier,
                password = password,
                token = token,
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
        },
        configuration = configuration
    )

suspend fun MatrixClient.Companion.loginWith(
    baseUrl: Url,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    scope: CoroutineScope,
    getLoginInfo: suspend (MatrixClientServerApiClientImpl) -> Result<LoginInfo>,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = kotlin.runCatching {
    val config = MatrixClientConfiguration().apply(configuration)
    val koinApplication = koinApplication {
        modules(module {
            single { scope }
            single { config }
            single { mediaStore }
        })
        modules(repositoriesModule)
        modules(config.modules)
    }
    val di = koinApplication.koin
    val rootStore = di.get<RootStore>()
    rootStore.init()

    val accountStore = di.get<AccountStore>()

    val api = MatrixClientServerApiClientImpl(
        baseUrl = baseUrl,
        httpClientFactory = config.httpClientFactory,
        onLogout = { onLogout(it, accountStore) },
        json = di.get(),
        eventContentSerializerMappings = di.get(),
    )
    val (userId, deviceId, accessToken, displayName, avatarUrl) = getLoginInfo(api).getOrThrow()
    val olmPickleKey = ""

    api.accessToken.value = accessToken
    accountStore.olmPickleKey.value = olmPickleKey
    accountStore.baseUrl.value = baseUrl
    accountStore.accessToken.value = accessToken
    accountStore.userId.value = userId
    accountStore.deviceId.value = deviceId
    accountStore.displayName.value = displayName
    accountStore.avatarUrl.value = avatarUrl

    val olmCryptoStore = di.get<OlmCryptoStore>()
    val (signingKey, identityKey) = freeAfter(
        olmCryptoStore.account.value?.let { OlmAccount.unpickle(olmPickleKey, it) }
            ?: OlmAccount.create().also { olmCryptoStore.account.value = it.pickle(olmPickleKey) }
    ) {
        Key.Ed25519Key(deviceId, it.identityKeys.ed25519) to
                Key.Curve25519Key(deviceId, it.identityKeys.curve25519)
    }

    koinApplication.modules(module {
        single { UserInfo(userId, deviceId, signingKey, identityKey) }
        single<MatrixClientServerApiClient> { api }
        single { CurrentSyncState(api.sync.currentSyncState) }
    })

    val keyStore = di.get<KeyStore>()

    val selfSignedDeviceKeys = di.get<SignService>().getSelfSignedDeviceKeys()
    selfSignedDeviceKeys.signed.keys.forEach {
        keyStore.saveKeyVerificationState(it, KeyVerificationState.Verified(it.value))
    }
    val matrixClient = MatrixClientImpl(
        userId = userId,
        deviceId = deviceId,
        identityKey = identityKey,
        signingKey = signingKey,
        api = api,
        di = di,
        rootStore = rootStore,
        accountStore = accountStore,
        mediaStore = di.get(),
        mediaCacheMappingStore = di.get(),
        eventHandlers = di.getAll(),
        tm = di.get(),
        config = config,
        scope = scope,
    )
    api.keys.setKeys(deviceKeys = selfSignedDeviceKeys)
        .onFailure { matrixClient.deleteAll() }
        .getOrThrow()
    keyStore.updateOutdatedKeys { it + userId }
    matrixClient
}

suspend fun MatrixClient.Companion.fromStore(
    repositoriesModule: Module,
    mediaStore: MediaStore,
    onSoftLogin: (suspend () -> SoftLoginInfo)? = null,
    scope: CoroutineScope,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): Result<MatrixClient?> = kotlin.runCatching {
    val config = MatrixClientConfiguration().apply(configuration)
    val koinApplication = koinApplication {
        modules(module {
            single { scope }
            single { config }
            single { mediaStore }
        })
        modules(repositoriesModule)
        modules(config.modules)
    }
    val di = koinApplication.koin

    val rootStore = di.get<RootStore>()
    rootStore.init()

    val accountStore = di.get<AccountStore>()
    val olmCryptoStore = di.get<OlmCryptoStore>()

    val baseUrl = accountStore.baseUrl.value
    val userId = accountStore.userId.value
    val deviceId = accountStore.deviceId.value
    val olmPickleKey = accountStore.olmPickleKey.value
    val olmAccount = olmCryptoStore.account.value

    if (olmPickleKey != null && userId != null && deviceId != null && baseUrl != null && olmAccount != null) {
        val api = MatrixClientServerApiClientImpl(
            baseUrl = baseUrl,
            httpClientFactory = config.httpClientFactory,
            onLogout = { onLogout(it, accountStore) },
            json = di.get(),
            eventContentSerializerMappings = di.get(),
        )
        val accessToken = accountStore.accessToken.value ?: onSoftLogin?.let {
            val (identifier, password, token, loginType) = onSoftLogin()
            api.authentication.login(identifier, password, token, loginType, deviceId)
                .getOrThrow().accessToken
                .also { accountStore.accessToken.value = it }
        }
        if (accessToken != null) {
            api.accessToken.value = accessToken
            val (signingKey, identityKey) = freeAfter(OlmAccount.unpickle(olmPickleKey, olmAccount)) {
                Key.Ed25519Key(deviceId, it.identityKeys.ed25519) to
                        Key.Curve25519Key(deviceId, it.identityKeys.curve25519)
            }
            koinApplication.modules(module {
                single { UserInfo(userId, deviceId, signingKey, identityKey) }
                single<MatrixClientServerApiClient> { api }
                single { CurrentSyncState(api.sync.currentSyncState) }
            })
            MatrixClientImpl(
                userId = userId,
                deviceId = deviceId,
                identityKey = identityKey,
                signingKey = signingKey,
                api = api,
                di = di,
                rootStore = rootStore,
                accountStore = accountStore,
                mediaStore = di.get(),
                mediaCacheMappingStore = di.get(),
                eventHandlers = di.getAll(),
                tm = di.get(),
                config = config,
                scope = scope,
            )
        } else null
    } else null
}

private fun onLogout(
    soft: Boolean,
    accountStore: AccountStore
) {
    log.debug { "This device has been logged out (soft=$soft)." }
    accountStore.accessToken.value = null
    if (!soft) {
        accountStore.syncBatchToken.value = null
    }
}

class MatrixClientImpl internal constructor(
    override val userId: UserId,
    override val deviceId: String,
    override val identityKey: Key.Curve25519Key,
    override val signingKey: Key.Ed25519Key,
    override val api: MatrixClientServerApiClient,
    override val di: Koin,
    private val rootStore: RootStore,
    private val accountStore: AccountStore,
    private val mediaStore: MediaStore,
    private val mediaCacheMappingStore: MediaCacheMappingStore,
    private val eventHandlers: List<EventHandler>,
    private val tm: TransactionManager,
    config: MatrixClientConfiguration,
    private val scope: CoroutineScope,
) : MatrixClient {
    override val displayName: StateFlow<String?> = accountStore.displayName
    override val avatarUrl: StateFlow<String?> = accountStore.avatarUrl
    override val syncState = api.sync.currentSyncState

    override val initialSyncDone: StateFlow<Boolean> =
        accountStore.syncBatchToken
            .map { token -> token != null }
            .stateIn(scope, Eagerly, accountStore.syncBatchToken.value != null)

    override val loginState: StateFlow<LoginState?> =
        combine(accountStore.accessToken, accountStore.syncBatchToken) { accessToken, syncBatchToken ->
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

    internal suspend fun deleteAll() {
        stopSync(true)
        rootStore.deleteAll()
    }

    /**
     * Be aware, that most StateFlows you got before will not be updated after calling this method.
     */
    override suspend fun clearCache(): Result<Unit> = kotlin.runCatching {
        stopSync(true)
        accountStore.syncBatchToken.value = null
        rootStore.clearCache()
        startSync()
    }

    override suspend fun clearMediaCache(): Result<Unit> = kotlin.runCatching {
        stopSync(true)
        mediaCacheMappingStore.clearCache()
        mediaStore.clearCache()
        startSync()
    }

    private val syncTransaction: suspend (onRollback: suspend () -> Unit, block: suspend () -> Unit) -> Unit =
        if (config.enableAsyncTransactions) tm::withWriteTransaction
        else { _, b -> b() }

    override suspend fun startSync() {
        startMatrixClient()
        api.sync.start(
            filter = requireNotNull(accountStore.filterId.value),
            setPresence = Presence.ONLINE,
            currentBatchToken = accountStore.syncBatchToken,
            withTransaction = syncTransaction,
            scope = scope,
        )
    }

    override suspend fun syncOnce(timeout: Long): Result<Unit> = syncOnce(timeout = timeout) { }

    override suspend fun <T> syncOnce(timeout: Long, runOnce: suspend (Sync.Response) -> T): Result<T> {
        startMatrixClient()
        return api.sync.startOnce(
            filter = requireNotNull(accountStore.backgroundFilterId.value),
            setPresence = Presence.OFFLINE,
            currentBatchToken = accountStore.syncBatchToken,
            timeout = timeout,
            withTransaction = syncTransaction,
            runOnce = runOnce
        )
    }

    private val initializationMutex = Mutex()
    private var initializationJob: Job? = null

    @OptIn(FlowPreview::class)
    private suspend fun startMatrixClient() = initializationMutex.withLock {
        if (initializationJob == null) {
            // even if the caller is cancelled, the initialization should be done -> scope.launch()
            initializationJob = scope.launch {
                val handler = CoroutineExceptionHandler { _, exception ->
                    log.error(exception) { "There was an unexpected exception. Will cancel sync now. This should never happen!!!" }
                    scope.launch {
                        stopSync(true)
                    }
                }
                val allHandlersStarted = MutableStateFlow(false)
                scope.launch(handler, CoroutineStart.UNDISPATCHED) {
                    eventHandlers.forEach {
                        log.debug { "start EventHandler: ${it::class.simpleName}" }
                        it.startInCoroutineScope(this)
                    }
                    allHandlersStarted.value = true
                }
                allHandlersStarted.first { it }
                log.debug { "all EventHandler started" }
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
                                rootStore.deleteAll()
                            }

                            else -> {}
                        }
                    }
                }

                val filterId = accountStore.filterId.value
                if (filterId == null) {
                    accountStore.filterId.value = retryWhen(
                        flowOf(true),
                        onError = { log.warn(it) { "could not set filter" } }
                    ) {
                        api.users.setFilter(
                            userId,
                            Filters(room = Filters.RoomFilter(state = Filters.RoomFilter.StateFilter(lazyLoadMembers = true)))
                        ).getOrThrow().also { log.debug { "set new filter for sync: $it" } }
                    }
                }
                val backgroundFilterId = accountStore.backgroundFilterId.value
                if (backgroundFilterId == null) {
                    accountStore.backgroundFilterId.value = retryWhen(
                        flowOf(true),
                        onError = { log.warn(it) { "could not set filter" } }
                    ) {
                        api.users.setFilter(
                            userId,
                            Filters(
                                room = Filters.RoomFilter(
                                    state = Filters.RoomFilter.StateFilter(lazyLoadMembers = true),
                                    ephemeral = Filters.RoomFilter.RoomEventFilter(limit = 0)
                                ),
                                presence = Filters.EventFilter(limit = 0)
                            )
                        ).getOrThrow().also { log.debug { "set new background filter for sync: $it" } }
                    }
                }
            }
        }

        // everyone has to wait for the initialization to finish
        initializationJob?.join()
    }

    override suspend fun stopSync(wait: Boolean) {
        api.sync.stop(wait)
    }

    override suspend fun cancelSync(wait: Boolean) {
        api.sync.cancel(wait)
    }

    override suspend fun setDisplayName(displayName: String?): Result<Unit> {
        return api.users.setDisplayName(userId, displayName).map {
            accountStore.displayName.value = displayName
        }
    }

    override suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit> {
        return api.users.setAvatarUrl(userId, avatarUrl).map {
            accountStore.avatarUrl.value = avatarUrl
        }
    }
}
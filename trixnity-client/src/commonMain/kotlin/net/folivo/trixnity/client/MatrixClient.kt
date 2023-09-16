package net.folivo.trixnity.client

import arrow.core.flatMap
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import net.folivo.trixnity.client.MatrixClient.*
import net.folivo.trixnity.client.MatrixClient.LoginState.*
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.utils.RetryLoopFlowState.RUN
import net.folivo.trixnity.client.utils.retryWhen
import net.folivo.trixnity.clientserverapi.client.*
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

    suspend fun clearCache(): Result<Unit>

    suspend fun clearMediaCache(): Result<Unit>

    suspend fun startSync(presence: Presence? = Presence.ONLINE)

    suspend fun syncOnce(presence: Presence? = Presence.OFFLINE, timeout: Long = 0L): Result<Unit>

    suspend fun <T> syncOnce(
        presence: Presence? = Presence.OFFLINE,
        timeout: Long = 0L,
        runOnce: suspend (Sync.Response) -> T
    ): Result<T>

    suspend fun stopSync(wait: Boolean = false)

    suspend fun cancelSync(wait: Boolean = false)

    /**
     * Stop the MatrixClient and its [CoroutineScope].
     * It should be called to clean up all resources used by [MatrixClient].
     *
     * After calling this, this instance should not be used anymore!
     */
    fun stop()

    suspend fun setDisplayName(displayName: String?): Result<Unit>

    suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit>
}

suspend fun MatrixClient.Companion.login(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String? = null,
    token: String? = null,
    loginType: LoginType = LoginType.Password,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): Result<MatrixClient> =
    loginWith(
        baseUrl = baseUrl,
        repositoriesModule = repositoriesModule,
        mediaStore = mediaStore,
        getLoginInfo = { api ->
            api.authentication.login(
                identifier = identifier,
                password = password,
                token = token,
                type = loginType,
                deviceId = deviceId,
                initialDeviceDisplayName = initialDeviceDisplayName
            ).flatMap { login ->
                api.accessToken.value = login.accessToken
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

suspend fun MatrixClient.Companion.loginWithPassword(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): Result<MatrixClient> =
    login(
        baseUrl = baseUrl,
        identifier = identifier,
        password = password,
        token = null,
        loginType = LoginType.Password,
        deviceId = deviceId,
        initialDeviceDisplayName = initialDeviceDisplayName,
        repositoriesModule = repositoriesModule,
        mediaStore = mediaStore,
        configuration = configuration
    )

suspend fun MatrixClient.Companion.loginWithToken(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    token: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): Result<MatrixClient> =
    login(
        baseUrl = baseUrl,
        identifier = identifier,
        password = null,
        token = token,
        loginType = LoginType.Token(),
        deviceId = deviceId,
        initialDeviceDisplayName = initialDeviceDisplayName,
        repositoriesModule = repositoriesModule,
        mediaStore = mediaStore,
        configuration = configuration
    )

suspend fun MatrixClient.Companion.loginWith(
    baseUrl: Url,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    getLoginInfo: suspend (MatrixClientServerApiClient) -> Result<LoginInfo>,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = kotlin.runCatching {
    val config = MatrixClientConfiguration().apply(configuration)
    val handler = CoroutineExceptionHandler { _, exception ->
        log.error(exception) { "There was an unexpected exception. This should never happen!!!" }
    }
    val coroutineScope = CoroutineScope(Dispatchers.Default + handler)
    val koinApplication = koinApplication {
        modules(module {
            single { coroutineScope }
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
        syncLoopDelay = config.syncLoopDelays.syncLoopDelay,
        syncLoopErrorDelay = config.syncLoopDelays.syncLoopErrorDelay
    )
    val (userId, deviceId, accessToken, displayName, avatarUrl) = getLoginInfo(api).getOrThrow()
    val olmPickleKey = ""

    api.accessToken.value = accessToken
    accountStore.updateAccount {
        Account(
            olmPickleKey = olmPickleKey,
            baseUrl = baseUrl.toString(),
            accessToken = accessToken,
            userId = userId,
            deviceId = deviceId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            backgroundFilterId = null,
            filterId = null,
            syncBatchToken = null
        )
    }


    val olmCryptoStore = di.get<OlmCryptoStore>()
    val (signingKey, identityKey) = freeAfter(
        olmCryptoStore.getOlmAccount()
            ?.let { OlmAccount.unpickle(olmPickleKey, it) }
            ?: OlmAccount.create()
                .also { olmAccount -> olmCryptoStore.updateOlmAccount { olmAccount.pickle(olmPickleKey) } }
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
        coroutineScope = coroutineScope,
    )
    api.keys.setKeys(deviceKeys = selfSignedDeviceKeys)
        .onFailure { matrixClient.deleteAll() }
        .getOrThrow()
    keyStore.updateOutdatedKeys { it + userId }
    matrixClient.also {
        log.trace { "finished creating MatrixClient" }
    }
}

suspend fun MatrixClient.Companion.fromStore(
    repositoriesModule: Module,
    mediaStore: MediaStore,
    onSoftLogin: (suspend () -> SoftLoginInfo)? = null,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): Result<MatrixClient?> = kotlin.runCatching {
    val config = MatrixClientConfiguration().apply(configuration)
    val handler = CoroutineExceptionHandler { _, exception ->
        log.error(exception) { "There was an unexpected exception. This should never happen!!!" }
    }
    val coroutineScope = CoroutineScope(Dispatchers.Default + handler)
    val koinApplication = koinApplication {
        modules(module {
            single { coroutineScope }
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

    val account = accountStore.getAccount()
    val baseUrl = account?.baseUrl?.let { Url(it) }
    val userId = account?.userId
    val deviceId = account?.deviceId
    val olmPickleKey = account?.olmPickleKey
    val olmAccount = olmCryptoStore.getOlmAccount()

    if (olmPickleKey != null && userId != null && deviceId != null && baseUrl != null && olmAccount != null) {
        val api = MatrixClientServerApiClientImpl(
            baseUrl = baseUrl,
            httpClientFactory = config.httpClientFactory,
            onLogout = { onLogout(it, accountStore) },
            json = di.get(),
            eventContentSerializerMappings = di.get(),
            syncLoopDelay = config.syncLoopDelays.syncLoopDelay,
            syncLoopErrorDelay = config.syncLoopDelays.syncLoopErrorDelay
        )
        val accessToken = accountStore.getAccount()?.accessToken ?: onSoftLogin?.let {
            val (identifier, password, token, loginType) = onSoftLogin()
            api.authentication.login(identifier, password, token, loginType, deviceId)
                .getOrThrow().accessToken
                .also { accountStore.updateAccount { account -> account.copy(accessToken = it) } }
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
                coroutineScope = coroutineScope,
            ).also {
                log.trace { "finished creating MatrixClient" }
            }
        } else null
    } else null
}

private suspend fun onLogout(
    soft: Boolean,
    accountStore: AccountStore
) {
    log.debug { "This device has been logged out (soft=$soft)." }
    accountStore.updateAccount {
        it.copy(
            accessToken = null,
            syncBatchToken = if (soft) it.syncBatchToken else null
        )
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
    private val coroutineScope: CoroutineScope,
) : MatrixClient {
    private val started = MutableStateFlow(false)

    override val displayName: StateFlow<String?> = accountStore.getAccountAsFlow().map { it?.displayName }
        .stateIn(coroutineScope, Eagerly, null)
    override val avatarUrl: StateFlow<String?> = accountStore.getAccountAsFlow().map { it?.avatarUrl }
        .stateIn(coroutineScope, Eagerly, null)
    override val syncState = api.sync.currentSyncState

    override val initialSyncDone: StateFlow<Boolean> =
        accountStore.getAccountAsFlow().map { it?.syncBatchToken }
            .map { token -> token != null }
            .stateIn(coroutineScope, Eagerly, true)

    override val loginState: StateFlow<LoginState?> =
        accountStore.getAccountAsFlow().map { account ->
            when {
                account?.accessToken != null -> LOGGED_IN
                account?.syncBatchToken != null -> LOGGED_OUT_SOFT
                else -> LOGGED_OUT
            }
        }.stateIn(coroutineScope, Eagerly, null)

    init {
        coroutineScope.launch {
            val allHandlersStarted = MutableStateFlow(false)
            launch {
                eventHandlers.forEach {
                    log.debug { "start EventHandler: ${it::class.simpleName}" }
                    it.startInCoroutineScope(this)
                }
                allHandlersStarted.value = true
            }
            allHandlersStarted.first { it }
            log.debug { "all EventHandler started" }
            launch {
                loginState.collect {
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

            val filterId = accountStore.getAccount()?.filterId
            if (filterId == null) {
                val newFilterId = retryWhen(
                    flowOf(RUN),
                    onError = { log.warn(it) { "could not set filter" } }
                ) {
                    api.users.setFilter(userId, baseFilters)
                        .getOrThrow().also { log.debug { "set new filter for sync: $it" } }
                }
                accountStore.updateAccount { it.copy(filterId = newFilterId) }
            }
            val backgroundFilterId = accountStore.getAccount()?.backgroundFilterId
            if (backgroundFilterId == null) {
                val newFilterId = retryWhen(
                    flowOf(RUN),
                    onError = { log.warn(it) { "could not set filter" } }
                ) {
                    api.users.setFilter(
                        userId,
                        baseFilters.copy(presence = Filters.EventFilter(limit = 0))
                    ).getOrThrow().also { log.debug { "set new background filter for sync: $it" } }
                }
                accountStore.updateAccount { it.copy(backgroundFilterId = newFilterId) }
            }
            started.value = true
        }
    }

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
        accountStore.updateAccount { it.copy(syncBatchToken = null) }
        rootStore.clearCache()
        startSync()
    }

    override suspend fun clearMediaCache(): Result<Unit> = kotlin.runCatching {
        stopSync(true)
        mediaCacheMappingStore.clearCache()
        mediaStore.clearCache()
        startSync()
    }

    override suspend fun startSync(presence: Presence?) {
        started.first { it }
        api.sync.start(
            filter = checkNotNull(accountStore.getAccount()?.filterId),
            setPresence = presence,
            getBatchToken = { accountStore.getAccount()?.syncBatchToken },
            setBatchToken = { accountStore.updateAccount { account -> account.copy(syncBatchToken = it) } },
            scope = coroutineScope,
        )
    }

    override suspend fun syncOnce(presence: Presence?, timeout: Long): Result<Unit> =
        syncOnce(presence = presence, timeout = timeout) { }

    override suspend fun <T> syncOnce(
        presence: Presence?,
        timeout: Long,
        runOnce: suspend (Sync.Response) -> T,
    ): Result<T> {
        started.first { it }
        return api.sync.startOnce(
            filter = checkNotNull(accountStore.getAccount()?.backgroundFilterId),
            setPresence = presence,
            getBatchToken = { accountStore.getAccount()?.syncBatchToken },
            setBatchToken = { accountStore.updateAccount { account -> account.copy(syncBatchToken = it) } },
            timeout = timeout,
            runOnce = runOnce
        )
    }

    private val baseFilters =
        Filters(
            room = Filters.RoomFilter(
                state = Filters.RoomFilter.StateFilter(lazyLoadMembers = true),
            )
        )

    override suspend fun stopSync(wait: Boolean) {
        api.sync.stop(wait)
    }

    override suspend fun cancelSync(wait: Boolean) {
        api.sync.cancel(wait)
    }

    override fun stop() {
        started.value = false
        coroutineScope.cancel("stopped MatrixClient")
    }

    override suspend fun setDisplayName(displayName: String?): Result<Unit> {
        return api.users.setDisplayName(userId, displayName).map {
            accountStore.updateAccount { it.copy(displayName = displayName) }
        }
    }

    override suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit> {
        return api.users.setAvatarUrl(userId, avatarUrl).map {
            accountStore.updateAccount { it.copy(avatarUrl = avatarUrl) }
        }
    }
}
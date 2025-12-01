package net.folivo.trixnity.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import net.folivo.trixnity.client.MatrixClient.LoginState
import net.folivo.trixnity.client.MatrixClient.LoginState.*
import net.folivo.trixnity.client.MatrixClientConfiguration.DeleteRooms
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.RepositoryMigration
import net.folivo.trixnity.clientserverapi.client.*
import net.folivo.trixnity.clientserverapi.model.users.Filters
import net.folivo.trixnity.core.ClientEventEmitter
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.subscribeAsFlow
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.useAll
import net.folivo.trixnity.crypto.of
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.utils.retry
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

private val log = KotlinLogging.logger("net.folivo.trixnity.client.MatrixClient")

interface MatrixClient : AutoCloseable {
    companion object

    val userId: UserId
    val deviceId: String
    val baseUrl: Url
    val identityKey: Key.Curve25519Key
    val signingKey: Key.Ed25519Key

    val di: Koin

    /**
     * Use this for further access to matrix client-server-API.
     */
    val api: MatrixClientServerApiClient
    val displayName: StateFlow<String?>
    val avatarUrl: StateFlow<String?>

    val serverData: StateFlow<ServerData?>

    val syncState: StateFlow<SyncState>
    val initialSyncDone: StateFlow<Boolean>

    val loginState: StateFlow<LoginState?>
    val started: StateFlow<Boolean>

    enum class LoginState {
        LOGGED_IN,
        LOGGED_OUT_SOFT,
        LOGGED_OUT,
        LOCKED,
    }

    data class LoginInfo(
        val userId: UserId,
        val deviceId: String,
    )

    suspend fun logout(): Result<Unit>

    suspend fun clearCache(): Result<Unit>

    suspend fun clearMediaCache(): Result<Unit>

    suspend fun startSync(presence: Presence? = Presence.ONLINE)

    /**
     * Usually used for background sync.
     */
    suspend fun syncOnce(presence: Presence? = Presence.OFFLINE, timeout: Duration = Duration.ZERO): Result<Unit>

    /**
     * Usually used for background sync.
     */
    suspend fun <T> syncOnce(
        presence: Presence? = Presence.OFFLINE,
        timeout: Duration = Duration.ZERO,
        runOnce: suspend (SyncEvents) -> T
    ): Result<T>

    suspend fun stopSync()

    suspend fun cancelSync()

    suspend fun setDisplayName(displayName: String?): Result<Unit>

    suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit>

    suspend fun closeSuspending()
}

fun interface RepositoriesModule {
    suspend fun create(): Module

    companion object
}

fun interface MediaStoreModule {
    suspend fun create(): Module

    companion object
}

fun interface CryptoDriverModule {
    suspend fun create(): Module

    companion object
}

suspend fun MatrixClient.Companion.login(
    baseUrl: Url,
    repositoriesModule: RepositoriesModule,
    mediaStoreModule: MediaStoreModule,
    cryptoDriverModule: CryptoDriverModule,
    authProviderData: MatrixClientAuthProviderData,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = login(
    baseUrl = baseUrl,
    authProviderData = authProviderData,
    repositoriesModuleFactory = { repositoriesModule },
    mediaStoreModuleFactory = { mediaStoreModule },
    cryptoDriverModule = cryptoDriverModule,
    coroutineContext = coroutineContext,
    configuration = configuration,
)

suspend fun MatrixClient.Companion.login(
    baseUrl: Url,
    repositoriesModuleFactory: (MatrixClient.LoginInfo) -> RepositoriesModule,
    mediaStoreModuleFactory: (MatrixClient.LoginInfo) -> MediaStoreModule,
    cryptoDriverModule: CryptoDriverModule,
    authProviderData: MatrixClientAuthProviderData,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = kotlin.runCatching {
    val config = MatrixClientConfiguration().apply(configuration)
    val finalCoroutineContext = (config.name?.let { CoroutineName(it) } ?: EmptyCoroutineContext) + coroutineContext
    val coroutineScope =
        CoroutineScope(finalCoroutineContext + SupervisorJob(coroutineContext[Job]) + coroutineExceptionHandler)

    val koinApplication = createKoinApplication(
        config = config,
        coroutineScope = coroutineScope,
    )

    val di = koinApplication.koin

    val authProviderFactory =
        requireNotNull(
            di.getAll<MatrixClientAuthProviderFactory>()
                .find { it.supports == authProviderData::class }) {
            "authProviderData of type ${authProviderData::class} is not supported. " +
                    "Supported types: ${di.getAll<MatrixClientAuthProviderFactory>().map { it.supports }}"
        }

    val (userId, deviceId) = config.matrixClientServerApiClientFactory.create(
        baseUrl = baseUrl,
        authProvider = authProviderFactory.create(
            baseUrl = baseUrl,
            store = MatrixClientAuthProviderStore.inMemory(),
            initialData = authProviderData,
            onLogout = { },
            httpClientEngine = config.httpClientEngine,
            httpClientConfig = config.httpClientConfig
        ),
        httpClientEngine = config.httpClientEngine,
        httpClientConfig = config.httpClientConfig,
        json = di.get(),
        eventContentSerializerMappings = di.get(),
        coroutineContext = finalCoroutineContext,
    ).use {
        it.authentication.whoAmI().getOrThrow()
    }
    requireNotNull(deviceId) { "deviceId must not be null" }
    val loginInfo = MatrixClient.LoginInfo(userId, deviceId)

    koinApplication.modules(
        repositoriesModuleFactory(loginInfo).create(),
        mediaStoreModuleFactory(loginInfo).create(),
        cryptoDriverModule.create(),
    )
    runMigrationsAndInitStores(di)

    val authenticationStore = di.get<AuthenticationStore>()
    val accountStore = di.get<AccountStore>()

    val authProvider = authProviderFactory.create(
        baseUrl = baseUrl,
        store = AuthenticationStoreMatrixClientAuthProviderStore(authProviderFactory.id, authenticationStore),
        initialData = authProviderData,
        onLogout = { onLogout(it, authenticationStore) },
        httpClientEngine = config.httpClientEngine,
        httpClientConfig = config.httpClientConfig
    )

    val api = config.matrixClientServerApiClientFactory.create(
        baseUrl = baseUrl,
        authProvider = authProvider,
        httpClientEngine = config.httpClientEngine,
        httpClientConfig = config.httpClientConfig,
        json = di.get(),
        eventContentSerializerMappings = di.get(),
        syncBatchTokenStore = AccountStoreSyncBatchTokenStore(accountStore),
        syncErrorDelayConfig = config.syncErrorDelayConfig,
        coroutineContext = finalCoroutineContext,
    )
    try {
        val (displayName, avatarUrl) = api.user.getProfile(userId).getOrThrow()
        accountStore.updateAccount {
            Account(
                olmPickleKey = null,
                baseUrl = baseUrl.toString(),
                userId = userId,
                deviceId = deviceId,
                displayName = displayName,
                avatarUrl = avatarUrl,
                backgroundFilterId = null,
                filterId = null,
                syncBatchToken = null,
            )
        }

        val userInfo = getUserInfo(userId, deviceId, di)

        koinApplication.modules(module {
            single { userInfo }
            single<MatrixClientServerApiClient> { api }
        })

        val keyStore = di.get<KeyStore>()

        val selfSignedDeviceKeys = di.get<SignService>().getSelfSignedDeviceKeys()
        api.key.setKeys(deviceKeys = selfSignedDeviceKeys).getOrThrow()
        selfSignedDeviceKeys.signed.keys.forEach {
            keyStore.saveKeyVerificationState(it, KeyVerificationState.Verified(it.value.value))
        }
        keyStore.updateOutdatedKeys { it + userId }

        log.trace { "finished create MatrixClient" }
        MatrixClientImpl(baseUrl, di)
    } catch (t: Throwable) {
        api.close()
        di.get<CoroutineScope>().cancel()
        throw t
    }
}

suspend fun MatrixClient.Companion.fromStore(
    repositoriesModule: RepositoriesModule,
    mediaStoreModule: MediaStoreModule,
    cryptoDriverModule: CryptoDriverModule,
    authProviderData: MatrixClientAuthProviderData? = null,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = kotlin.runCatching {
    val config = MatrixClientConfiguration().apply(configuration)
    val finalCoroutineContext = (config.name?.let { CoroutineName(it) } ?: EmptyCoroutineContext) + coroutineContext
    val coroutineScope =
        CoroutineScope(finalCoroutineContext + SupervisorJob(coroutineContext[Job]) + coroutineExceptionHandler)

    val koinApplication = createKoinApplication(
        config = config,
        coroutineScope = coroutineScope,
        extraModules = listOf(
            repositoriesModule.create(),
            mediaStoreModule.create(),
            cryptoDriverModule.create(),
        )
    )

    val di = koinApplication.koin
    runMigrationsAndInitStores(di)

    val authenticationStore = di.get<AuthenticationStore>()
    val accountStore = di.get<AccountStore>()
    val authentication =
        checkNotNull(authenticationStore.getAuthentication()) { "store did not contain authentication" }
    val account = checkNotNull(accountStore.getAccount()) { "store did not contain account" }
    val baseUrl = Url(account.baseUrl)
    val userId = account.userId
    val deviceId = account.deviceId
    val legacyAuthProviderData = account.takeIf { it.accessToken != null }
        ?.let {
            @Suppress("DEPRECATION")
            ClassicMatrixClientAuthProviderData(
                accessToken = checkNotNull(it.accessToken),
                accessTokenExpiresInMs = null,
                refreshToken = it.refreshToken
            )
        }

    val authProviderFactory = when {
        authProviderData != null -> {
            authenticationStore.updateAuthentication { it?.copy(logoutInfo = null) }
            requireNotNull(
                di.getAll<MatrixClientAuthProviderFactory>()
                    .find { it.supports == authProviderData::class }) {
                "authProviderData of type ${authProviderData::class} is not supported. " +
                        "Supported types: ${di.getAll<MatrixClientAuthProviderFactory>().map { it.supports }}"
            }
        }

        legacyAuthProviderData != null -> {
            requireNotNull(
                di.getAll<MatrixClientAuthProviderFactory>()
                    .find { it.supports == ClassicMatrixClientAuthProviderData::class }) {
                "authProviderData of type ${ClassicMatrixClientAuthProviderData::class} is needed for migration. " +
                        "Supported types: ${di.getAll<MatrixClientAuthProviderFactory>().map { it.supports }}"
            }
        }

        else -> {
            requireNotNull(
                di.getAll<MatrixClientAuthProviderFactory>()
                    .find { it.id == authentication.providerId }) {
                "authProviderId ${authentication.providerId} is not supported. " +
                        "Supported types: ${di.getAll<MatrixClientAuthProviderFactory>().map { it.id }}"
            }
        }
    }

    if (authProviderData != null) {
        config.matrixClientServerApiClientFactory.create(
            baseUrl = baseUrl,
            authProvider = authProviderFactory.create(
                baseUrl = baseUrl,
                store = MatrixClientAuthProviderStore.inMemory(),
                initialData = authProviderData,
                onLogout = { },
                httpClientEngine = config.httpClientEngine,
                httpClientConfig = config.httpClientConfig
            ),
            httpClientEngine = config.httpClientEngine,
            httpClientConfig = config.httpClientConfig,
            json = di.get(),
            eventContentSerializerMappings = di.get(),
            coroutineContext = finalCoroutineContext,
        ).use {
            val (newUserId, newDeviceId) = it.authentication.whoAmI().getOrThrow()
            if (newUserId != userId || newDeviceId != deviceId) {
                throw IllegalArgumentException(
                    "newly authenticated userId ($newUserId) and deviceId ($newDeviceId) " +
                            "must match stored authenticated userId ($userId) and deviceId ($deviceId). "
                )
            }
        }
    }

    val authProvider = authProviderFactory.create(
        baseUrl = baseUrl,
        store = AuthenticationStoreMatrixClientAuthProviderStore(authProviderFactory.id, authenticationStore),
        initialData = authProviderData ?: legacyAuthProviderData,
        onLogout = { onLogout(it, authenticationStore) },
        httpClientEngine = config.httpClientEngine,
        httpClientConfig = config.httpClientConfig
    )
    if (legacyAuthProviderData != null) {
        accountStore.updateAccount { it?.copy(accessToken = null, refreshToken = null) }
    }

    val userInfo = getUserInfo(userId, deviceId, di)

    koinApplication.modules(module {
        single { userInfo }
        single<MatrixClientServerApiClient> {
            config.matrixClientServerApiClientFactory.create(
                baseUrl = baseUrl,
                authProvider = authProvider,
                httpClientEngine = config.httpClientEngine,
                httpClientConfig = config.httpClientConfig,
                json = di.get(),
                eventContentSerializerMappings = di.get(),
                syncBatchTokenStore = AccountStoreSyncBatchTokenStore(accountStore),
                syncErrorDelayConfig = config.syncErrorDelayConfig,
                coroutineContext = finalCoroutineContext,
            )
        }
    })

    log.trace { "finished create MatrixClient" }
    MatrixClientImpl(baseUrl, di)
}

private class AuthenticationStoreMatrixClientAuthProviderStore(
    private val id: String,
    private val store: AuthenticationStore,
) : MatrixClientAuthProviderStore {
    override suspend fun getAuthData(): String? = store.getAuthentication()?.providerData
    override suspend fun setAuthData(authData: String?) = store.updateAuthentication {
        if (authData != null)
            it?.copy(providerData = authData)
                ?: Authentication(
                    providerId = id,
                    providerData = authData,
                    logoutInfo = null,
                )
        else null
    }
}

private class AccountStoreSyncBatchTokenStore(
    private val store: AccountStore,
) : SyncBatchTokenStore {
    override suspend fun getSyncBatchToken(): String? =
        store.getAccount()?.syncBatchToken

    override suspend fun setSyncBatchToken(token: String) {
        store.updateAccount { it?.copy(syncBatchToken = token) }
    }
}

private suspend fun onLogout(
    logoutInfo: LogoutInfo,
    authenticationStore: AuthenticationStore,
) {
    log.debug { "This device has been logged out ($logoutInfo)." }
    withContext(NonCancellable) {
        authenticationStore.updateAuthentication {
            it?.copy(logoutInfo = logoutInfo)
        }
    }
}

private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
    log.error(exception) { "There was an unexpected exception. This should never happen!!!" }
}

private fun createKoinApplication(
    config: MatrixClientConfiguration,
    coroutineScope: CoroutineScope,
    extraModules: List<Module> = emptyList(),
): KoinApplication {
    return koinApplication {
        modules(module {
            single { coroutineScope }
            single { config }
        })
        if (extraModules.isNotEmpty()) modules(extraModules)
        modules(config.modulesFactories.map { it.invoke() })
    }
}

private suspend fun runMigrationsAndInitStores(di: Koin) {
    di.getAll<RepositoryMigration>().forEach { it.run() }
    val rootStore = di.get<RootStore>()
    rootStore.init(di.get())
}

private suspend fun getUserInfo(userId: UserId, deviceId: String, di: Koin): UserInfo {
    val driver = di.get<CryptoDriver>()

    val pickleKey = driver.key.pickleKey(di.get<AccountStore>().getAccount()?.olmPickleKey)

    val olmCryptoStore = di.get<OlmCryptoStore>()
    val (signingKey, identityKey) = (olmCryptoStore.getOlmAccount()
        ?.let { driver.olm.account.fromPickle(it, pickleKey) }
        ?: driver.olm.account())
        .use { account ->
            olmCryptoStore.updateOlmAccount { account.pickle(pickleKey) }

            useAll(
                { account.ed25519Key },
                { account.curve25519Key }
            ) { signingKey, identityKey ->
                Key.of(deviceId, signingKey) to Key.of(deviceId, identityKey)
            }
        }
    return UserInfo(userId, deviceId, signingKey, identityKey)
}

class MatrixClientImpl internal constructor(
    override val baseUrl: Url,
    override val di: Koin,
) : MatrixClient {
    private val coroutineScope: CoroutineScope = di.get()
    private val rootStore: RootStore = di.get()
    private val authenticationStore: AuthenticationStore = di.get()
    private val accountStore: AccountStore = di.get()
    private val mediaStore: MediaStore = di.get()
    private val mediaCacheMappingStore: MediaCacheMappingStore = di.get()
    private val eventHandlers: List<EventHandler> = di.getAll()
    private val config: MatrixClientConfiguration = di.get()
    override val api: MatrixClientServerApiClient = di.get()
    private val userInfo: UserInfo = di.get()
    override val userId: UserId = userInfo.userId
    override val deviceId: String = userInfo.deviceId
    override val identityKey: Key.Curve25519Key = userInfo.identityPublicKey
    override val signingKey: Key.Ed25519Key = userInfo.signingPublicKey

    override val started: MatrixClientStarted = di.get()

    override val displayName: StateFlow<String?> = accountStore.getAccountAsFlow().map { it?.displayName }
        .stateIn(coroutineScope, Eagerly, null)
    override val avatarUrl: StateFlow<String?> = accountStore.getAccountAsFlow().map { it?.avatarUrl }
        .stateIn(coroutineScope, Eagerly, null)
    override val serverData: StateFlow<ServerData?> = di.get<ServerDataStore>().getServerDataFlow()
        .stateIn(coroutineScope, Eagerly, null)

    override val syncState = api.sync.currentSyncState

    override val initialSyncDone: StateFlow<Boolean> =
        accountStore.getAccountAsFlow().map { it?.syncBatchToken }
            .map { token -> token != null }
            .stateIn(coroutineScope, Eagerly, true)

    override val loginState: StateFlow<LoginState?> =
        authenticationStore.getAuthenticationAsFlow().map { authentication ->
            val logoutInfo = authentication?.logoutInfo
            when {
                logoutInfo == null -> LOGGED_IN
                logoutInfo.isSoft -> LOGGED_OUT_SOFT
                logoutInfo.isLocked -> LOCKED
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
                loginState.collectLatest {
                    log.info { "login state: $it" }
                    when (it) {
                        LOGGED_OUT_SOFT -> {
                            log.info { "stop sync" }
                            stopSync()
                        }

                        LOGGED_OUT -> {
                            log.info { "stop sync and delete all" }
                            stopSync()
                            rootStore.deleteAll()
                        }

                        LOCKED -> {
                            log.info { "account is locked - waiting for successful sync for unlocking" }
                            api.sync.subscribeAsFlow(ClientEventEmitter.Priority.FIRST).first()
                            authenticationStore.updateAuthentication { it?.copy(logoutInfo = null) }
                            log.info { "account unlocked" }
                        }

                        else -> {}
                    }
                }
            }

            val filterId = accountStore.getAccount()?.filterId
            if (filterId == null) {
                val newFilterId = retry(
                    onError = { error, delay -> log.warn(error) { "could not set filter, retry again in $delay" } }
                ) {
                    val filter = config.syncFilter.applyDefaultFilter()
                    api.user.setFilter(userId, filter)
                        .getOrThrow().also { log.debug { "set new filter for sync with id $it: $filter" } }
                }
                accountStore.updateAccount { it?.copy(filterId = newFilterId) }
            }
            val backgroundFilterId = accountStore.getAccount()?.backgroundFilterId
            if (backgroundFilterId == null) {
                val newFilterId = retry(
                    onError = { error, delay -> log.warn(error) { "could not set background filter, retry again in $delay" } }
                ) {
                    val filter = config.syncOnceFilter.applyDefaultFilter()
                    api.user.setFilter(userId, filter)
                        .getOrThrow().also { log.debug { "set new background filter for sync with id $it: $filter" } }
                }
                accountStore.updateAccount { it?.copy(backgroundFilterId = newFilterId) }
            }
            started.delegate.value = true
        }
    }

    private fun Filters.applyDefaultFilter(): Filters {
        val mappings = di.get<EventContentSerializerMappings>()
        return copy(
            accountData = (accountData ?: Filters.EventFilter()).copy(
                types = mappings.globalAccountData.map { it.type }.toSet(),
            ),
            room = (room ?: Filters.RoomFilter()).copy(
                accountData = (room?.accountData ?: Filters.RoomFilter.RoomEventFilter()).copy(
                    types = mappings.roomAccountData.map { it.type }.toSet(),
                ),
                ephemeral = (room?.ephemeral ?: Filters.RoomFilter.RoomEventFilter()).copy(
                    types = mappings.ephemeral.map { it.type }.toSet(),
                ),
                state = (room?.state ?: Filters.RoomFilter.RoomEventFilter()).copy(
                    lazyLoadMembers = true,
                    types = mappings.state.map { it.type }.toSet(),
                ),
                timeline = (room?.timeline ?: Filters.RoomFilter.RoomEventFilter()).copy(
                    types = (mappings.message + mappings.state).map { it.type }.toSet(),
                ),
                includeLeave = config.deleteRooms !is DeleteRooms.OnLeave,
            )
        )
    }

    override suspend fun logout(): Result<Unit> {
        cancelSync()
        return if (loginState.value == LOGGED_OUT_SOFT) {
            deleteAll()
            Result.success(Unit)
        } else api.authentication.logout()
            .mapCatching {
                deleteAll()
            }
    }

    internal suspend fun deleteAll() {
        rootStore.deleteAll()
    }

    /**
     * Be aware, that most StateFlows you got before will not be updated after calling this method.
     */
    override suspend fun clearCache(): Result<Unit> = kotlin.runCatching {
        stopSync()
        accountStore.updateAccount { it?.copy(syncBatchToken = null) }
        rootStore.clearCache()
        startSync()
    }

    override suspend fun clearMediaCache(): Result<Unit> = kotlin.runCatching {
        stopSync()
        mediaCacheMappingStore.clearCache()
        mediaStore.clearCache()
        startSync()
    }

    override suspend fun startSync(presence: Presence?) {
        started.first { it }
        api.sync.start(
            timeout = config.syncLoopTimeout,
            filter = checkNotNull(accountStore.getAccount()?.filterId),
            setPresence = presence,
        )
    }

    override suspend fun syncOnce(presence: Presence?, timeout: Duration): Result<Unit> =
        syncOnce(presence = presence, timeout = timeout) { }

    override suspend fun <T> syncOnce(
        presence: Presence?,
        timeout: Duration,
        runOnce: suspend (SyncEvents) -> T,
    ): Result<T> {
        started.first { it }
        return api.sync.startOnce(
            filter = checkNotNull(accountStore.getAccount()?.backgroundFilterId),
            setPresence = presence,
            timeout = timeout,
            runOnce = runOnce
        )
    }

    override suspend fun stopSync() {
        api.sync.stop()
    }

    override suspend fun cancelSync() {
        api.sync.cancel()
    }

    override fun close() {
        started.delegate.value = false
        api.close()
        coroutineScope.cancel("stopped MatrixClient")
    }

    override suspend fun closeSuspending() {
        val job = coroutineScope.coroutineContext.job
        close()
        job.join()
    }

    override suspend fun setDisplayName(displayName: String?): Result<Unit> {
        return api.user.setDisplayName(userId, displayName).map {
            accountStore.updateAccount { it?.copy(displayName = displayName) }
        }
    }

    override suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit> {
        return api.user.setAvatarUrl(userId, avatarUrl).map {
            accountStore.updateAccount { it?.copy(avatarUrl = avatarUrl) }
        }
    }
}